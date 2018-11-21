package com.fota.trade.manager;

import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.asset.service.AssetService;
import com.fota.common.Result;
import com.fota.common.enums.FotaApplicationEnum;
import com.fota.data.domain.TickerDTO;
import com.fota.risk.client.domain.UserPositionQuantileDTO;
import com.fota.risk.client.manager.RelativeRiskLevelManager;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.PriceTypeEnum;
import com.fota.trade.client.*;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.enums.*;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.msg.BaseCancelReqMessage;
import com.fota.trade.msg.BaseCanceledMessage;
import com.fota.trade.msg.ContractPlaceOrderMessage;
import com.fota.trade.msg.TopicConstants;
import com.fota.trade.service.ContractCategoryService;
import com.fota.trade.service.internal.MarketAccountListService;
import com.fota.trade.util.*;
import com.google.common.base.Joiner;
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
import java.util.stream.Collectors;

import static com.fota.asset.domain.enums.UserContractStatus.LIMIT;
import static com.fota.common.utils.CommonUtils.scale;
import static com.fota.trade.PriceTypeEnum.*;
import static com.fota.trade.client.MQConstants.ORDER_TOPIC;
import static com.fota.trade.client.MQConstants.TO_CANCEL_CONTRACT_TAG;
import static com.fota.trade.client.constants.Constants.MAX_BATCH_SIZE;
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
    private CurrentPriceService currentPriceService;

    @Autowired
    private MarketAccountListService marketAccountListService;

    @Autowired
    private RelativeRiskLevelManager relativeRiskLevelManager;

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


    /**
     * 是否强平单
     * @param placeOrderRequest
     * @param isEnforce
     * @return
     */
    public Result<List<PlaceOrderResult>> placeOrder(PlaceOrderRequest<PlaceContractOrderDTO> placeOrderRequest, boolean isEnforce){
        Profiler profiler = null == ThreadContextUtil.getPrifiler() ?
                new Profiler("ContractOrderManager.placeOrder"): ThreadContextUtil.getPrifiler();

        //检查委托参数
        if (null == placeOrderRequest || !placeOrderRequest.checkParam() || null == placeOrderRequest.getUserLevel()) {
            return Result.fail(ILLEGAL_PARAM.getCode(), ILLEGAL_PARAM.getMessage());
        }
        if (placeOrderRequest.getPlaceOrderDTOS().size() > MAX_BATCH_SIZE) {
            return Result.fail(SIZE_TOO_LARGE.getCode(), SIZE_TOO_LARGE.getMessage());
        }

        //检查合约
        List<ContractCategoryDTO> categoryList = contractCategoryService.listActiveContract();
        profiler.complelete("select contract category");

        Map<Long, ContractCategoryDTO> categoryDTOMap = (null == categoryList) ? new HashMap<>() : categoryList.stream()
                .collect(Collectors.toMap(ContractCategoryDTO::getId, x -> x));

        //校验合约状态
        for (PlaceContractOrderDTO placeContractOrderDTO : placeOrderRequest.getPlaceOrderDTOS()) {
            long contractId = placeContractOrderDTO.getSubjectId();
            Result checkContractRest = checkConctractCategory(categoryDTOMap.get(contractId), contractId);
            if (!checkContractRest.isSuccess()) {
                return Result.fail(checkContractRest.getCode(), checkContractRest.getMessage());
            }
        }


        long userId = placeOrderRequest.getUserId();
        List<PlaceOrderResult> placeOrderResults = new LinkedList<>();
        List<ContractOrderDO> contractOrderDOS = new LinkedList<>();

        //委托冻结的中间值
        Map<String, Object> entrustInternalValues = new HashMap<>();
        if (isEnforce) {
            for (PlaceContractOrderDTO placeOrderDTO : placeOrderRequest.getPlaceOrderDTOS()) {
                ContractOrderDO contractOrderDO = ConvertUtils.extractContractOrderDO(placeOrderDTO, placeOrderRequest.getUserId(),
                        placeOrderRequest.getUserLevel().getFeeRate(), placeOrderRequest.getUserName(), placeOrderRequest.getIp());
                contractOrderDOS.add(contractOrderDO);
                PlaceOrderResult placeOrderResult = new PlaceOrderResult();
                placeOrderResult.setOrderId(contractOrderDO.getId());
                placeOrderResults.add(placeOrderResult);
            }
            boolean suc = batchInsert(contractOrderDOS);
            if (!suc) {
                return Result.fail(ORDER_FAILED.getCode(), ORDER_FAILED.getMessage());
            }
        } else {
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

            List<CompetitorsPriceDTO> competitorsPrices = realTimeEntrustManager.getContractCompetitorsPriceOrder();
            profiler.complelete("getContractCompetitorsPriceOrder");

            for (PlaceContractOrderDTO placeOrderDTO : placeOrderRequest.getPlaceOrderDTOS()) {
                ContractOrderDO contractOrderDO = ConvertUtils.extractContractOrderDO(placeOrderDTO, placeOrderRequest.getUserId(),
                        placeOrderRequest.getUserLevel().getFeeRate(), placeOrderRequest.getUserName(), placeOrderRequest.getIp());
                Result checkRes = checkAndfillProperties(contractOrderDO, competitorsPrices, placeOrderDTO.getEntrustValue());
                if(!checkRes.isSuccess()) {
                    return checkRes;
                }
                contractOrderDOS.add(contractOrderDO);

                PlaceOrderResult placeOrderResult = new PlaceOrderResult();
                placeOrderResult.setOrderId(contractOrderDO.getId());
                placeOrderResult.setExtOrderId(placeOrderDTO.getExtOrderId());
                placeOrderResults.add(placeOrderResult);
            }

            Result<OrderResult> judgeRet = judgeOrderAvailable(userId, new BigDecimal(userContractDTO.getAmount()), contractOrderDOS,
                    categoryDTOMap, competitorsPrices, placeOrderRequest.getCaller());
            profiler.complelete("judge order available");

            if (!judgeRet.isSuccess()) {
                return Result.fail(judgeRet.getCode(), judgeRet.getMessage());
            }
            if (null != judgeRet.getData().getEntrustInternalValues()) {
                entrustInternalValues.putAll(judgeRet.getData().getEntrustInternalValues());
            }
            boolean suc = batchInsert(contractOrderDOS);
            if (!suc) {
                return Result.fail(ORDER_FAILED.getCode(), ORDER_FAILED.getMessage());
            }
            profiler.complelete("insert record");
        }


        Runnable runnable = () -> {

            List<ContractPlaceOrderMessage> contractPlaceOrderMessages = contractOrderDOS.stream().map(ConvertUtils::toContractPlaceOrderMessage).collect(toList());
            rocketMqManager.batchSendMessage(TopicConstants.TRD_CONTRACT_ORDER, x -> x.getSubjectId() + "", x -> x.getOrderId()+"", contractPlaceOrderMessages);
            profiler.complelete("send MQ message");

            String userContractPositionExtraKey = RedisKey.getUserContractPositionExtraKey(placeOrderRequest.getUserId());
            redisManager.hPutAll(userContractPositionExtraKey, entrustInternalValues);

            String username =  placeOrderRequest.getUserName();
            String ipAddress = placeOrderRequest.getIp();
            logPlaceOrder(contractOrderDOS, username, ipAddress);
        };
        ThreadContextUtil.setPostTask(runnable);
        return Result.suc(placeOrderResults);
    }

    /**
     * 计算价格，数量
     * @param newContractOrderDO
     * @param competitorsPrices
     * @param entrustValue
     * @return
     */
    private Result checkAndfillProperties(ContractOrderDO newContractOrderDO, List<CompetitorsPriceDTO> competitorsPrices, BigDecimal entrustValue){
        int assetId = AssetTypeEnum.getAssetIdByContractName(newContractOrderDO.getContractName());
        if (assetId == AssetTypeEnum.UNKNOW.getCode()) {
            log.error("illegal assetId, contractName={}", newContractOrderDO.getContractName());
            return Result.fail(ILLEGAL_PARAM.getCode(), "illegal contractName");
        }
        //计算合约价格
        Result<BigDecimal> getPriceRes = computeAndCheckOrderPrice(competitorsPrices, newContractOrderDO.getOrderType(), newContractOrderDO.getPrice(),
                assetId, newContractOrderDO.getContractId(), newContractOrderDO.getOrderDirection());
        if (!getPriceRes.isSuccess()) {
            return Result.fail(getPriceRes.getCode(), getPriceRes.getMessage());
        }
        //重新设置价格
        newContractOrderDO.setPrice(getPriceRes.getData());
        setActualOrderType(newContractOrderDO);

        //根据金额计算数量
        int scale = AssetTypeEnum.getContractAmountPrecisionByAssetId(assetId);
        if (null != entrustValue) {
            newContractOrderDO.setTotalAmount(entrustValue.divide(newContractOrderDO.getPrice(), scale, BigDecimal.ROUND_DOWN));
            newContractOrderDO.setUnfilledAmount(newContractOrderDO.getTotalAmount());
        }else {
            newContractOrderDO.setTotalAmount(newContractOrderDO.getTotalAmount().setScale(scale,BigDecimal.ROUND_DOWN));
            newContractOrderDO.setUnfilledAmount(newContractOrderDO.getTotalAmount());
        }
        if (newContractOrderDO.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail(AMOUNT_ILLEGAL.getCode(), "合约金额太小");
        }
        return Result.suc(null);
    }

    public Result checkOrderAndPositionSize(long userId, Map<Long, List<ContractOrderDO>> contractMap, List<UserPositionDO> userPositionDOS,
                                            FotaApplicationEnum caller){
        Map<Long, UserPositionDO> contractPositionDOMap = CollectionUtils.isEmpty(userPositionDOS) ? new HashMap<>() : userPositionDOS.stream()
                .collect(Collectors.toMap(UserPositionDO::getContractId, x -> x));

        if (null == contractMap) return Result.suc(null);

        boolean isMarketUser = FotaApplicationEnum.TRADING_API == caller;
        for (Map.Entry<Long, List<ContractOrderDO>> entry : contractMap.entrySet()) {
            //合约数量限制
            if (entry.getValue().size() > 200) {
                return Result.fail(TOO_MUCH_ORDERS.getCode(), TOO_MUCH_ORDERS.getMessage());
            }

            //做市账户不限制
            if (isMarketUser) {
                continue;
            }

            //仓位限制
            long contractId = entry.getKey();
            UserPositionDO userPositionDO = contractPositionDOMap.get(contractId);

            List<ContractOrderDO> orderList = entry.getValue();
            for (Integer direction : Arrays.asList(ASK.getCode(), BID.getCode())) {
                List<ContractOrderDO> oneDirectionOrders = orderList.stream().filter(orderDO -> direction.equals(orderDO.getOrderDirection()))
                        .collect(toList());
                if (CollectionUtils.isEmpty(oneDirectionOrders)) {
                    continue;
                }
                Integer assetId = AssetTypeEnum.getAssetIdByContractName(oneDirectionOrders.get(0).getContractName());

                BigDecimal signedPositionAmount = null == userPositionDO ? BigDecimal.ZERO : ContractUtils.computeSignAmount(userPositionDO.getUnfilledAmount(), userPositionDO.getPositionType());

                BigDecimal oneDirectionOrderSum = oneDirectionOrders.stream()
                        .map(ContractOrderDO::getUnfilledAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal ownedAbs = ContractUtils.computeSignAmount(oneDirectionOrderSum, direction)
                        .add(signedPositionAmount)
                        .abs();
                BigDecimal limit = BigDecimal.ZERO;
                if (assetId == AssetTypeEnum.EOS.getCode()) {
                    limit = POSITION_LIMIT_EOS;
                } else if (assetId == AssetTypeEnum.BTC.getCode()) {
                    limit = POSITION_LIMIT_BTC;
                } else if (assetId == AssetTypeEnum.ETH.getCode()) {
                    limit = POSITION_LIMIT_ETH;
                }
                if (ownedAbs.compareTo(limit) >= 0) {
                   return Result.fail(POSITION_EXCEEDS.getCode(), POSITION_EXCEEDS.getMessage());
                }
            }


        }
        return Result.suc(null);

    }

    public void setActualOrderType(ContractOrderDO contractOrderDO){
        if (contractOrderDO.getOrderType().equals(PriceTypeEnum.RIVAL_PRICE.getCode())) {
            contractOrderDO.setOrderType(PriceTypeEnum.SPECIFIED_PRICE.getCode());
        }
    }

    private void logPlaceOrder(List<ContractOrderDO> contractOrderDOS , String username, String ipAddress){

        for (ContractOrderDO contractOrderDO : contractOrderDOS) {
            if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {

                tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                        2, contractOrderDO.getContractName(), username, ipAddress, contractOrderDO.getTotalAmount(),
                        System.currentTimeMillis(), 3, contractOrderDO.getOrderDirection(), contractOrderDO.getUserId(), 2);
            } else {
                tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                        2, contractOrderDO.getContractName(), username, ipAddress, contractOrderDO.getTotalAmount(),
                        System.currentTimeMillis(), 2, contractOrderDO.getOrderDirection(), contractOrderDO.getUserId(), 1);
            }
        }
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
//        ContractCategoryDTO contractCategoryDO = contractCategoryService.getContractById(contractOrderDO.getContractId());
//        if (contractCategoryDO == null){
//            return ResultCode.error(BIZ_ERROR.getCode(),"contract is null, id="+contractOrderDO.getContractId());
//        }
//        if (contractCategoryDO.getStatus() == DELIVERYING.getCode()){
//            log.error("contract status illegal,can not cancel{}", contractCategoryDO);
//            return ResultCode.error(BIZ_ERROR.getCode(),"illegal status, id="+contractCategoryDO.getId() + ", status="+ contractCategoryDO.getStatus());
//        }
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
        if (CollectionUtils.isEmpty(orderIdList) || null == userId) {
            log.error("empty orderList");
            return;
        }
        //批量发送MQ消息到match
        int i = 0;
        int batchSize = 20;
        while (i < orderIdList.size()) {
            int temp =  i + batchSize;
            temp = temp < orderIdList.size() ? temp : orderIdList.size();
            List<Long> subList = orderIdList.subList(i, temp);
            BaseCancelReqMessage toCancelMessage = new BaseCancelReqMessage();
            toCancelMessage.setCancelType(CancelTypeEnum.CANCEL_BY_ORDERID);
            toCancelMessage.setUserId(userId);
            toCancelMessage.setIdList(subList);
            String msgKey = Joiner.on(",").join(subList);
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
        Map<Integer, Integer> leverMap = contractLeverManager.getLeverMapByUserId(userId);

        String userContractPositionExtraKey = RedisKey.getUserContractPositionExtraKey(userId);
        Map<String, Object> userContractPositions =  redisManager.hEntries(userContractPositionExtraKey);
        if (Objects.isNull(userContractPositions)) {
            userContractPositions = Collections.emptyMap();
        }
        BigDecimal totalPositionMarginByIndex = BigDecimal.ZERO;
        BigDecimal totalPositionValueByIndex = BigDecimal.ZERO;
        BigDecimal totalPositionValue = BigDecimal.ZERO;
        BigDecimal totalEntrustValue = BigDecimal.ZERO;
        BigDecimal totalFloatingPLByIndex = BigDecimal.ZERO;
        BigDecimal totalEntrustMarginByIndex = BigDecimal.ZERO;
        //todo 获取简单交割指数列表
        List<TickerDTO> list = new ArrayList<>();
        try{
            list = currentPriceService.getSpotIndexes();
        }catch (Exception e){
            log.error("get simpleIndexList failed", e);
        }
        Map<String, Object> map = new HashMap<>();
        List<UserPositionDTO> userPositionDTOS = new ArrayList<>(allPositions.size());
        UserPositionQuantileDTO userPositionQuantileDTO = new UserPositionQuantileDTO();
        List<UserPositionQuantileDTO.UserPositionDTO> dtoList = allPositions.stream()
                .map(userPositionDO -> {
                    UserPositionQuantileDTO.UserPositionDTO userPositionDTO = new UserPositionQuantileDTO.UserPositionDTO();
                    userPositionDTO.setContractId(userPositionDO.getContractId());
                    userPositionDTO.setPositionType(userPositionDO.getPositionType());

                    return userPositionDTO;
                }).collect(toList());
        userPositionQuantileDTO.setUserPositions(dtoList);
        userPositionQuantileDTO.setUserId(userId);
        Map<Long, Long> quantiles = relativeRiskLevelManager.quantiles(userPositionQuantileDTO);
        for (ContractCategoryDTO contractCategoryDO : categoryList) {
            long contractId = contractCategoryDO.getId();
            BigDecimal lever = contractLeverManager.findLever(leverMap, contractCategoryDO.getAssetId());
            BigDecimal positionMargin = BigDecimal.ZERO;
            BigDecimal floatingPL = BigDecimal.ZERO;
            BigDecimal entrustMargin;
            BigDecimal entrustMarginByIndex;
            BigDecimal positionUnfilledAmount= BigDecimal.ZERO;
            BigDecimal positionMarginByIndex = BigDecimal.ZERO;
            BigDecimal floatingPLByIndex = BigDecimal.ZERO;
            BigDecimal positionValueByIndex = BigDecimal.ZERO;
            BigDecimal positionValue = BigDecimal.ZERO;
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
                BigDecimal currentPrice = price.multiply(positionUnfilledAmount);

                floatingPL = price.subtract(positionAveragePrice).multiply(positionUnfilledAmount).multiply(new BigDecimal(dire));
                positionMargin = positionUnfilledAmount.multiply(price).divide(lever, scale, BigDecimal.ROUND_UP);

                floatingPLByIndex = index.compareTo(BigDecimal.ZERO) == 0 ? floatingPL :
                        index.subtract(positionAveragePrice).multiply(positionUnfilledAmount).multiply(new BigDecimal(dire));
                positionMarginByIndex = index.compareTo(BigDecimal.ZERO) == 0 ? positionMargin :
                        positionUnfilledAmount.multiply(index).divide(lever, scale, BigDecimal.ROUND_UP);
                positionValueByIndex = index.compareTo(BigDecimal.ZERO) == 0 ? positionUnfilledAmount.multiply(price) :
                        positionUnfilledAmount.multiply(index);
                positionValue = positionUnfilledAmount.multiply(price);
                UserPositionDTO userPositionDTO = com.fota.trade.common.BeanUtils.copy(userPositionDO);
                userPositionDTO.setLever(lever.intValue());
                userPositionDTO.setAveragePrice(positionAveragePrice.toPlainString());
                userPositionDTO.setMargin(positionMargin);
                userPositionDTO.setFloatingPL(floatingPL);
                userPositionDTO.setCurrentPrice(currentPrice);
                Long quantile = quantiles.get(userPositionDTO.getContractId());
                if (Objects.isNull(quantile)) {
                    quantile = Constant.DEFAULT_POSITION_QUANTILE;
                    log.error("user:{} contract:{}/{} quantile miss", userId, contractId, positionType);
                }
                userPositionDTO.setQuantile(quantile);
                userPositionDTOS.add(userPositionDTO);
            }

            //计算委托额外保证金
            String contraryKey = "", sameKey = "";
            String contraryEntrustKey = "", sameEntrustKey = "";
            if (positionType == PositionTypeEnum.OVER.getCode()) {
                contraryKey = contractId + "-" + PositionTypeEnum.EMPTY.name();
                sameKey = contractId + "-" + PositionTypeEnum.OVER.name();
                contraryEntrustKey = contractId + "-" + Constant.ENTRUST_VALUE_KEY + "-" + PositionTypeEnum.EMPTY.name();
                sameEntrustKey = contractId + "-" + Constant.ENTRUST_VALUE_KEY + "-" + PositionTypeEnum.OVER.name();
            } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
                contraryKey = contractId + "-" + PositionTypeEnum.OVER.name();
                sameKey = contractId + "-" + PositionTypeEnum.EMPTY.name();
                contraryEntrustKey = contractId + "-" + Constant.ENTRUST_VALUE_KEY + "-" + PositionTypeEnum.OVER.name();
                sameEntrustKey = contractId + "-" + Constant.ENTRUST_VALUE_KEY + "-" + PositionTypeEnum.EMPTY.name();
            }

            Object contraryValue = userContractPositions.get(contraryKey);
            Object sameValue = userContractPositions.get(sameKey);
            Object contraryEntrustValue = userContractPositions.get(contraryEntrustKey);
            Object sameEntrustValue = userContractPositions.get(sameEntrustKey);
            EntrustMarginDO entrustMarginDO;
            if (Objects.nonNull(contraryValue) && Objects.nonNull(sameValue) && Objects.nonNull(contraryEntrustValue) && Objects.nonNull(sameEntrustValue)) {
                entrustMarginDO = cal(new BigDecimal(contraryValue.toString()), new BigDecimal(sameValue.toString()), positionMargin, positionMarginByIndex,
                        new BigDecimal(contraryEntrustValue.toString()), new BigDecimal(sameEntrustValue.toString()), positionValue);
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
                entrustMarginDO = getExtraEntrustAmount(userId, contractId, bidList, askList, positionType, positionUnfilledAmount, positionMargin, positionMarginByIndex, lever, positionValue);
                Pair<BigDecimal, Map<String, Object>> pair = entrustMarginDO.getPair();
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
            totalPositionValue = totalPositionValue.add(positionValue);
            totalEntrustMarginByIndex = totalEntrustMarginByIndex.add(entrustMarginByIndex);
            totalEntrustValue = totalEntrustValue.add(entrustMarginDO.getEntrustValue());
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
        BigDecimal btcSpotIndex = getIndex(AssetTypeEnum.BTC.getDesc(), list);
        contractAccount.setAccountValuation(contractAccount.getAccountEquity().multiply(btcSpotIndex));
        contractAccount.setAccountMargin(contractAccount.getFrozenAmount().add(contractAccount.getMarginCallRequirement()));
        contractAccount.setSuggestedAddAmount(contractAccount.getAvailableAmount().negate().max(BigDecimal.ZERO));
        contractAccount.setUserPositionDTOS(userPositionDTOS);
        if (contractAccount.getAccountEquity().compareTo(BigDecimal.ZERO) <= 0){
            contractAccount.setEffectiveLever(null);
        }else {
            contractAccount.setEffectiveLever(totalPositionValue.add(totalEntrustValue).divide(contractAccount.getAccountEquity(), 3, BigDecimal.ROUND_DOWN));
        }
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
                listFee = listFee.add(sortedList.get(i).getPrice().multiply(sortedList.get(i).getUnfilledAmount()).multiply(sortedList.get(i).getFee()));
                positionUnfilledAmount = positionUnfilledAmount.subtract(sortedList.get(i).getUnfilledAmount());
                if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0 && flag.equals(0)) {
                    flag = 1;
                    BigDecimal restAmount = positionUnfilledAmount.negate().multiply(sortedList.get(i).getPrice()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                    for (int j = i + 1; j < sortedList.size(); j++) {
                        BigDecimal orderAmount = sortedList.get(j).getPrice().multiply(sortedList.get(j).getUnfilledAmount()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                        BigDecimal orderFee = orderAmount.multiply(lever).multiply(sortedList.get(j).getFee());
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
                                            BigDecimal positionEntrustAmount,BigDecimal positionMarginByIndex,
                                            BigDecimal lever, BigDecimal positionValue) {
        if (null == positionUnfilledAmount) {
            log.error("null positionUnfilledAmount");
            positionUnfilledAmount = BigDecimal.ZERO;
        }

        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal entrustAmount = BigDecimal.ZERO;
        BigDecimal entrustValue = BigDecimal.ZERO;
        BigDecimal totalContraryEntrustAmount = BigDecimal.ZERO;
        BigDecimal totalSameEntrustAmount = BigDecimal.ZERO;
        BigDecimal contraryEntrustValue = BigDecimal.ZERO;
        BigDecimal sameEntrustValue = BigDecimal.ZERO;
        String contraryKey = "", sameKey = "";
        String contraryEntrustKey = "", sameEntrustKey = "";
        List<ContractOrderDO> contrarySortedList, sameList;
        if (positionType == PositionTypeEnum.OVER.getCode()) {
            contrarySortedList = sortListAsc(askList);
            sameList = bidList;
            contraryKey = contractId + "-" + PositionTypeEnum.EMPTY.name();
            sameKey = contractId + "-" + PositionTypeEnum.OVER.name();
            contraryEntrustKey = contractId + "-" + Constant.ENTRUST_VALUE_KEY + "-" + PositionTypeEnum.EMPTY.name();
            sameEntrustKey = contractId + "-" + Constant.ENTRUST_VALUE_KEY + "-" + PositionTypeEnum.OVER.name();
        } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
            contrarySortedList = sortListDesc(bidList);
            sameList = askList;
            contraryKey = contractId + "-" + PositionTypeEnum.OVER.name();
            sameKey = contractId + "-" + PositionTypeEnum.EMPTY.name();
            contraryEntrustKey = contractId + "-" + Constant.ENTRUST_VALUE_KEY + "-" + PositionTypeEnum.OVER.name();
            sameEntrustKey = contractId + "-" + Constant.ENTRUST_VALUE_KEY + "-" + PositionTypeEnum.EMPTY.name();
        } else {
            throw new RuntimeException("positionType illegal");
        }

        int flag = 0;
        for (int i = 0; i < contrarySortedList.size(); i++) {
            fee = fee.add(contrarySortedList.get(i).getPrice()
                    .multiply(contrarySortedList.get(i).getUnfilledAmount())
                    .multiply(contrarySortedList.get(i).getFee()));
            positionUnfilledAmount = positionUnfilledAmount.subtract(contrarySortedList.get(i).getUnfilledAmount());
            if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0 && flag == 0) {
                flag = 1;
                BigDecimal restValue = positionUnfilledAmount.negate().multiply(contrarySortedList.get(i).getPrice());
                BigDecimal restAmount = restValue.divide(lever, 8, BigDecimal.ROUND_DOWN);
                for (int j = i + 1; j < contrarySortedList.size(); j++) {
                    BigDecimal orderValue = contrarySortedList.get(j).getPrice()
                            .multiply(contrarySortedList.get(j).getUnfilledAmount());
                    BigDecimal orderAmount = orderValue.divide(lever, 8, BigDecimal.ROUND_DOWN);
                    entrustAmount = entrustAmount.add(orderAmount);
                    entrustValue = entrustValue.add(orderValue);
                }
                totalContraryEntrustAmount = restAmount.add(entrustAmount);
                contraryEntrustValue = restValue.add(entrustValue);
            }
        }
        totalContraryEntrustAmount = totalContraryEntrustAmount.add(fee);

        for (ContractOrderDO contractOrderDO : sameList) {
            BigDecimal orderAmount = contractOrderDO.getPrice()
                    .multiply(contractOrderDO.getUnfilledAmount())
                    .divide(lever, 8, BigDecimal.ROUND_DOWN);
            BigDecimal orderFee = orderAmount.multiply(lever).multiply(contractOrderDO.getFee());
            totalSameEntrustAmount = totalSameEntrustAmount.add(orderAmount.add(orderFee));
            sameEntrustValue = sameEntrustValue.add(contractOrderDO.getPrice()
                    .multiply(contractOrderDO.getUnfilledAmount()));
        }

        Map<String, Object> map = new HashMap<>();
        map.put(contraryKey, totalContraryEntrustAmount.toPlainString());
        map.put(sameKey, totalSameEntrustAmount.toPlainString());
        map.put(contraryEntrustKey, contraryEntrustValue.toPlainString());
        map.put(sameEntrustKey, sameEntrustValue.toPlainString());
        EntrustMarginDO entrustMarginDO =  cal(totalContraryEntrustAmount, totalSameEntrustAmount,
                                               positionEntrustAmount, positionMarginByIndex,
                                               contraryEntrustValue, sameEntrustValue, positionValue);
        BigDecimal entrustMargin = entrustMarginDO.getEntrustMargin();
        entrustMarginDO.setPair(Pair.of(entrustMargin, map));
        return entrustMarginDO;
    }

    private EntrustMarginDO cal(BigDecimal totalContraryEntrustAmount, BigDecimal totalSameEntrustAmount,
                                BigDecimal positionEntrustAmount, BigDecimal positionMarginByIndex,
                                BigDecimal contraryEntrustValue, BigDecimal sameEntrustValue,
                                BigDecimal positionValue) {
        EntrustMarginDO entrustMarginDO = new EntrustMarginDO();
        contraryEntrustValue = contraryEntrustValue == null ? BigDecimal.ZERO : contraryEntrustValue;
        sameEntrustValue = sameEntrustValue == null ? BigDecimal.ZERO : sameEntrustValue;
        positionValue = positionValue == null ? BigDecimal.ZERO : positionValue;
        BigDecimal max1 = totalContraryEntrustAmount.subtract(positionEntrustAmount).max(BigDecimal.ZERO);
        max1 = totalSameEntrustAmount.max(max1);
        BigDecimal max2 = BigDecimal.ZERO;
        if (positionMarginByIndex.compareTo(BigDecimal.ZERO) > 0){
            max2 = totalContraryEntrustAmount.subtract(positionEntrustAmount).max(BigDecimal.ZERO);
            max2 = totalSameEntrustAmount.max(max2);
        }
        BigDecimal max3 = contraryEntrustValue.subtract(positionValue).max(BigDecimal.ZERO);
        max3 = sameEntrustValue.max(max3);

        entrustMarginDO.setEntrustMargin(max1);
        entrustMarginDO.setEntrustMarginByIndex(max2);
        entrustMarginDO.setEntrustValue(max3);
        return entrustMarginDO;
    }

    public boolean batchInsert(List<ContractOrderDO> contractOrderDOS){

        int insertContractOrderRet = contractOrderMapper.batchInsert(contractOrderDOS);
        if (insertContractOrderRet < contractOrderDOS.size()) {
            log.error("insert contractOrder failed");
            return false;
        }
        return true;
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
     * @param orderType
     * @param orderPrice
     * @param contractId
     * @param orderDeriction
     * @return
     */
    public Result<BigDecimal> computeAndCheckOrderPrice(List<CompetitorsPriceDTO> competitorsPriceList, Integer orderType, BigDecimal orderPrice, int assetId, Long contractId, int orderDeriction) {

        if (null == orderType) {
            return Result.fail(PRICE_TYPE_ILLEGAL.getCode(), PRICE_TYPE_ILLEGAL.getMessage());
        }
        if (RIVAL_PRICE.getCode() != orderType && MARKET_PRICE.getCode() != orderType && SPECIFIED_PRICE.getCode() != orderType && OrderTypeEnum.PASSIVE.getCode() != orderType){
            return Result.fail(PRICE_TYPE_ILLEGAL.getCode(), PRICE_TYPE_ILLEGAL.getMessage());
        }

        //无论如何都要获取交割指数
        BigDecimal indexes = currentPriceService.getSpotIndexByAssetId(assetId);
        Integer scale = AssetTypeEnum.getContractPricePrecisionByAssetId(assetId);
        int roundingMode = ASK.getCode() == orderDeriction ? BigDecimal.ROUND_UP : BigDecimal.ROUND_DOWN;

        BigDecimal buyMaxPrice = indexes.multiply(new BigDecimal("1.05")).setScale(scale, RoundingMode.UP);
        BigDecimal sellMinPrice = indexes.multiply(new BigDecimal("0.95")).setScale(scale, BigDecimal.ROUND_DOWN);

        //市场单
        if (orderType == MARKET_PRICE.getCode()) {
            orderPrice = orderDeriction == ASK.getCode() ? sellMinPrice : buyMaxPrice;
            if (orderPrice.compareTo(BigDecimal.ZERO) <=0) {
                return Result.fail(AMOUNT_ILLEGAL.getCode(), AMOUNT_ILLEGAL.getMessage());
            }
            return Result.suc(orderPrice);
        }

        //对手价
        if (orderType == RIVAL_PRICE.getCode()){
            Integer opDirection = ASK.getCode() + BID.getCode() - orderDeriction;
            Optional<CompetitorsPriceDTO> currentPrice = competitorsPriceList.stream().filter(competitorsPrice-> competitorsPrice.getOrderDirection() == opDirection &&

                    competitorsPrice.getId() == contractId.intValue()).findFirst();
            if (currentPrice.isPresent() && currentPrice.get().getPrice().compareTo(BigDecimal.ZERO) > 0){
                orderPrice= currentPrice.get().getPrice().setScale(scale, roundingMode);
            }else {
                log.info("contractId={}, competitorsPriceList={}", contractId, competitorsPriceList);
                return Result.fail(NO_COMPETITORS_PRICE.getCode(), NO_COMPETITORS_PRICE.getMessage());
            }

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
     * @return
     */
    public Result<OrderResult> judgeOrderAvailable(long userId, BigDecimal amount, List<ContractOrderDO> newContractOrderDOS, Map<Long, ContractCategoryDTO> categoryDTOMap,
                                                   List<CompetitorsPriceDTO> competitorsPrices, FotaApplicationEnum caller) {
        Profiler profiler = (null == ThreadContextUtil.getPrifiler()) ? new Profiler("judgeOrderAvailable") : ThreadContextUtil.getPrifiler();

        ContractAccount contractAccount = new ContractAccount();
        contractAccount.setMarginCallRequirement(BigDecimal.ZERO)
                .setFrozenAmount(BigDecimal.ZERO)
                .setFloatingPL(BigDecimal.ZERO)
                .setUserId(userId);

        //查询用户所有非强平活跃单
        List<ContractOrderDO> allContractOrders = contractOrderMapper.selectNotEnforceOrderByUserId(userId);
        profiler.complelete("selectNotEnforceOrderByUserId");
        if (null == allContractOrders) {
            allContractOrders = new ArrayList<>();
        }
        allContractOrders.addAll(newContractOrderDOS);
        Set<String> newOrderSet = newContractOrderDOS.stream().map(contractOrderDO -> contractOrderDO.getContractId() + "_"+contractOrderDO.getOrderDirection())
        .collect((Collectors.toSet()));

        //如果是单个下单记录下下单的合约ID
        Long orderContractId = null;
        if (1 == newContractOrderDOS.size()) {
            orderContractId = newContractOrderDOS.get(0).getContractId();

        }
        Map<Long, List<ContractOrderDO>> contractOrderMap = allContractOrders.stream().filter(contractOrderDO -> newOrderSet.contains(contractOrderDO.getContractId() + "_"+contractOrderDO.getOrderDirection()))
                .collect(Collectors.groupingBy(ContractOrderDO::getContractId));

        List<UserPositionDO> allPositions = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        profiler.complelete("selectPositionByUserId");

        Result checkSizeResult = checkOrderAndPositionSize(userId, contractOrderMap, allPositions, caller);
        if (!checkSizeResult.isSuccess()) {
            return checkSizeResult;
        }

        Map<Integer, Integer> assetLeverMap = contractLeverManager.getLeverMapByUserId(userId);
        profiler.complelete("listUserContractLever");
        //设置杠杆
        setLever(newContractOrderDOS, assetLeverMap);

        Map<String, Object> map = new HashMap<>();
        OrderResult orderResult = new OrderResult();
        orderResult.setEntrustInternalValues(map);
        for (ContractCategoryDTO contractCategoryDO : categoryDTOMap.values()) {
            long contractId = contractCategoryDO.getId();
            BigDecimal lever = contractLeverManager.findLever(assetLeverMap,  contractCategoryDO.getAssetId());
            BigDecimal positionMargin = BigDecimal.ZERO;
            BigDecimal floatingPL = BigDecimal.ZERO;
            BigDecimal entrustMargin = BigDecimal.ZERO;
            BigDecimal positionUnfilledAmount= BigDecimal.ZERO;
            Integer positionType = PositionTypeEnum.EMPTY.getCode();

            List<ContractOrderDO>  orderList = allContractOrders.stream()
                    .filter(contractOrder -> contractOrder.getContractId().equals(contractId))
                    .collect(toList());

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
                    return Result.fail(SYSTEM_ERROR.getCode(), SYSTEM_ERROR.getMessage());
                }
                floatingPL = price.subtract(positionAveragePrice)
                        .multiply(positionUnfilledAmount)
                        .multiply(new BigDecimal(dire));

                positionMargin = positionUnfilledAmount.multiply(price)
                        .divide(lever, scale, BigDecimal.ROUND_UP);


                //如果单个合约下单并且是下单的合约
                if (null != orderContractId &&  orderContractId.equals(contractCategoryDO.getId())) {
                    Integer orderDirection =  newContractOrderDOS.get(0).getOrderDirection();
                    List<ContractOrderDO> sameDirectionOrderList  = orderList.stream()
                            .filter(order -> order.getOrderDirection().equals(orderDirection))
                            .collect(toList());
                    //持仓反方向的"仓加挂"小于该合约持仓保证金，允许下单
                    if (positionType != orderDirection) {
                        if (!CollectionUtils.isEmpty(sameDirectionOrderList)) {
                            //计算委托额外保证金
                            Boolean judgeRet = judgeOrderResult(sameDirectionOrderList, positionType, positionUnfilledAmount, positionMargin, lever);
                            if (judgeRet) {
                                return Result.suc(orderResult);
                            }
                        }
                    }

                }
            }

            //计算委托额外保证金
            List<ContractOrderDO> bidList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.BID.getCode()).collect(toList());
            List<ContractOrderDO> askList = orderList.stream().filter(order -> order.getOrderDirection() == ASK.getCode()).collect(toList());
            Pair<BigDecimal, Map<String, Object>> pair = getExtraEntrustAmount(userId, contractId, bidList, askList, positionType, positionUnfilledAmount, positionMargin, BigDecimal.ZERO, lever, null).getPair();
            entrustMargin = pair.getLeft();
            map.putAll(pair.getRight());

            contractAccount.setMarginCallRequirement(contractAccount.getMarginCallRequirement().add(positionMargin))
                    .setFrozenAmount(contractAccount.getFrozenAmount().add(entrustMargin))
                    .setFloatingPL(contractAccount.getFloatingPL().add(floatingPL));

        }
        BigDecimal availableAmount = amount.add(contractAccount.getFloatingPL())
                .subtract(contractAccount.getMarginCallRequirement())
                .subtract(contractAccount.getFrozenAmount());
        boolean enough = availableAmount.compareTo(BigDecimal.ZERO) >= 0;
        if (enough) {
            return Result.suc(orderResult);
        }
        return Result.fail(CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(), CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
    }


    private void setLever(List<ContractOrderDO> contractOrderDOS, Map<Integer, Integer> assetLeverMap) {
        for (ContractOrderDO contractOrderDO : contractOrderDOS) {
            Integer assetId = AssetTypeEnum.getAssetIdByContractName(contractOrderDO.getContractName());
            contractOrderDO.setLever(contractLeverManager.doFindLever(assetLeverMap, assetId));
        }
    }

    public void updateExtraEntrustAmountByContract(Long userId, Long contractId) {
        executorService.submit(() -> internalUpdateExtraEntrustAmountByContract(userId, contractId));
    }
    public void internalUpdateExtraEntrustAmountByContract(Long userId, Long contractId){
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
        entrustMarginDO = getExtraEntrustAmount(userId, contractId, bidList, askList, positionType, unfilledAmount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(lever), null);
        Pair<BigDecimal, Map<String, Object>> pair = entrustMarginDO.getPair();
        Map<String, Object> map = pair.getRight();
        String userContractPositionExtraKey = RedisKey.getUserContractPositionExtraKey(userId);
        redisManager.hPutAll(userContractPositionExtraKey, map);
    }
}
