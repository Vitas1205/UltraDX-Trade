package com.fota.trade.manager;

import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.asset.service.AssetService;
import com.fota.common.Result;
import com.fota.common.utils.CommonUtils;
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
import com.fota.trade.service.ContractCategoryService;
import com.fota.trade.service.internal.MarketAccountListService;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ContractUtils;
import com.fota.trade.util.Profiler;
import com.fota.trade.util.ThreadContextUtil;
import com.google.common.base.Joiner;
import lombok.NonNull;
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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.fota.asset.domain.enums.UserContractStatus.LIMIT;
import static com.fota.trade.client.MQConstants.ORDER_TOPIC;
import static com.fota.trade.client.MQConstants.TO_CANCEL_CONTRACT_TAG;
import static com.fota.trade.common.Constant.DEFAULT_LEVER;
import static com.fota.trade.common.ResultCodeEnum.*;
import static com.fota.trade.domain.enums.ContractStatusEnum.PROCESSING;
import static com.fota.trade.domain.enums.OrderStatusEnum.*;
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
    private DeliveryPriceManager deliveryPriceManager;

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

            sendCancelMessage(orderDOList, userId);
        }

        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }


//    @Transactional(rollbackFor = Throwable.class)
    public Result<Long> placeOrder(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap) throws Exception{

        Profiler profiler = null == ThreadContextUtil.getPrifiler() ?
                new Profiler("ContractOrderManager.placeOrder"): ThreadContextUtil.getPrifiler();
        profiler.complelete("start transaction");
        ContractOrderDO contractOrderDO = com.fota.trade.common.BeanUtils.copy(contractOrderDTO);
        String username = StringUtils.isEmpty(userInfoMap.get("username")) ? "" : userInfoMap.get("username");
        String ipAddress = StringUtils.isEmpty(userInfoMap.get("ip")) ? "" : userInfoMap.get("ip");
        com.fota.common.Result<Long> result = new com.fota.common.Result<Long>();
        Map<String, Object> newMap = new HashMap<>();
        if (contractOrderDTO.getOrderContext() !=null){
            newMap = contractOrderDTO.getOrderContext();
        }
        newMap.put("username", username);
        contractOrderDTO.setOrderContext(newMap);
        profiler.complelete("before json serialization");
        contractOrderDO.setOrderContext(JSONObject.toJSONString(contractOrderDTO.getOrderContext()));
        profiler.complelete("json serialization");
        Long orderId = BasicUtils.generateId();
        long transferTime = System.currentTimeMillis();
        contractOrderDO.setGmtCreate(new Date(transferTime));
        contractOrderDO.setGmtModified(new Date(transferTime));
        contractOrderDO.setStatus(COMMIT.getCode());
        //根据用户等级获取费率

        boolean ret = marketAccountListService.contains(contractOrderDTO.getUserId());
        String userType = StringUtils.isEmpty(userInfoMap.get("userType")) ? "0" : userInfoMap.get("userType");
        BigDecimal feeRate = Constant.FEE_RATE;
        if (userType.equals(Constant.MARKET_MAKER_ACCOUNT_TAG) || ret){
            feeRate = BigDecimal.ZERO;
        }
        contractOrderDO.setFee(feeRate);
        contractOrderDO.setId(orderId);
        contractOrderDO.setUnfilledAmount(contractOrderDO.getTotalAmount());

        ContractCategoryDTO contractCategoryDO = contractCategoryService.getContractById(contractOrderDO.getContractId());
        profiler.complelete("select contract category");
        //委托冻结的中间值
        Map<String, Object> entrustInternalValues = new HashMap<>();
        if (contractCategoryDO == null) {
            log.error("Contract Is Null");
            throw new RuntimeException("Contract Is Null");
        }
        if (contractCategoryDO.getStatus() == ContractStatusEnum.ROOLING_BACK.getCode()){
            result.setCode(ResultCodeEnum.CONTRACT_IS_ROLLING_BACK.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_IS_ROLLING_BACK.getMessage());
            result.setData(orderId);
            return result;
        }else if(contractCategoryDO.getStatus() == ContractStatusEnum.DELIVERYING.getCode()){
            result.setCode(ResultCodeEnum.CONTRACT_IS_DELIVERYING.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_IS_DELIVERYING.getMessage());
            result.setData(orderId);
            return result;
        }else if(contractCategoryDO.getStatus() == ContractStatusEnum.DELIVERED.getCode()){
            result.setCode(ResultCodeEnum.CONTRACT_HAS_DELIVERIED.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_HAS_DELIVERIED.getMessage());
            result.setData(orderId);
            return result;
        }else if(contractCategoryDO.getStatus() == PROCESSING.getCode()){
        }else {
            result.setCode(ResultCodeEnum.CONTRACT_STATUS_ILLEGAL.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_STATUS_ILLEGAL.getMessage());
            result.setData(orderId);
            return result;
        }
        if (contractOrderDO.getCloseType() == null){
            contractOrderDO.setCloseType(OrderCloseTypeEnum.MANUAL.getCode());
        }
        contractOrderDO.setContractName(contractCategoryDO.getContractName());
        if (contractOrderDO.getOrderType() == null){
            contractOrderDO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        }
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            insertOrderRecord(contractOrderDO);
        } else {
            Result<OrderResult> judgeRet = judgeOrderAvailable(contractOrderDO.getUserId(), contractOrderDTO.getPriceType(), contractOrderDO);
            profiler.complelete("judge order available");

            if (!judgeRet.isSuccess()) {
                return Result.fail(judgeRet.getCode(), judgeRet.getMessage());
            }
            if (null != judgeRet.getData().getEntrustInternalValues()) {
                entrustInternalValues.putAll(judgeRet.getData().getEntrustInternalValues());
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
        result.setCode(0);
        result.setMessage("success");
        result.setData(orderId);
        return result;
    }


    private void sendPlaceOrderMessage(ContractOrderDO contractOrderDO, Integer contractType, String assetName){
        //推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setAmount(contractOrderDO.getUnfilledAmount());
        if (contractOrderDO.getPrice() != null){
            orderMessage.setPrice(contractOrderDO.getPrice());
        }
        orderMessage.setOrderDirection(contractOrderDO.getOrderDirection());
        orderMessage.setOrderType(contractOrderDO.getOrderType());
        orderMessage.setTransferTime(contractOrderDO.getGmtCreate().getTime());
        orderMessage.setOrderId(contractOrderDO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId());
        orderMessage.setSubjectName(contractOrderDO.getContractName());
        orderMessage.setContractType(contractType);
        orderMessage.setContractMatchAssetName(assetName);
        boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder",String.valueOf(contractOrderDO.getId()), orderMessage);
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
        sendCancelMessage(orderIdList, userId);
        return resultCode;
    }

    /**
     * 根据撮合发出的MQ消息撤单
     * @param orderId 委托单ID
     */
//    @Transactional(rollbackFor = Throwable.class)
    public ResultCode cancelOrderByMessage(long userId, long orderId, @NonNull BigDecimal unfilleAmount) {

        ResultCode resultCode = doCancelOrder(userId, orderId, unfilleAmount);
        return resultCode;

    }

    public ResultCode doCancelOrder(long userId, long orderId, BigDecimal unfilleAmount) {

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
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setAmount(unfilleAmount);
        orderMessage.setPrice(contractOrderDTO.getPrice());
        orderMessage.setTransferTime(transferTime);
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId());
        orderMessage.setSubjectName(contractOrderDO.getContractName());
        orderMessage.setOrderDirection(contractOrderDO.getOrderDirection());
        boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder", "contract_doCanceled_"+ orderId, orderMessage);
        if (!sendRet) {
            log.error("send canceled message failed, message={}", orderMessage);
        }
        updateExtraEntrustAmountByContract(contractOrderDO.getUserId(), contractOrderDO.getContractId());
        return ResultCode.success();
    }

    public void sendCancelMessage(List<Long> orderIdList, Long userId) {
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
            ToCancelMessage toCancelMessage = new ToCancelMessage();
            toCancelMessage.setCancelType(CancelTypeEnum.CANCEL_BY_ORDERID);
            toCancelMessage.setUserId(userId);
            toCancelMessage.setIdList(subList);
            String msgKey = "to_cancel_contract_"+Joiner.on(",").join(subList);
            rocketMqManager.sendMessage("order", "ContractCancel", msgKey , toCancelMessage);
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

            sendCancelMessage(orderIdList, userId);
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }



    public  ContractAccount computeContractAccount(long userId) {
        return computeContractAccount(userId, null);
    }
    /**
     *
     * @param userId
     * @param newContractOrderDO
     * @return
     */
    public  ContractAccount computeContractAccount(long userId, ContractOrderDO newContractOrderDO) {
        boolean isMarketUser = marketAccountListService.contains(userId);

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
            log.error("empty categoryList");
             return contractAccount.setAccountEquity(new BigDecimal(userContractDTO.getAmount()));
        }
        List<UserPositionDO> allPositions = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        List<CompetitorsPriceDTO> competitorsPrices = realTimeEntrustManager.getContractCompetitorsPriceOrder();
        List<UserContractLeverDO> contractLeverDOS = userContractLeverMapper.listUserContractLever(userId);
        List<ContractOrderDO> allContractOrders = null;

        if (isMarketUser) {
            allContractOrders = contractOrderMapper.selectNotEnforceOrderByUserId(userId);
        }
        if (null == allContractOrders) {
            allContractOrders = new ArrayList<>();
        }
        if (null != newContractOrderDO) {
            allContractOrders.add(newContractOrderDO);
        }

        Map<String, Object> userContractPositions = null;
        if (!isMarketUser) {
            String userContractPositionExtraKey = RedisKey.getUserContractPositionExtraKey(userId);
            userContractPositions = redisManager.hEntries(userContractPositionExtraKey);
        }
        if (Objects.isNull(userContractPositions)) {
            userContractPositions = Collections.emptyMap();
        }

        Map<String, Object> map = new HashMap<>();
        for (ContractCategoryDTO contractCategoryDO : categoryList) {
            long contractId = contractCategoryDO.getId();
            BigDecimal lever = findLever(contractLeverDOS, userId, contractCategoryDO.getAssetId());
            BigDecimal positionMargin = BigDecimal.ZERO;
            BigDecimal floatingPL = BigDecimal.ZERO;
            BigDecimal entrustMargin = BigDecimal.ZERO;
            BigDecimal positionUnfilledAmount= BigDecimal.ZERO;
            int positionType = PositionTypeEnum.EMPTY.getCode();

            Optional<UserPositionDO> userPositionDOOptional = allPositions.stream()
                    .filter(userPosition -> userPosition.getContractId().equals(contractCategoryDO.getId()))
                    .findFirst();

            //计算保证金，浮动盈亏
            if (userPositionDOOptional.isPresent()) {
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

                positionMargin = positionUnfilledAmount.multiply(price).divide(lever, CommonUtils.scale, BigDecimal.ROUND_UP);

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
            if (!isMarketUser && Objects.nonNull(contraryValue) && Objects.nonNull(sameValue)) {
                entrustMargin = cal(new BigDecimal(contraryValue.toString()), new BigDecimal(sameValue.toString()), positionMargin);
            } else {
                List<ContractOrderDO> orderList = null;
                if (isMarketUser) {
                    orderList = allContractOrders.stream()
                            .filter(contractOrder -> contractOrder.getContractId().equals(contractId))
                            .collect(toList());
                } else {
                    orderList = contractOrderMapper.selectNotEnforceOrderByUserIdAndContractId(userId, contractId);
                }
                if (CollectionUtils.isEmpty(orderList)) {
                    orderList = Collections.emptyList();
                }
                List<ContractOrderDO> bidList = orderList.stream()
                        .filter(order -> order.getOrderDirection() == OrderDirectionEnum.BID.getCode())
                        .collect(toList());
                List<ContractOrderDO> askList = orderList.stream()
                        .filter(order -> order.getOrderDirection() == OrderDirectionEnum.ASK.getCode())
                        .collect(toList());

                Pair<BigDecimal, Map<String, Object>> pair = getExtraEntrustAmount(userId, contractId, bidList, askList, positionType, positionUnfilledAmount, positionMargin, lever);
                entrustMargin = pair.getLeft();
                map.putAll(pair.getRight());
            }

            contractAccount.setMarginCallRequirement(contractAccount.getMarginCallRequirement().add(positionMargin))
                    .setFrozenAmount(contractAccount.getFrozenAmount().add(entrustMargin))
                    .setFloatingPL(contractAccount.getFloatingPL().add(floatingPL));

        }
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        contractAccount.setAvailableAmount(amount.add(contractAccount.getFloatingPL())
                .subtract(contractAccount.getMarginCallRequirement())
                .subtract(contractAccount.getFrozenAmount()));
        contractAccount.setAccountEquity(amount.add(contractAccount.getFloatingPL()));

        String userContractPositionExtraKey = RedisKey.getUserContractPositionExtraKey(userId);
        redisManager.hPutAll(userContractPositionExtraKey, map);
        return contractAccount;
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

        if (competitorsPriceDTOOptional.isPresent()) {
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
    public Pair<BigDecimal, Map<String, Object>> getExtraEntrustAmount(Long userId, Long contractId,
                                                                       List<ContractOrderDO> bidList, List<ContractOrderDO> askList,
                                                                       Integer positionType, BigDecimal positionUnfilledAmount,
                                                                       BigDecimal positionEntrustAmount, BigDecimal lever) {
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

        BigDecimal entrustMargin = cal(totalContraryEntrustAmount, totalSameEntrustAmount, positionEntrustAmount);
        return Pair.of(entrustMargin, map);
    }

    private BigDecimal cal(BigDecimal totalContraryEntrustAmount, BigDecimal totalSameEntrustAmount, BigDecimal positionEntrustAmount) {
        BigDecimal max = totalContraryEntrustAmount.subtract(positionEntrustAmount).max(BigDecimal.ZERO);
        max = totalSameEntrustAmount.max(max);

        return max;
    }

    public void insertOrderRecord(ContractOrderDO contractOrderDO){
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

    /**
     * 根据不同价格策略，获取下单价格
     * @param competitorsPriceList
     * @param priceType
     * @param orderPrice
     * @param contractId
     * @param orderDeriction
     * @return
     */
    public Result<BigDecimal> getOrderPrice(List<CompetitorsPriceDTO> competitorsPriceList, Integer priceType, BigDecimal orderPrice,  long assetId, Long contractId, int orderDeriction) {

        if (null == priceType) {
            return Result.fail(PRICE_TYPE_ILLEGAL.getCode(), PRICE_TYPE_ILLEGAL.getMessage());
        }
        if (priceType == PriceTypeEnum.SPECIFIED_PRICE.getCode())
            return Result.suc(orderPrice);

        if (priceType == PriceTypeEnum.RIVAL_PRICE.getCode()){

            Optional<CompetitorsPriceDTO> currentPrice = competitorsPriceList.stream().filter(competitorsPrice-> competitorsPrice.getOrderDirection() == orderDeriction &&
                    competitorsPrice.getId() == contractId.intValue()).findFirst();
            if (currentPrice.isPresent() && currentPrice.get().getPrice().compareTo(BigDecimal.ZERO) > 0){
                BigDecimal actualPrice = currentPrice.get().getPrice();
                Integer precision = AssetExtraProperties.getPrecisionByAssetId(assetId);
                if (null != precision) {
                    actualPrice = actualPrice.setScale(precision, BigDecimal.ROUND_DOWN);
                }
                return Result.suc(actualPrice);
            }
            return Result.fail(NO_COMPETITORS_PRICE.getCode(), NO_COMPETITORS_PRICE.getMessage());
        }

        return Result.fail(PRICE_TYPE_ILLEGAL.getCode(), PRICE_TYPE_ILLEGAL.getMessage());

    }

    private Result checkPrice(int priceType, int assetId, BigDecimal orderPrice, int orderDeriction){
        if (orderPrice.compareTo(BigDecimal.ZERO) <= 0){
            return Result.fail(AMOUNT_ILLEGAL.getCode(), AMOUNT_ILLEGAL.getMessage());
        }

        Integer precision = AssetExtraProperties.getPrecisionByAssetId(assetId);
        if ( null != precision && precision < orderPrice.precision()) {
            return Result.fail(AMOUNT_ILLEGAL.getCode(), AMOUNT_ILLEGAL.getMessage());
        }

        BigDecimal indexes = deliveryPriceManager.getDeliveryPrice(assetId);
        if (indexes != null && indexes.compareTo(BigDecimal.ZERO) != 0){
            BigDecimal maxPrice = indexes.multiply(new BigDecimal("1.05"));
            BigDecimal minPrice = indexes.multiply(new BigDecimal("0.95"));
            if (orderDeriction == OrderDirectionEnum.ASK.getCode() && orderPrice.compareTo(minPrice) < 0){
                return Result.fail(PRICE_OUT_OF_BOUNDARY.getCode(), PRICE_OUT_OF_BOUNDARY.getMessage());
            }
            if (orderDeriction == OrderDirectionEnum.BID.getCode() && orderPrice.compareTo(maxPrice) > 0){
                return Result.fail(PRICE_OUT_OF_BOUNDARY.getCode(), PRICE_OUT_OF_BOUNDARY.getMessage());
            }
        }
        return Result.suc(null);
    }

    /**
     * 判断新的合约委托能否下单
     * @param userId
     * @param newContractOrderDO
     * @return
     */
    public Result<OrderResult> judgeOrderAvailable(long userId, Integer priceType, ContractOrderDO newContractOrderDO) {
        ContractAccount contractAccount = new ContractAccount();
        contractAccount.setMarginCallRequirement(BigDecimal.ZERO)
                .setFrozenAmount(BigDecimal.ZERO)
                .setFloatingPL(BigDecimal.ZERO)
                .setUserId(userId);
        Long orderContractId = newContractOrderDO.getContractId();
        Integer orderDirection = newContractOrderDO.getOrderDirection();
        UserContractDTO userContractDTO = assetService.getContractAccount(userId);

        if (null == userContractDTO) {
            log.error("null userContractDTO, userId={}", userId);
            return Result.fail(NO_CONTRACT_BALANCE.getCode(), NO_CONTRACT_BALANCE.getMessage());
        }
        //用户被接管
        if (userContractDTO.getStatus() == LIMIT.getCode()) {
            return Result.fail(CONTRACT_ACCOUNT_HAS_LIMITED.getCode(), CONTRACT_ACCOUNT_HAS_LIMITED.getMessage());
        }


        List<ContractCategoryDTO> categoryList = contractCategoryService.listActiveContract();
        //校验合约有效性
        if (CollectionUtils.isEmpty(categoryList)) {
            return Result.fail( ResultCodeEnum.ILLEGAL_CONTRACT.getCode(), ILLEGAL_CONTRACT.getMessage());
        }
        ContractCategoryDTO contractCategoryDTO = categoryList.stream().filter(x -> x.getId().equals(newContractOrderDO.getContractId())).findFirst()
                .orElse(null);
        if (null == contractCategoryDTO) {
            return Result.fail( ResultCodeEnum.ILLEGAL_CONTRACT.getCode(), ILLEGAL_CONTRACT.getMessage());
        }

        List<CompetitorsPriceDTO> competitorsPrices = realTimeEntrustManager.getContractCompetitorsPriceOrder();
        //计算合约价格
        Result<BigDecimal> getPriceRes = getOrderPrice(competitorsPrices, priceType, newContractOrderDO.getPrice(),
                contractCategoryDTO.getAssetId(), newContractOrderDO.getContractId(), newContractOrderDO.getOrderDirection());
        if (!getPriceRes.isSuccess()) {
            return Result.fail(getPriceRes.getCode(), getPriceRes.getMessage());
        }
        //校验价格
        Result checkPriceRes = checkPrice(priceType, contractCategoryDTO.getAssetId(), getPriceRes.getData(), orderDirection);
        if (!checkPriceRes.isSuccess()) {
            return Result.fail(checkPriceRes.getCode(), checkPriceRes.getMessage());
        }
        //重新设置价格
        newContractOrderDO.setPrice(getPriceRes.getData());

        //查询用户所有非强平活跃单
        List<ContractOrderDO> allContractOrders = contractOrderMapper.selectNotEnforceOrderByUserId(userId);
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
        List<UserContractLeverDO> contractLeverDOS = userContractLeverMapper.listUserContractLever(userId);

        Map<String, Object> map = new HashMap<>();
        OrderResult orderResult = new OrderResult();
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
                        .divide(lever, CommonUtils.scale, BigDecimal.ROUND_UP);

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

            //该用户是否达到持仓上限
            if (isCurrentContract) {
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
            List<ContractOrderDO> askList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.ASK.getCode()).collect(toList());
            Pair<BigDecimal, Map<String, Object>> pair = getExtraEntrustAmount(userId, contractId, bidList, askList, positionType, positionUnfilledAmount, positionMargin, lever);
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
                .filter(order -> order.getOrderDirection() == OrderDirectionEnum.ASK.getCode())
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
        Pair<BigDecimal, Map<String, Object>> pair = getExtraEntrustAmount(userId, contractId, bidList, askList, positionType, unfilledAmount, BigDecimal.ZERO,
                BigDecimal.valueOf(lever));
        Map<String, Object> map = pair.getRight();
        String userContractPositionExtraKey = RedisKey.getUserContractPositionExtraKey(userId);
        redisManager.hPutAll(userContractPositionExtraKey, map);
    }
}
