package com.fota.trade.manager;

import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.asset.service.AssetService;
import com.fota.common.Result;
import com.fota.common.utils.CommonUtils;
import com.fota.data.domain.TickerDTO;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.PriceTypeEnum;
import com.fota.trade.client.AssetExtraProperties;
import com.fota.trade.client.CancelTypeEnum;
import com.fota.trade.client.OrderResult;
import com.fota.trade.client.ToCancelMessage;
import com.fota.trade.common.BizException;
import com.fota.trade.common.Constant;
import com.fota.trade.common.RedisKey;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.*;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserContractLeverMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.msg.*;
import com.fota.trade.service.ContractCategoryService;
import com.fota.trade.service.internal.MarketAccountListService;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ContractUtils;
import com.fota.trade.util.Profiler;
import com.fota.trade.util.ThreadContextUtil;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import static com.fota.asset.domain.enums.UserContractStatus.LIMIT;
import static com.fota.common.utils.CommonUtils.scale;
import static com.fota.trade.PriceTypeEnum.MARKET_PRICE;
import static com.fota.trade.PriceTypeEnum.RIVAL_PRICE;
import static com.fota.trade.PriceTypeEnum.SPECIFIED_PRICE;
import static com.fota.trade.client.MQConstants.ORDER_TOPIC;
import static com.fota.trade.client.MQConstants.TO_CANCEL_CONTRACT_TAG;
import static com.fota.trade.common.Constant.DEFAULT_LEVER;
import static com.fota.trade.common.ResultCodeEnum.*;
import static com.fota.trade.domain.enums.ContractStatusEnum.PROCESSING;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;
import static com.fota.trade.domain.enums.OrderStatusEnum.*;
import static com.fota.trade.msg.TopicConstants.TRD_CONTRACT_CANCELED;
import static java.util.stream.Collectors.toList;


/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Component
public class ContractOrderManager {
    private static final Logger log = LoggerFactory.getLogger(ContractOrderManager.class);
    private static final Logger tradeLog = LoggerFactory.getLogger("trade");


    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Autowired
    private ContractLeverManager contractLeverManager;

    @Resource
    private UserContractLeverMapper userContractLeverMapper;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private ContractCategoryService contractCategoryService;

    @Autowired
    private RocketMqManager rocketMqManager;

    @Autowired
    private AssetService assetService;

    @Resource
    private RedisManager redisManager;

    @Autowired
    private RealTimeEntrustManager realTimeEntrustManager;

    @Autowired
    private CurrentPriceManager currentPriceManager;

    @Autowired
    private MarketAccountListService marketAccountListService;

    private static final BigDecimal POSITION_LIMIT_BTC = BigDecimal.valueOf(100);

    private static final BigDecimal POSITION_LIMIT_ETH = BigDecimal.valueOf(2_500);

    private static final BigDecimal POSITION_LIMIT_EOS = BigDecimal.valueOf(100_000);

    private static final ExecutorService executorService = new ThreadPoolExecutor(4, 10, 10, TimeUnit.MINUTES, new LinkedBlockingDeque<>());

    public ResultCode cancelOrderByContractId(Long contractId, Map<String, String> userInfoMap) throws Exception {
        if (Objects.isNull(contractId)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }

        ResultCode resultCode = ResultCode.success();
        ToCancelMessage toCancelMessage = new ToCancelMessage();
        toCancelMessage.setCancelType(CancelTypeEnum.CANCEL_BY_CONTRACTID);
        toCancelMessage.setContractId(contractId);
        rocketMqManager.sendMessage(ORDER_TOPIC, TO_CANCEL_CONTRACT_TAG, "to_cancelByContractId_"+contractId, toCancelMessage);
        return resultCode;
    }

    public ResultCode cancelOrderByOrderType(long userId, List<Integer> orderTypes, Map<String, String> userInfoMap) throws Exception {
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.listByUserIdAndOrderType(userId, orderTypes);
        if (!CollectionUtils.isEmpty(list)) {
            Predicate<ContractOrderDO> isNotEnforce = contractOrderDO -> contractOrderDO.getOrderType() != OrderTypeEnum.ENFORCE.getCode();
            Predicate<ContractOrderDO> isCommit = contractOrderDO -> contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode();
            Predicate<ContractOrderDO> isPartMatch = contractOrderDO -> contractOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode();
            List<Long> orderDOList = list.stream()
                    .filter(isCommit.or(isPartMatch).and(isNotEnforce))
                    .map(ContractOrderDO::getId)
                    .collect(toList());

            sendCancelReq(orderDOList, userId);
        }

        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }


//    @Transactional(rollbackFor = Throwable.class)
    public Result<Long> placeOrder(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap) throws Exception{

        Long orderId = BasicUtils.generateId();
        Profiler profiler = null == ThreadContextUtil.getPrifiler() ?
                new Profiler("ContractOrderManager.placeOrder", orderId.toString()): ThreadContextUtil.getPrifiler();
        profiler.setTraceId(orderId.toString());

        //检查委托参数
        Result checkRes = checkOrderDTO(contractOrderDTO);
        if (!checkRes.isSuccess()) {
            return Result.fail(checkRes.getCode(), checkRes.getMessage());
        }

        long contractId = contractOrderDTO.getContractId();
        //检查合约
        ContractCategoryDTO contractCategoryDO = contractCategoryService.getContractById(contractId);
        profiler.complelete("select contract category");
        Result checkContractRest = checkConctractCategory(contractCategoryDO, contractId);
        if (!checkContractRest.isSuccess()) {
            return Result.fail(checkContractRest.getCode(), checkContractRest.getMessage());
        }


        //根据用户等级获取费率
        boolean ret = marketAccountListService.contains(contractOrderDTO.getUserId());
        String userType = StringUtils.isEmpty(userInfoMap.get("userType")) ? "0" : userInfoMap.get("userType");
        BigDecimal feeRate = Constant.FEE_RATE;
        if (userType.equals(Constant.MARKET_MAKER_ACCOUNT_TAG) || ret){
            feeRate = BigDecimal.ZERO;
        }

        String username = org.apache.commons.lang3.StringUtils.isEmpty(userInfoMap.get("username")) ? "" : userInfoMap.get("username");
        String ipAddress = org.apache.commons.lang3.StringUtils.isEmpty(userInfoMap.get("ip")) ? "" : userInfoMap.get("ip");
        ContractOrderDO contractOrderDO = com.fota.trade.common.BeanUtils.extractContractOrderDO(orderId, contractOrderDTO, username, feeRate);

        //委托冻结的中间值
        Map<String, Object> entrustInternalValues = new HashMap<>();

        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            insertOrderRecord(contractOrderDO);
        } else {
            Result<OrderResult> judgeRet = judgeOrderAvailable(contractOrderDO.getUserId(), contractOrderDO, contractOrderDTO.getEntrustValue());
            profiler.complelete("judge order available");

            if (!judgeRet.isSuccess()) {
                return Result.fail(judgeRet.getCode(), judgeRet.getMessage());
            }
            if (null != judgeRet.getData().getEntrustInternalValues()) {
                entrustInternalValues.putAll(judgeRet.getData().getEntrustInternalValues());
            }
            if (null != judgeRet.getData().getLever()) {
                contractOrderDO.setLever(judgeRet.getData().getLever());
            }
            insertOrderRecord(contractOrderDO);
            orderId = contractOrderDO.getId();
            profiler.complelete("insert record");
        }

        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            // 强平单
            JSONObject jsonObject = JSONObject.parseObject(contractOrderDO.getOrderContext());
            // 日志系统需要
            if (jsonObject != null && !jsonObject.isEmpty()) {
                username = jsonObject.get("username") == null ? "" : jsonObject.get("username").toString();
            }
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    2, contractOrderDO.getContractName(), username, ipAddress, contractOrderDO.getTotalAmount(),
                    System.currentTimeMillis(), 3, contractOrderDO.getOrderDirection(), contractOrderDO.getUserId(), 2);
        } else {
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    2, contractOrderDO.getContractName(), username, ipAddress, contractOrderDO.getTotalAmount(),
                    System.currentTimeMillis(), 2, contractOrderDO.getOrderDirection(), contractOrderDO.getUserId(), 1);
        }
        Runnable runnable = () -> {
            sendPlaceOrderMessage(contractOrderDO, contractCategoryDO.getContractType(), contractCategoryDO.getAssetName());
            String userContractPositionExtraKey = RedisKey.getUserContractPositionExtraKey(contractOrderDO.getUserId());
            redisManager.hPutAll(userContractPositionExtraKey, entrustInternalValues);
            profiler.complelete("send MQ message");
        };
        ThreadContextUtil.setPostTask(runnable);
        return Result.suc(orderId);
    }

    private Result checkConctractCategory(ContractCategoryDTO contractCategoryDO, long contractId){
        if (contractCategoryDO == null) {
            log.error("contract is null, id={}", contractId);
            return Result.fail(ILLEGAL_CONTRACT.getCode(), ILLEGAL_CONTRACT.getMessage());
        }
        if (contractCategoryDO.getStatus() == ContractStatusEnum.ROOLING_BACK.getCode()){
            return Result.fail(CONTRACT_IS_ROLLING_BACK.getCode(), CONTRACT_IS_ROLLING_BACK.getMessage());
        }
        if(contractCategoryDO.getStatus() == ContractStatusEnum.DELIVERYING.getCode()){
            return Result.fail(CONTRACT_IS_DELIVERYING.getCode(), CONTRACT_IS_DELIVERYING.getMessage());
        }
        if(contractCategoryDO.getStatus() == ContractStatusEnum.DELIVERED.getCode()){
            return Result.fail(CONTRACT_HAS_DELIVERIED.getCode(), CONTRACT_HAS_DELIVERIED.getMessage());
        }
        if(contractCategoryDO.getStatus() != PROCESSING.getCode()){
            return Result.fail(ILLEGAL_CONTRACT.getCode(), ILLEGAL_CONTRACT.getMessage());
        }
        return Result.suc(null);
    }
    private void sendPlaceOrderMessage(ContractOrderDO contractOrderDO, Integer contractType, String assetName){
        //推送MQ消息
        ContractPlaceOrderMessage placeOrderMessage = new ContractPlaceOrderMessage();
        placeOrderMessage.setTotalAmount(contractOrderDO.getTotalAmount());
        if (contractOrderDO.getPrice() != null){
            placeOrderMessage.setPrice(contractOrderDO.getPrice());
        }
        placeOrderMessage.setOrderDirection(contractOrderDO.getOrderDirection());
        placeOrderMessage.setOrderType(contractOrderDO.getOrderType());
        placeOrderMessage.setOrderId(contractOrderDO.getId());
        placeOrderMessage.setUserId(contractOrderDO.getUserId());
        placeOrderMessage.setSubjectId(contractOrderDO.getContractId());
        placeOrderMessage.setSubjectName(contractOrderDO.getContractName());
        placeOrderMessage.setFee(contractOrderDO.getFee());
        boolean sendRet = rocketMqManager.sendMessage(TopicConstants.TRD_CONTRACT_ORDER, placeOrderMessage.getSubjectId()+"", placeOrderMessage.getOrderId()+"", placeOrderMessage);
        if (!sendRet) {
            log.error("Send RocketMQ Message Failed ");
        }
    }

    public ResultCode cancelOrder(Long userId, Long orderId, Map<String, String> userInfoMap) throws Exception {
        if (Objects.isNull(userId) || Objects.isNull(orderId)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByIdAndUserId(userId, orderId);
        if (Objects.isNull(contractOrderDO)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            return ResultCode.error(ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getCode(),
                    ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getMessage());
        }
        ContractCategoryDTO contractCategoryDO = contractCategoryService.getContractById(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
            return ResultCode.error(BIZ_ERROR.getCode(),"contract is null, id="+contractOrderDO.getContractId());
        }
        if (contractCategoryDO.getStatus() != PROCESSING.getCode()){
            log.error("contract status illegal,can not cancel{}", contractCategoryDO);
            return ResultCode.error(BIZ_ERROR.getCode(),"illegal status, id="+contractCategoryDO.getId() + ", status="+ contractCategoryDO.getStatus());
        }
        ResultCode resultCode = ResultCode.success();
        List<Long> orderIdList = Collections.singletonList(orderId);
        sendCancelReq(orderIdList, userId);
        return resultCode;
    }

    /**
     * 根据撮合发出的MQ消息撤单
     */
//    @Transactional(rollbackFor = Throwable.class)
    public ResultCode cancelOrderByMessage(BaseCanceledMessage canceledMessage) {

        long userId = canceledMessage.getUserId();
        long orderId = canceledMessage.getOrderId();
        BigDecimal unfilleAmount = canceledMessage.getUnfilledAmount();
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByIdAndUserId(userId, orderId);
        if (Objects.isNull(contractOrderDO)) {
            return ResultCode.error(ILLEGAL_PARAM.getCode(), "contract order does not exist, id="+orderId);
        }
        Integer status = contractOrderDO.getStatus();

        if (status != COMMIT.getCode() && status != PART_MATCH.getCode()) {
            return ResultCode.error(ILLEGAL_PARAM.getCode(),"illegal order status, id="+contractOrderDO.getId() + ", status="+ contractOrderDO.getStatus());
        }
        Integer toStatus = unfilleAmount.compareTo(contractOrderDO.getTotalAmount()) < 0 ? PART_CANCEL.getCode() : CANCEL.getCode();

        Long transferTime = System.currentTimeMillis();
        int ret = contractOrderMapper.cancel(userId, orderId, toStatus);
        if (ret > 0) {
        } else {
            return ResultCode.error(ILLEGAL_PARAM.getCode(),"cancel failed, id="+ contractOrderDO.getId());
        }
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO);
        contractOrderDTO.setCompleteAmount(contractOrderDTO.getTotalAmount().subtract(unfilleAmount));
        contractOrderDTO.setContractId(contractOrderDO.getContractId());
        JSONObject jsonObject = JSONObject.parseObject(contractOrderDO.getOrderContext());
        // 日志系统需要
        String username = "";
        if (jsonObject != null && !jsonObject.isEmpty()) {
            username = jsonObject.get("username") == null ? "" : jsonObject.get("username").toString();
        }
        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, contractOrderDTO.getContractName(), username, "", contractOrderDTO.getUnfilledAmount(),
                System.currentTimeMillis(), 1, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 1);
        sendCanceledMessage(canceledMessage);
        updateExtraEntrustAmountByContract(contractOrderDO.getUserId(), contractOrderDO.getContractId());
        return ResultCode.success();
    }

    public void sendCanceledMessage(BaseCanceledMessage canceledMessage){
        boolean sendRet = rocketMqManager.sendMessage(TRD_CONTRACT_CANCELED, canceledMessage.getSubjectId()+"", canceledMessage.getOrderId()+"", canceledMessage);
        if (!sendRet) {
            log.error("send canceled message failed, message={}", canceledMessage);
        }
    }

    public void sendCancelReq(List<Long> orderIdList, Long userId) {
        if (CollectionUtils.isEmpty(orderIdList)) {
            log.error("empty orderList");
            return;
        }
        //批量发送MQ消息到match
        int i = 0;
        int batchSize = 10;
        while (i < orderIdList.size()) {
            int temp =  i + batchSize;
            temp = temp < orderIdList.size() ? temp : orderIdList.size();
            List<Long> subList = orderIdList.subList(i, temp);
            BaseCancelReqMessage toCancelMessage = new BaseCancelReqMessage();
            toCancelMessage.setCancelType(CancelTypeEnum.CANCEL_BY_ORDERID);
            toCancelMessage.setUserId(userId);
            toCancelMessage.setIdList(subList);
            String msgKey = "to_cancel_contract_"+Joiner.on(",").join(subList);
            rocketMqManager.sendMessage(TopicConstants.TRD_CONTRACT_CANCEL_REQ, "contract", msgKey , toCancelMessage);
            i = temp;
        }
    }

    public ResultCode cancelAllOrder(Long userId, Map<String, String> userInfoMap) throws Exception {
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByUserId(userId);
        List<ContractOrderDO> listFilter = new ArrayList<>();
        for (ContractOrderDO temp : list){
            ContractCategoryDTO contractCategoryDO = contractCategoryService.getContractById(temp.getContractId());
            if (contractCategoryDO.getStatus() != PROCESSING.getCode()){
                log.error("contract status illegal,can not cancel{}", contractCategoryDO);
                continue;
            }
            if (temp.getOrderType() != OrderTypeEnum.ENFORCE.getCode()){
                listFilter.add(temp);
            }
        }
        if (listFilter != null){
            List<Long> orderIdList = list.stream()
                    .map(ContractOrderDO::getId)
                    .collect(toList());

            sendCancelReq(orderIdList, userId);
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }



    /**
     *
     * @param userId
     * @return
     */
    public  ContractAccount computeContractAccount(long userId) {

        ContractAccount contractAccount = new ContractAccount();
        contractAccount.setMarginCallRequirement(BigDecimal.ZERO)
                .setFrozenAmount(BigDecimal.ZERO)
                .setFloatingPL(BigDecimal.ZERO)
                .setUserId(userId);

        UserContractDTO userContractDTO = assetService.getContractAccount(userId);
        if (null == userContractDTO) {
            log.error("null userContractDTO, userId={}", userId);
            return null;
        }

        List<ContractCategoryDTO> categoryList = contractCategoryService.listActiveContract();
        if (CollectionUtils.isEmpty(categoryList)) {
            log.error("empty categoryList, userId={}", userId);
             return contractAccount.setAccountEquity(new BigDecimal(userContractDTO.getAmount()));
        }
        List<UserPositionDO> allPositions = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        List<CompetitorsPriceDTO> competitorsPrices = realTimeEntrustManager.getContractCompetitorsPriceOrder();
        List<UserContractLeverDO> contractLeverDOS = userContractLeverMapper.listUserContractLever(userId);

        String userContractPositionExtraKey = RedisKey.getUserContractPositionExtraKey(userId);
        Map<String, Object> userContractPositions =  redisManager.hEntries(userContractPositionExtraKey);
        if (Objects.isNull(userContractPositions)) {
            userContractPositions = Collections.emptyMap();
        }
        BigDecimal totalPositionMarginByIndex = BigDecimal.ZERO;
        BigDecimal totalPositionValueByIndex = BigDecimal.ZERO;
        BigDecimal totalFloatingPLByIndex = BigDecimal.ZERO;
        BigDecimal totalEntrustMarginByIndex = BigDecimal.ZERO;
        //todo 获取简单交割指数列表
        List<TickerDTO> list = new ArrayList<>();
        try{
            list = currentPriceManager.getSpotIndexes();
        }catch (Exception e){
            log.error("get simpleIndexList failed", e);
        }
        Map<String, Object> map = new HashMap<>();
        for (ContractCategoryDTO contractCategoryDO : categoryList) {
            long contractId = contractCategoryDO.getId();
            BigDecimal lever = findLever(contractLeverDOS, userId, contractCategoryDO.getAssetId());
            BigDecimal positionMargin = BigDecimal.ZERO;
            BigDecimal floatingPL = BigDecimal.ZERO;
            BigDecimal entrustMargin = BigDecimal.ZERO;
            BigDecimal entrustMarginByIndex = BigDecimal.ZERO;
            BigDecimal positionUnfilledAmount= BigDecimal.ZERO;
            BigDecimal positionMarginByIndex = BigDecimal.ZERO;
            BigDecimal floatingPLByIndex = BigDecimal.ZERO;
            BigDecimal positionValueByIndex = BigDecimal.ZERO;
            int positionType = PositionTypeEnum.EMPTY.getCode();
            Optional<UserPositionDO> userPositionDOOptional = allPositions.stream()
                    .filter(userPosition -> userPosition.getContractId().equals(contractCategoryDO.getId()))
                    .findFirst();
            //计算保证金，浮动盈亏
            if (userPositionDOOptional.isPresent()) {
                //获取交割指数
                BigDecimal index = getIndex(contractCategoryDO.getContractName() , list);
                UserPositionDO userPositionDO = userPositionDOOptional.get();
                positionType = userPositionDO.getPositionType();
                positionUnfilledAmount = userPositionDO.getUnfilledAmount();
                BigDecimal positionAveragePrice = userPositionDO.getAveragePrice();

                int dire = ContractUtils.toDirection(userPositionDO.getPositionType());

                BigDecimal price = computePrice(competitorsPrices, userPositionDO.getPositionType(), contractId);
                if (null == price) {
                    return null;
                }
                floatingPL = price.subtract(positionAveragePrice).multiply(positionUnfilledAmount).multiply(new BigDecimal(dire));
                positionMargin = positionUnfilledAmount.multiply(price).divide(lever, scale, BigDecimal.ROUND_UP);

                floatingPLByIndex = index.compareTo(BigDecimal.ZERO) == 0 ? floatingPL :
                        index.subtract(positionAveragePrice).multiply(positionUnfilledAmount).multiply(new BigDecimal(dire));
                positionMarginByIndex = index.compareTo(BigDecimal.ZERO) == 0 ? positionMargin :
                        positionUnfilledAmount.multiply(index).divide(lever, scale, BigDecimal.ROUND_UP);
                positionValueByIndex = index.compareTo(BigDecimal.ZERO) == 0 ? positionUnfilledAmount.multiply(price) :
                        positionUnfilledAmount.multiply(index);
            }

            //计算委托额外保证金
            String contraryKey = "", sameKey = "";
            if (positionType == PositionTypeEnum.OVER.getCode()) {
                contraryKey = contractId + "-" + PositionTypeEnum.EMPTY.name();
                sameKey = contractId + "-" + PositionTypeEnum.OVER.name();
            } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
                contraryKey = contractId + "-" + PositionTypeEnum.OVER.name();
                sameKey = contractId + "-" + PositionTypeEnum.EMPTY.name();
            }

            Object contraryValue = userContractPositions.get(contraryKey);
            Object sameValue = userContractPositions.get(sameKey);
            EntrustMarginDO entrustMarginDO = new EntrustMarginDO();
            if (Objects.nonNull(contraryValue) && Objects.nonNull(sameValue)) {
                entrustMarginDO = cal(new BigDecimal(contraryValue.toString()), new BigDecimal(sameValue.toString()), positionMargin, positionMarginByIndex);
            } else {
                List<ContractOrderDO> orderList =  contractOrderMapper.selectNotEnforceOrderByUserIdAndContractId(userId, contractId);
                if (CollectionUtils.isEmpty(orderList)) {
                    orderList = Collections.emptyList();
                }
                List<ContractOrderDO> bidList = orderList.stream()
                        .filter(order -> order.getOrderDirection() == OrderDirectionEnum.BID.getCode())
                        .collect(toList());
                List<ContractOrderDO> askList = orderList.stream()
                        .filter(order -> order.getOrderDirection() == ASK.getCode())
                        .collect(toList());
                entrustMarginDO = getExtraEntrustAmount(userId, contractId, bidList, askList, positionType, positionUnfilledAmount, positionMargin, positionMarginByIndex, lever);
                Pair<BigDecimal, Map<String, Object>> pair = entrustMarginDO.getPair();
                entrustMargin = pair.getLeft();
                map.putAll(pair.getRight());
            }
            entrustMargin = entrustMarginDO.getEntrustMargin();
            entrustMarginByIndex = entrustMarginDO.getEntrustMarginByIndex();
            contractAccount.setMarginCallRequirement(contractAccount.getMarginCallRequirement().add(positionMargin))
                    .setFrozenAmount(contractAccount.getFrozenAmount().add(entrustMargin))
                    .setFloatingPL(contractAccount.getFloatingPL().add(floatingPL));
            totalPositionMarginByIndex = totalPositionMarginByIndex.add(positionMarginByIndex);
            totalFloatingPLByIndex = totalFloatingPLByIndex.add(floatingPLByIndex);
            totalPositionValueByIndex = totalPositionValueByIndex.add(positionValueByIndex);
            totalEntrustMarginByIndex = totalEntrustMarginByIndex.add(entrustMarginByIndex);
        }
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        contractAccount.setAvailableAmount(amount.add(contractAccount.getFloatingPL())
                .subtract(contractAccount.getMarginCallRequirement())
                .subtract(contractAccount.getFrozenAmount()));
        contractAccount.setAccountEquity(amount.add(contractAccount.getFloatingPL()));
        //计算强平安全边际
        //通过简单现货指数计算出的账户权益
        BigDecimal accountEquityByIndex = amount.add(totalFloatingPLByIndex);
        BigDecimal T2 = new BigDecimal("0.6");
        if (totalPositionMarginByIndex.compareTo(BigDecimal.ZERO) == 0){
            contractAccount.setSecurityBorder(accountEquityByIndex.subtract((T2.multiply(totalPositionMarginByIndex.add(totalEntrustMarginByIndex)))));
        }else {
            BigDecimal L = totalPositionValueByIndex.divide(totalPositionMarginByIndex, scale, BigDecimal.ROUND_DOWN);
            BigDecimal securityBorder = accountEquityByIndex.subtract((T2.multiply(totalPositionMarginByIndex.add(totalEntrustMarginByIndex)))).
                    divide((new BigDecimal("1").subtract(T2.divide(L, scale, BigDecimal.ROUND_DOWN))), scale, BigDecimal.ROUND_DOWN);
            contractAccount.setSecurityBorder(securityBorder);
        }
        contractAccount.setAccountMargin(contractAccount.getFrozenAmount().add(contractAccount.getMarginCallRequirement()));
        contractAccount.setSuggestedAddAmount(totalPositionValueByIndex.add(totalEntrustMarginByIndex).subtract(contractAccount.getAccountEquity()).max(BigDecimal.ZERO));
        redisManager.hPutAll(userContractPositionExtraKey, map);
        return contractAccount;

    }

    public BigDecimal getIndex(String contractName, List<TickerDTO> list){
        BigDecimal index = BigDecimal.ZERO;
        String symbol = contractName.substring(0, 3);
        Optional<TickerDTO> op = list.stream().filter(x->x.getSymbol().equals(symbol)).findFirst();
        if (!op.isPresent()){
           log.error("get simpleIndex faild, contractName{}", contractName);
           return index;
        }
        index = op.get().getPrice();
        return index;
    }

    public BigDecimal findLever(List<UserContractLeverDO> contractLeverDOS, long userId, long assetId){
        if(CollectionUtils.isEmpty(contractLeverDOS)) {
            return DEFAULT_LEVER;
        }
        Optional<UserContractLeverDO> leverDO = contractLeverDOS.stream().filter(x -> x.getUserId().equals(userId) && Long.valueOf(x.getAssetId()).equals(assetId)).findFirst();
        return leverDO.map(userContractLeverDO -> new BigDecimal(userContractLeverDO.getLever())).orElse(DEFAULT_LEVER);
    }

    public BigDecimal computePrice(List<CompetitorsPriceDTO> competitorsPriceList, int type, long contractId) {
        if (CollectionUtils.isEmpty(competitorsPriceList)) {
            String ret = redisManager.get(Constant.LAST_CONTRACT_MATCH_PRICE + String.valueOf(contractId));
            if (null == ret) {
                log.error("there is no latestMatchedPrice, contractId={}, type={}", contractId, type);
            }
            return new BigDecimal(ret);
        }
        Optional<CompetitorsPriceDTO>  competitorsPriceDTOOptional = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == type &&
                competitorsPrice.getId() == contractId).findFirst();

        if (competitorsPriceDTOOptional.isPresent() && competitorsPriceDTOOptional.get().getPrice().compareTo(BigDecimal.ZERO) > 0) {
            return competitorsPriceDTOOptional.get().getPrice();
        }
        String latestPrice = redisManager.get(Constant.LAST_CONTRACT_MATCH_PRICE + String.valueOf(contractId));
        if (latestPrice != null) {
            return new BigDecimal(latestPrice);
        }
        log.error("there is no latestMatchedPrice, contractId={}, type={}", contractId, type);
        return null;
    }

    //todo 判断持仓反方向的"仓加挂"大于是否该合约持仓保证金
    public Boolean judgeOrderResult(List<ContractOrderDO> filterOrderList,Integer positionType,
                                            BigDecimal positionUnfilledAmount, BigDecimal positionEntrustAmount, BigDecimal lever) {
        if (null == positionUnfilledAmount) {
            log.error("null positionUnfilledAmount");
            positionUnfilledAmount = BigDecimal.ZERO;
        }
        BigDecimal totalEntrustAmount = BigDecimal.ZERO;
        BigDecimal entrustAmount = BigDecimal.ZERO;
        BigDecimal listFee = BigDecimal.ZERO;
        if (filterOrderList != null && filterOrderList.size() != 0) {
            List<ContractOrderDO> sortedList = new ArrayList<>();
            if (positionType == PositionTypeEnum.OVER.getCode()){
                sortedList = sortListAsc(filterOrderList);
            }else {
                sortedList = sortListDesc(filterOrderList);
            }
            Integer flag = 0;
            for (int i = 0; i < sortedList.size(); i++) {
                listFee = listFee.add(sortedList.get(i).getPrice().multiply(sortedList.get(i).getUnfilledAmount()).multiply(Constant.FEE_RATE));
                positionUnfilledAmount = positionUnfilledAmount.subtract(sortedList.get(i).getUnfilledAmount());
                if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0 && flag.equals(0)) {
                    flag = 1;
                    BigDecimal restAmount = positionUnfilledAmount.negate().multiply(sortedList.get(i).getPrice()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                    for (int j = i + 1; j < sortedList.size(); j++) {
                        BigDecimal orderAmount = sortedList.get(j).getPrice().multiply(sortedList.get(j).getUnfilledAmount()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                        BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        entrustAmount = entrustAmount.add(orderAmount.add(orderFee));
                    }
                    totalEntrustAmount = restAmount.add(entrustAmount);
                }
            }
            totalEntrustAmount = totalEntrustAmount.add(listFee);
            if (totalEntrustAmount.compareTo(positionEntrustAmount) <= 0){
                return true;
            }
        }
        return false;
    }


    /**
     * 获取多空仓额外保证金
     * @return 额外保证金和委托冻结中间值
     */
    public EntrustMarginDO getExtraEntrustAmount(Long userId, Long contractId,
                                            List<ContractOrderDO> bidList, List<ContractOrderDO> askList,
                                            Integer positionType, BigDecimal positionUnfilledAmount,
                                            BigDecimal positionEntrustAmount,BigDecimal positionMarginByIndex, BigDecimal lever) {
        if (null == positionUnfilledAmount) {
            log.error("null positionUnfilledAmount");
            positionUnfilledAmount = BigDecimal.ZERO;
        }

        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal entrustAmount = BigDecimal.ZERO;
        BigDecimal totalContraryEntrustAmount = BigDecimal.ZERO;
        BigDecimal totalSameEntrustAmount = BigDecimal.ZERO;
        String contraryKey = "", sameKey = "";

        List<ContractOrderDO> contrarySortedList, sameList;
        if (positionType == PositionTypeEnum.OVER.getCode()) {
            contrarySortedList = sortListAsc(askList);
            sameList = bidList;
            contraryKey = contractId + "-" + PositionTypeEnum.EMPTY.name();
            sameKey = contractId + "-" + PositionTypeEnum.OVER.name();
        } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
            contrarySortedList = sortListDesc(bidList);
            sameList = askList;
            contraryKey = contractId + "-" + PositionTypeEnum.OVER.name();
            sameKey = contractId + "-" + PositionTypeEnum.EMPTY.name();
        } else {
            throw new RuntimeException("positionType illegal");
        }

        int flag = 0;
        for (int i = 0; i < contrarySortedList.size(); i++) {
            fee = fee.add(contrarySortedList.get(i).getPrice()
                    .multiply(contrarySortedList.get(i).getUnfilledAmount())
                    .multiply(Constant.FEE_RATE));
            positionUnfilledAmount = positionUnfilledAmount.subtract(contrarySortedList.get(i).getUnfilledAmount());
            if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0 && flag == 0) {
                flag = 1;
                BigDecimal restAmount = positionUnfilledAmount.negate()
                        .multiply(contrarySortedList.get(i).getPrice())
                        .divide(lever, 8, BigDecimal.ROUND_DOWN);
                for (int j = i + 1; j < contrarySortedList.size(); j++) {
                    BigDecimal orderAmount = contrarySortedList.get(j).getPrice()
                            .multiply(contrarySortedList.get(j).getUnfilledAmount())
                            .divide(lever, 8, BigDecimal.ROUND_DOWN);
                    entrustAmount = entrustAmount.add(orderAmount);
                }
                totalContraryEntrustAmount = restAmount.add(entrustAmount);
            }
        }
        totalContraryEntrustAmount = totalContraryEntrustAmount.add(fee);

        for (ContractOrderDO contractOrderDO : sameList) {
            BigDecimal orderAmount = contractOrderDO.getPrice()
                    .multiply(contractOrderDO.getUnfilledAmount())
                    .divide(lever, 8, BigDecimal.ROUND_DOWN);
            BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
            totalSameEntrustAmount = totalSameEntrustAmount.add(orderAmount.add(orderFee));
        }

        Map<String, Object> map = new HashMap<>();
        map.put(contraryKey, totalContraryEntrustAmount.toPlainString());
        map.put(sameKey, totalSameEntrustAmount.toPlainString());
        EntrustMarginDO entrustMarginDO =  new EntrustMarginDO();
        entrustMarginDO =  cal(totalContraryEntrustAmount, totalSameEntrustAmount, positionEntrustAmount, positionMarginByIndex);
        BigDecimal entrustMargin = entrustMarginDO.getEntrustMargin();
        entrustMarginDO.setPair(Pair.of(entrustMargin, map));
        return entrustMarginDO;
    }

    private EntrustMarginDO cal(BigDecimal totalContraryEntrustAmount, BigDecimal totalSameEntrustAmount, BigDecimal positionEntrustAmount, BigDecimal positionMarginByIndex) {
        EntrustMarginDO entrustMarginDO = new EntrustMarginDO();
        BigDecimal max1 = totalContraryEntrustAmount.subtract(positionEntrustAmount).max(BigDecimal.ZERO);
        max1 = totalSameEntrustAmount.max(max1);
        BigDecimal max2 = BigDecimal.ZERO;
        if (positionMarginByIndex.compareTo(BigDecimal.ZERO) > 0){
            max2 = totalContraryEntrustAmount.subtract(positionEntrustAmount).max(BigDecimal.ZERO);
            max2 = totalSameEntrustAmount.max(max2);
        }
        entrustMarginDO.setEntrustMargin(max1);
        entrustMarginDO.setEntrustMarginByIndex(max2);
        return entrustMarginDO;
    }

    public void insertOrderRecord(ContractOrderDO contractOrderDO){
        if (contractOrderDO.getOrderType().equals(PriceTypeEnum.RIVAL_PRICE.getCode())) {
            contractOrderDO.setOrderType(PriceTypeEnum.SPECIFIED_PRICE.getCode());
        }
        int insertContractOrderRet = contractOrderMapper.insert(contractOrderDO);
        if (insertContractOrderRet <= 0) {
            log.error("insert contractOrder failed");
            throw new RuntimeException("insert contractOrder failed");
        }
    }

    //升序排列
    public List<ContractOrderDO> sortListAsc(List<ContractOrderDO> list) {
        return list.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice))
                .collect(toList());
    }

    //降序排列
    public List<ContractOrderDO> sortListDesc(List<ContractOrderDO> list) {
        List<ContractOrderDO> sortedList = list.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice).reversed())
                .collect(toList());
        return sortedList;
    }


    public BigDecimal getTotalLockAmount(ContractOrderDO contractOrderDO) {
        Integer lever = contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(), contractOrderDO.getContractId());
        BigDecimal totalValue = contractOrderDO.getPrice().multiply(contractOrderDO.getTotalAmount())
                .multiply(new BigDecimal(0.01)).divide(new BigDecimal(lever), 8, BigDecimal.ROUND_DOWN);
        BigDecimal fee = totalValue.multiply(Constant.FEE_RATE).multiply(new BigDecimal(lever));
        return totalValue.add(fee);
    }




    public ResultCode checkMatchOrderDTO(ContractMatchedOrderDTO contractMatchedOrderDTO) {
        if (contractMatchedOrderDTO == null || null == contractMatchedOrderDTO.getAskUserId() || null == contractMatchedOrderDTO.getBidUserId()
                || null == contractMatchedOrderDTO.getAskOrderId() || null == contractMatchedOrderDTO.getBidOrderId() || null == contractMatchedOrderDTO.getFilledAmount()
                || null == contractMatchedOrderDTO.getFilledPrice()) {
            log.error(ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        return ResultCode.success();
    }

    public Result checkOrderDTO(ContractOrderDTO contractOrderDTO) {
        if (null == contractOrderDTO   || null == contractOrderDTO.getUserId() || null == contractOrderDTO.getContractId()
                || null == contractOrderDTO.getOrderDirection()) {
            return Result.fail(ILLEGAL_PARAM.getCode(), ILLEGAL_PARAM.getMessage());
        }
        if ( null == contractOrderDTO.getTotalAmount() && null == contractOrderDTO.getEntrustValue()) {
            return Result.fail(ILLEGAL_PARAM.getCode(), ILLEGAL_PARAM.getMessage());
        }
        if (ASK.getCode() != contractOrderDTO.getOrderDirection() && BID.getCode() != contractOrderDTO.getOrderDirection()) {
            return Result.fail(ILLEGAL_ORDER_DIRECTION.getCode(), ILLEGAL_ORDER_DIRECTION.getMessage());
        }
        return Result.suc(null);
    }

    /**
     * 根据不同价格策略，获取下单价格
     * @param competitorsPriceList
     * @param orderType
     * @param orderPrice
     * @param contractId
     * @param orderDeriction
     * @return
     */
    public Result<BigDecimal> computeAndCheckOrderPrice(List<CompetitorsPriceDTO> competitorsPriceList, Integer orderType, BigDecimal orderPrice, long assetId, Long contractId, int orderDeriction) {

        if (null == orderType) {
            return Result.fail(PRICE_TYPE_ILLEGAL.getCode(), PRICE_TYPE_ILLEGAL.getMessage());
        }
        if (RIVAL_PRICE.getCode() != orderType && MARKET_PRICE.getCode() != orderType && SPECIFIED_PRICE.getCode() != orderType){
            return Result.fail(PRICE_TYPE_ILLEGAL.getCode(), PRICE_TYPE_ILLEGAL.getMessage());
        }

        //无论如何都要获取交割指数
        BigDecimal indexes = currentPriceManager.getSpotIndexByAssetId(assetId);
        Integer scale = AssetExtraProperties.getPrecisionByAssetId(assetId);
        BigDecimal buyMaxPrice = indexes.multiply(new BigDecimal("1.05")).setScale(scale, RoundingMode.UP);
        BigDecimal sellMinPrice = indexes.multiply(new BigDecimal("0.95")).setScale(scale, BigDecimal.ROUND_DOWN);

        //市场单
        if (orderType == MARKET_PRICE.getCode()) {
            if (orderDeriction == ASK.getCode()) {
                return Result.suc(sellMinPrice);
            }
            return Result.suc(buyMaxPrice);
        }

        //对手价
        if (orderType == RIVAL_PRICE.getCode()){
            Integer opDirection = ASK.getCode() + BID.getCode() - orderDeriction;
            Optional<CompetitorsPriceDTO> currentPrice = competitorsPriceList.stream().filter(competitorsPrice-> competitorsPrice.getOrderDirection() == opDirection &&

                    competitorsPrice.getId() == contractId.intValue()).findFirst();
            if (currentPrice.isPresent() && currentPrice.get().getPrice().compareTo(BigDecimal.ZERO) > 0){
                BigDecimal actualPrice = currentPrice.get().getPrice();
                Integer precision = AssetExtraProperties.getPrecisionByAssetId(assetId);
                if (null != precision) {
                    actualPrice = actualPrice.setScale(precision, BigDecimal.ROUND_DOWN);
                }
                return Result.suc(actualPrice);
            }
            log.info("contractId={}, competitorsPriceList={}", contractId, competitorsPriceList);
            return Result.fail(NO_COMPETITORS_PRICE.getCode(), NO_COMPETITORS_PRICE.getMessage());
        }

        //校验价格
        if (orderPrice.compareTo(BigDecimal.ZERO) <= 0){
            return Result.fail(AMOUNT_ILLEGAL.getCode(), AMOUNT_ILLEGAL.getMessage());
        }
        if ( scale < orderPrice.scale()) {
            return Result.fail(AMOUNT_ILLEGAL.getCode(), AMOUNT_ILLEGAL.getMessage());
        }
        if (indexes != null && indexes.compareTo(BigDecimal.ZERO) != 0){
            if (orderDeriction == ASK.getCode() && orderPrice.compareTo(sellMinPrice) < 0){
                return Result.fail(PRICE_OUT_OF_BOUNDARY.getCode(), PRICE_OUT_OF_BOUNDARY.getMessage());
            }
            if (orderDeriction == OrderDirectionEnum.BID.getCode() && orderPrice.compareTo(buyMaxPrice) > 0){
                return Result.fail(PRICE_OUT_OF_BOUNDARY.getCode(), PRICE_OUT_OF_BOUNDARY.getMessage());
            }
        }
        return Result.suc(orderPrice);

    }

    /**
     * 判断新的合约委托能否下单
     * @param userId
     * @param newContractOrderDO
     * @return
     */
    public Result<OrderResult> judgeOrderAvailable(long userId, ContractOrderDO newContractOrderDO, BigDecimal entrustValue) {
        Profiler profiler = (null == ThreadContextUtil.getPrifiler()) ? new Profiler("judgeOrderAvailable") : ThreadContextUtil.getPrifiler();
        ContractAccount contractAccount = new ContractAccount();
        contractAccount.setMarginCallRequirement(BigDecimal.ZERO)
                .setFrozenAmount(BigDecimal.ZERO)
                .setFloatingPL(BigDecimal.ZERO)
                .setUserId(userId);
        Long orderContractId = newContractOrderDO.getContractId();
        Integer orderDirection = newContractOrderDO.getOrderDirection();
        UserContractDTO userContractDTO = assetService.getContractAccount(userId);
        profiler.complelete("getContractAccount");
        if (null == userContractDTO) {
            log.error("null userContractDTO, userId={}", userId);
            return Result.fail(NO_CONTRACT_BALANCE.getCode(), NO_CONTRACT_BALANCE.getMessage());
        }
        //用户被接管
        if (userContractDTO.getStatus() == LIMIT.getCode()) {
            return Result.fail(CONTRACT_ACCOUNT_HAS_LIMITED.getCode(), CONTRACT_ACCOUNT_HAS_LIMITED.getMessage());
        }


        List<ContractCategoryDTO> categoryList = contractCategoryService.listActiveContract();
        profiler.complelete("listActiveContract");
        //校验合约有效性
        if (CollectionUtils.isEmpty(categoryList)) {
            log.error("empty categoryList, userId={}", userId);
            return Result.fail( ResultCodeEnum.ILLEGAL_CONTRACT.getCode(), ILLEGAL_CONTRACT.getMessage());
        }
        ContractCategoryDTO contractCategoryDTO = categoryList.stream().filter(x -> x.getId().equals(newContractOrderDO.getContractId())).findFirst()
                .orElse(null);
        if (null == contractCategoryDTO) {
            return Result.fail( ResultCodeEnum.ILLEGAL_CONTRACT.getCode(), ILLEGAL_CONTRACT.getMessage());
        }

        List<CompetitorsPriceDTO> competitorsPrices = realTimeEntrustManager.getContractCompetitorsPriceOrder();
        profiler.complelete("getContractCompetitorsPriceOrder");
        //计算合约价格
        Result<BigDecimal> getPriceRes = computeAndCheckOrderPrice(competitorsPrices, newContractOrderDO.getOrderType(), newContractOrderDO.getPrice(),
                contractCategoryDTO.getAssetId(), newContractOrderDO.getContractId(), newContractOrderDO.getOrderDirection());
        if (!getPriceRes.isSuccess()) {
            return Result.fail(getPriceRes.getCode(), getPriceRes.getMessage());
        }
        //重新设置价格
        newContractOrderDO.setPrice(getPriceRes.getData());

        //根据金额计算数量
        if (null != entrustValue) {
            newContractOrderDO.setTotalAmount(entrustValue.divide(newContractOrderDO.getPrice(), scale, BigDecimal.ROUND_DOWN));
        }
        //查询用户所有非强平活跃单
        List<ContractOrderDO> allContractOrders = contractOrderMapper.selectNotEnforceOrderByUserId(userId);
        profiler.complelete("selectNotEnforceOrderByUserId");
        if (null == allContractOrders) {
            allContractOrders = new ArrayList<>();
        }
        allContractOrders.add(newContractOrderDO);

        List<ContractOrderDO> currentContractOrders = allContractOrders.stream()
                .filter(contractOrderDO -> orderContractId.equals(contractOrderDO.getContractId()))
                .collect(toList());

        //合约数量限制
        if (currentContractOrders.size() > 200) {
            throw new BizException(ResultCodeEnum.TOO_MUCH_ORDERS);
        }


        List<UserPositionDO> allPositions = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        profiler.complelete("selectPositionByUserId");

        List<UserContractLeverDO> contractLeverDOS = userContractLeverMapper.listUserContractLever(userId);
        profiler.complelete("listUserContractLever");

        Map<String, Object> map = new HashMap<>();
        OrderResult orderResult = new OrderResult();
        orderResult.setLever(findLever(contractLeverDOS, userId, contractCategoryDTO.getAssetId().longValue()).intValue());
        orderResult.setEntrustInternalValues(map);
        for (ContractCategoryDTO contractCategoryDO : categoryList) {
            long contractId = contractCategoryDO.getId();
            BigDecimal lever = findLever(contractLeverDOS, userId, contractCategoryDO.getAssetId());
            BigDecimal positionMargin = BigDecimal.ZERO;
            BigDecimal floatingPL = BigDecimal.ZERO;
            BigDecimal entrustMargin = BigDecimal.ZERO;
            BigDecimal positionUnfilledAmount= BigDecimal.ZERO;
            int positionType = PositionTypeEnum.EMPTY.getCode();


            List<ContractOrderDO> orderList = null;
            boolean isCurrentContract = orderContractId.equals(contractCategoryDO.getId());
            if (isCurrentContract) {
                orderList = currentContractOrders;
            } else {
                orderList = allContractOrders.stream()
                        .filter(contractOrder -> contractOrder.getContractId().equals(contractId))
                        .collect(toList());
            }
            Optional<UserPositionDO> userPositionDOOptional = allPositions.stream()
                    .filter(userPosition -> userPosition.getContractId().equals(contractCategoryDO.getId()))
                    .findFirst();

            List<ContractOrderDO> sameDirectionOrderList = Collections.emptyList();
            if (isCurrentContract) {
                sameDirectionOrderList = orderList.stream()
                        .filter(order -> order.getOrderDirection().equals(orderDirection))
                        .collect(toList());
            }

            //计算保证金，浮动盈亏
            if (userPositionDOOptional.isPresent()) {
                UserPositionDO userPositionDO = userPositionDOOptional.get();
                positionType = userPositionDO.getPositionType();
                positionUnfilledAmount = userPositionDO.getUnfilledAmount();
                BigDecimal positionAveragePrice = userPositionDO.getAveragePrice();

                int dire = ContractUtils.toDirection(userPositionDO.getPositionType());

                BigDecimal price = computePrice(competitorsPrices, userPositionDO.getPositionType(), contractId);
                if (null == price) {
                    return Result.fail(SYSTEM_ERROR.getCode(), SYSTEM_ERROR.getMessage());
                }
                floatingPL = price.subtract(positionAveragePrice)
                        .multiply(positionUnfilledAmount)
                        .multiply(new BigDecimal(dire));

                positionMargin = positionUnfilledAmount.multiply(price)
                        .divide(lever, scale, BigDecimal.ROUND_UP);

                if (isCurrentContract) {
                    //持仓反方向的"仓加挂"小于该合约持仓保证金，允许下单
                    if (positionType != orderDirection) {
                        if (!CollectionUtils.isEmpty(orderList)) {
                            //计算委托额外保证金
                            Boolean judgeRet = judgeOrderResult(sameDirectionOrderList, positionType, positionUnfilledAmount, positionMargin, lever);
                            if (judgeRet){
                                return Result.suc(orderResult);
                            }
                        }
                    }
                }
            }

            boolean isMarketUser = marketAccountListService.contains(userId);

            //该用户是否达到持仓上限,做市账户不限制
            if (isCurrentContract && !isMarketUser) {
                BigDecimal sameDirectionOrderSum = sameDirectionOrderList.stream()
                        .map(ContractOrderDO::getUnfilledAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal owned = sameDirectionOrderSum
                        .add(positionUnfilledAmount.multiply(BigDecimal.valueOf(positionType == orderDirection ? 1 : -1)));
                BigDecimal limit = BigDecimal.ZERO;
                if (contractCategoryDO.getAssetId() == AssetTypeEnum.EOS.getCode()) {
                    limit = POSITION_LIMIT_EOS;
                } else if (contractCategoryDO.getAssetId() == AssetTypeEnum.BTC.getCode()) {
                    limit = POSITION_LIMIT_BTC;
                } else if (contractCategoryDO.getAssetId() == AssetTypeEnum.ETH.getCode()) {
                    limit = POSITION_LIMIT_ETH;
                }
                if (owned.compareTo(limit) >= 0) {
                    throw new BizException(ResultCodeEnum.POSITION_EXCEEDS);
                }
            }

            //计算委托额外保证金
            List<ContractOrderDO> bidList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.BID.getCode()).collect(toList());
            List<ContractOrderDO> askList = orderList.stream().filter(order -> order.getOrderDirection() == ASK.getCode()).collect(toList());
            Pair<BigDecimal, Map<String, Object>> pair = getExtraEntrustAmount(userId, contractId, bidList, askList, positionType, positionUnfilledAmount, positionMargin, BigDecimal.ZERO, lever).getPair();
            entrustMargin = pair.getLeft();
            map.putAll(pair.getRight());

            contractAccount.setMarginCallRequirement(contractAccount.getMarginCallRequirement().add(positionMargin))
                    .setFrozenAmount(contractAccount.getFrozenAmount().add(entrustMargin))
                    .setFloatingPL(contractAccount.getFloatingPL().add(floatingPL));

        }
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        BigDecimal availableAmount = amount.add(contractAccount.getFloatingPL())
                .subtract(contractAccount.getMarginCallRequirement())
                .subtract(contractAccount.getFrozenAmount());
        boolean enough = availableAmount.compareTo(BigDecimal.ZERO) >= 0;
        if (enough) {
            return Result.suc(orderResult);
        }
        return Result.fail(CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(), CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
    }

    public void updateExtraEntrustAmountByContract(Long userId, Long contractId) {
        executorService.submit(() -> internalUpdateExtraEntrustAmountByContract(userId, contractId));
    }
    public void internalUpdateExtraEntrustAmountByContract(Long userId, Long contractId){
        if (marketAccountListService.contains(userId)) {
            return;
        }
        List<ContractOrderDO> contractOrderDOS = contractOrderMapper.selectNotEnforceOrderByUserIdAndContractId(userId, contractId);
        if (CollectionUtils.isEmpty(contractOrderDOS)) {
            contractOrderDOS = Collections.emptyList();
        }
        List<ContractOrderDO> bidList = contractOrderDOS.stream()
                .filter(order -> order.getOrderDirection() == OrderDirectionEnum.BID.getCode())
                .collect(toList());
        List<ContractOrderDO> askList = contractOrderDOS.stream()
                .filter(order -> order.getOrderDirection() == ASK.getCode())
                .collect(toList());

        int lever = contractLeverManager.getLeverByContractId(userId, contractId);
        UserPositionDO userPositionDO = userPositionMapper.selectByUserIdAndId(userId, contractId);
        Integer positionType = Optional.ofNullable(userPositionDO)
                .map(UserPositionDO::getPositionType)
                .orElse(PositionTypeEnum.EMPTY.getCode());
        BigDecimal unfilledAmount = Optional.ofNullable(userPositionDO)
                .map(UserPositionDO::getUnfilledAmount)
                .orElse(BigDecimal.ZERO);

        log.info("user position: {}", userPositionDO);
        EntrustMarginDO entrustMarginDO = new EntrustMarginDO();
        entrustMarginDO = getExtraEntrustAmount(userId, contractId, bidList, askList, positionType, unfilledAmount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(lever));
        Pair<BigDecimal, Map<String, Object>> pair = entrustMarginDO.getPair();
        Map<String, Object> map = pair.getRight();
        String userContractPositionExtraKey = RedisKey.getUserContractPositionExtraKey(userId);
        redisManager.hPutAll(userContractPositionExtraKey, map);
    }
}
