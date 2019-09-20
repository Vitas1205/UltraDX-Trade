package com.fota.trade.manager;

import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.CapitalAccountAddAmountDTO;
import com.fota.asset.domain.UserCapitalDTO;
import com.fota.asset.domain.enums.AssetOperationTypeEnum;
import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.asset.service.AssetService;
import com.fota.common.Result;
import com.fota.common.domain.config.BrokerTradingPairConfig;
import com.fota.common.domain.enums.TradingPriceRateTypeEnum;
import com.fota.common.enums.BusinessTypeEnum;
import com.fota.common.manager.BrokerAssetManager;
import com.fota.common.manager.BrokerTradingPairManager;
import com.fota.common.manager.FotaAssetManager;
import com.fota.common.utils.LogUtil;
import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.PriceTypeEnum;
import com.fota.trade.client.CancelTypeEnum;
import com.fota.trade.client.PlaceCoinOrderDTO;
import com.fota.trade.client.PlaceOrderRequest;
import com.fota.trade.client.PlaceOrderResult;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderTypeEnum;
import com.fota.trade.mapper.sharding.UsdkMatchedOrderMapper;
import com.fota.trade.mapper.sharding.UsdkOrderMapper;
import com.fota.trade.msg.*;
import com.fota.trade.service.internal.AssetWriteService;
import com.fota.trade.service.internal.MarketAccountListService;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.MonitorLogManager;
import com.fota.trade.util.Profiler;
import com.fota.trade.util.ThreadContextUtil;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.xml.stream.events.StartDocument;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

import static com.fota.trade.PriceTypeEnum.MARKET_PRICE;
import static com.fota.trade.PriceTypeEnum.SPECIFIED_PRICE;
import static com.fota.trade.common.ResultCodeEnum.*;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;
import static com.fota.trade.domain.enums.OrderStatusEnum.COMMIT;
import static com.fota.trade.domain.enums.OrderStatusEnum.PART_MATCH;
import static com.fota.trade.domain.enums.OrderTypeEnum.*;
import static com.fota.trade.msg.TopicConstants.*;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.*;


/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/7
 * @Modified:
 */

@Component
@Slf4j
public class UsdkOrderManager {

    private static final Logger tradeLog = LoggerFactory.getLogger("trade");
    private static BigDecimal usdkFee = BigDecimal.valueOf(0);
    @Autowired
    private UsdkOrderMapper usdkOrderMapper;
    @Autowired
    private RocketMqManager rocketMqManager;
    @Autowired
    private AssetWriteService assetWriteService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private UsdkMatchedOrderMapper usdkMatchedOrder;
    @Autowired
    private RealTimeEntrustManager realTimeEntrustManager;
    @Autowired
    private MonitorLogManager monitorLogManager;
    @Autowired
    private BrokerUsdkOrderFeeListManager brokerUsdkOrderFeeListManager;
    @Autowired
    private RedisManager redisManager;
    @Autowired
    private MarketAccountListService marketAccountListService;

    @Autowired
    private FotaAssetManager fotaAssetManager;

    @Autowired
    private BrokerAssetManager brokerAssetManager;

    @Autowired
    private RealTimeEntrust realTimeEntrust;

    @Autowired
    private BrokerTradingPairManager brokerTradingPairManager;

    private static BigDecimal defaultFee = new BigDecimal("0.001");

    //TODO 优化: 先更新账户，再insert订单，而不是先insert订单再更新账户
    @Transactional(rollbackFor={Throwable.class})
    public com.fota.common.Result<Long> placeOrder(UsdkOrderDTO usdkOrderDTO, Map<String, String> userInfoMap)throws Exception {
        Profiler profiler = new Profiler("UsdkOrderManager.placeOrder");
        ThreadContextUtil.setProfiler(profiler);
        String username = StringUtils.isEmpty(userInfoMap.get("username")) ? "" : userInfoMap.get("username");
        String ipAddress = StringUtils.isEmpty(userInfoMap.get("ipAddress")) ? "" : userInfoMap.get("ipAddress");
        com.fota.common.Result<Long> result = new com.fota.common.Result<Long>();
        Long orderId = BasicUtils.generateId();
        usdkOrderDTO.setId(orderId);

        Map<String, Object> criteriaMap = new HashMap<>();
        criteriaMap.put("userId", usdkOrderDTO.getUserId());
        criteriaMap.put("assetId", usdkOrderDTO.getAssetId());
        criteriaMap.put("orderStatus", Arrays.asList(COMMIT.getCode(), PART_MATCH.getCode()));
        int count = usdkOrderMapper.countByQuery(criteriaMap);
        profiler.complelete("count 8,9 orders");
        Long userId = usdkOrderDTO.getUserId();
        boolean isMarket = marketAccountListService.contains(userId);
        if ((isMarket && count >= 5000) || (!isMarket && count >= 100)) {
            log.warn("user: {} too much {} orders", usdkOrderDTO.getUserId(), usdkOrderDTO.getAssetId());
            return Result.fail(TOO_MUCH_ORDERS.getCode(), TOO_MUCH_ORDERS.getMessage());
        }
        UsdkOrderDO usdkOrderDO = com.fota.trade.common.BeanUtils.copy(usdkOrderDTO);
        Map<String, Object> newMap = new HashMap<>();
        if (usdkOrderDTO.getOrderContext() !=null){
            newMap = usdkOrderDTO.getOrderContext();
        }
        newMap.put("username", username);
        usdkOrderDTO.setOrderContext(newMap);
        usdkOrderDO.setOrderContext(JSONObject.toJSONString(usdkOrderDTO.getOrderContext()));
        Integer assetId = usdkOrderDO.getAssetId();
        BrokerTradingPairConfig tradingPairConfig = brokerTradingPairManager.getTradingPairById(assetId.longValue());

        Integer orderDirection = usdkOrderDO.getOrderDirection();
        List<UserCapitalDTO> list = assetService.getUserCapital(userId);
        profiler.complelete("getUserCapital");
        BigDecimal feeRate = usdkOrderDO.getFee();
        if (isMarket) {
            feeRate = BigDecimal.ZERO;
        } else {
            //feeRate = getFeeRateByBrokerId(usdkOrderDTO.getBrokerId(), assetId);
            if(null == feeRate)
            {
                feeRate = defaultFee;
            }
        }
        usdkOrderDO.setFee(feeRate);
        usdkOrderDO.setStatus(COMMIT.getCode());
        usdkOrderDO.setUnfilledAmount(usdkOrderDO.getTotalAmount());
        long transferTime = System.currentTimeMillis();
        usdkOrderDO.setGmtCreate(new Date(transferTime));
        usdkOrderDO.setGmtModified(new Date(transferTime));
        if(usdkOrderDO.getOrderType() == null){
            usdkOrderDO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        }
        if (usdkOrderDO.getOrderType() != OrderTypeEnum.ENFORCE.getCode()){
            BigDecimal totalAmount = usdkOrderDO.getTotalAmount();
            if (!marketAccountListService.contains(userId)) {
                Result checkValidAmountResult = checkOrderAmount(userId, usdkOrderDO.getBrokerId(), usdkOrderDO.getAssetId(), totalAmount, usdkOrderDO.getOrderDirection());
                if (!checkValidAmountResult.isSuccess()) {
                    return checkValidAmountResult;
                }
            }
            Result<BigDecimal> checkPriceRes = computeAndCheckOrderPriceWrapped(usdkOrderDO.getPrice(), usdkOrderDO.getOrderType(), usdkOrderDO.getOrderDirection(), usdkOrderDO.getAssetId(), usdkOrderDO.getBrokerId(), isMarket);
            profiler.complelete("computeAndCheckOrderPrice");
            if (usdkOrderDO.getOrderType() == RIVAL.getCode()) {
                usdkOrderDO.setOrderType(LIMIT.getCode());
            }
            if (!checkPriceRes.isSuccess()) {
                return Result.fail(checkPriceRes.getCode(), checkPriceRes.getMessage());
            }
            usdkOrderDO.setPrice(checkPriceRes.getData());
            BigDecimal price = usdkOrderDO.getPrice();
            BigDecimal orderValue = totalAmount.multiply(price);
            //插入委托订单记录
            int ret = batchInsertUsdkOrder(Arrays.asList(usdkOrderDO));
            profiler.complelete("insertUsdkOrder");
            if (ret <= 0){
                LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), String.valueOf(usdkOrderDO.getId()), usdkOrderDO,  "insert Order failed");
                throw new RuntimeException("insert order failed");
            }
            orderId = usdkOrderDO.getId();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
            int assetTypeId;
            BigDecimal entrustValue;
            int errorCode;
            String errorMsg;

            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                assetTypeId = tradingPairConfig.getQuoteId();
                entrustValue = orderValue;
                errorCode = ResultCodeEnum.CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode();
                errorMsg = ResultCodeEnum.CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage();
            }else {
                assetTypeId = tradingPairConfig.getBaseId();
                entrustValue = usdkOrderDO.getTotalAmount();
                errorCode = ResultCodeEnum.COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode();
                errorMsg = ResultCodeEnum.COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage();
            }
            //查询账户可用余额
            boolean capitalEnough = false;
            for(UserCapitalDTO userCapitalDTO : list) {
                if (userCapitalDTO.getAssetId() == assetTypeId){
                    capitalEnough = true;
                    BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                    BigDecimal lockedAmount = new BigDecimal(userCapitalDTO.getLockedAmount());
                    BigDecimal availableAmount = amount.subtract(lockedAmount);
                    //判断账户可用余额是否大于orderValue
                    Map<String, Object> param = new HashMap<>();
                    if (availableAmount.compareTo(entrustValue) >= 0){
                        Result<Boolean> updateLockedAmountRet;
                        try{
                            CapitalAccountAddAmountDTO capitalAccountAddAmountDTO = new CapitalAccountAddAmountDTO();
                            capitalAccountAddAmountDTO.setAddOrderLocked(entrustValue);
                            capitalAccountAddAmountDTO.setUserId(userId);
                            capitalAccountAddAmountDTO.setAssetId(userCapitalDTO.getAssetId());
                            updateLockedAmountRet = assetWriteService.addCapitalAmount(capitalAccountAddAmountDTO, orderId.toString(), AssetOperationTypeEnum.USDT_EXCHANGE_PLACE_ORDER.getCode());
                            profiler.complelete("addCapitalAmount");
                        }catch (Exception e){
                            param.put("userId", userId);
                            param.put("assetId", userCapitalDTO.getAssetId());
                            param.put("entrustValue", entrustValue);
                            LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), orderId.toString(), param, "Asset RPC Error!, placeOrder assetWriteService.addCapitalAmount exception", e);
                            throw new RuntimeException("placeOrder assetWriteService.addCapitalAmount exception", e);
                        }
                        if (!updateLockedAmountRet.isSuccess() || !updateLockedAmountRet.getData()){
                            param.put("userId", userId);
                            param.put("assetId", userCapitalDTO.getAssetId());
                            param.put("entrustValue", entrustValue);
                            param.put("totalAmount", amount);
                            param.put("availableAmount", availableAmount);
                            log.warn( "bizType:{},\037traceId:{},\037param:{},\037detailMsg:{}\037", TradeBizTypeEnum.COIN_ORDER.toString(), orderId.toString(), param, "errorCode:"+ updateLockedAmountRet.getCode() + ", errorMsg:"+ updateLockedAmountRet.getMessage());
                            throw new BusinessException(errorCode, errorMsg);
                        }
                    }else {
                        param.put("userId", userId);
                        param.put("assetId", userCapitalDTO.getAssetId());
                        param.put("entrustValue", entrustValue);
                        param.put("totalAmount", amount);
                        param.put("availableAmount", availableAmount);
                        log.warn("bizType:{},\037traceId:{},\037param:{},\037detailMsg:{}\037", TradeBizTypeEnum.COIN_ORDER.toString(), orderId.toString(), param, errorMsg);
                        throw new BusinessException(errorCode, errorMsg);
                    }
                }
            }
            if (!capitalEnough) {
                throw new BusinessException(errorCode, errorMsg);
            }
        } else if (usdkOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()){
            //强平单处理
            int ret = insertUsdkOrder(usdkOrderDO);
            if (ret <= 0){
                LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), orderId.toString(), usdkOrderDO, "insert order failed");
                throw new RuntimeException("insert order failed");
            }
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
        }
        monitorLogManager.placeCoinOrderInfo(usdkOrderDO, username);
        usdkOrderDTO.setCompleteAmount(BigDecimal.ZERO);
        //消息一定要在事务外发，不然会出现收到下单消息，db还没有这个订单
       Runnable postTask = () -> {
           CoinPlaceOrderMessage placeOrderMessage = toCoinPlaceOrderMessage(usdkOrderDO);
           Boolean sendRet = rocketMqManager.sendMessage(TopicConstants.TRD_COIN_ORDER, placeOrderMessage.getSubjectId()+"", placeOrderMessage.getUserId() + "_" + placeOrderMessage.getOrderId(), placeOrderMessage);
           if (!sendRet){
               LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), String.valueOf(usdkOrderDO.getId()), placeOrderMessage, "Send RocketMQ Message Failed, placeOrderMessage={}");
           }
       };
       ThreadContextUtil.setPostTask(postTask);
        result.setCode(0);
        result.setMessage("success");
        result.setData(orderId);
        return result;
    }

    @Transactional(rollbackFor={Throwable.class})
    public Result<List<PlaceOrderResult>> batchOrder(PlaceOrderRequest<PlaceCoinOrderDTO> placeOrderRequest) throws Exception{
        Long batchOrderId = BasicUtils.generateId();
        if (!placeOrderRequest.checkParam()){
            LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), batchOrderId.toString(), placeOrderRequest, "checkParam failed, placOrderRequest");
            return Result.fail(ILLEGAL_PARAM.getCode(), ILLEGAL_PARAM.getMessage());
        }
        Profiler profiler = new Profiler("UsdkOrderManager.batchOrder");

        Result<List<PlaceOrderResult>> result = new Result<>();
        List<PlaceOrderResult> respList = new ArrayList<>();
        List<PlaceCoinOrderDTO> reqList = placeOrderRequest.getPlaceOrderDTOS();
        List<UsdkOrderDO> usdkOrderDOList = new ArrayList<>();
        if (CollectionUtils.isEmpty(reqList) || reqList.size() > Constant.BATCH_ORDER_MAX_SIZE){
            log.warn("out of max order size");
            LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), batchOrderId.toString(), reqList, "out of max order size");
            return result.error(ResultCodeEnum.BATCH_SIZE_OUT_OF_LIMIT.getCode(),ResultCodeEnum.BATCH_SIZE_OUT_OF_LIMIT.getMessage());
        }
        Map<Integer, BigDecimal> map = new HashMap<>();
        Long userId = placeOrderRequest.getUserId();
        String username = placeOrderRequest.getUserName();
        String ipAddress = placeOrderRequest.getIp();

        List<UserCapitalDTO> userCapitalDTOList = assetService.getUserCapital(userId);
        if (CollectionUtils.isEmpty(userCapitalDTOList)) {
            return Result.fail(COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(), COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
        }

        for(PlaceCoinOrderDTO placeCoinOrderDTO : reqList){
            Integer assetId = Integer.valueOf(String.valueOf(placeCoinOrderDTO.getSubjectId()));
            Long orderId = BasicUtils.generateId();
            PlaceOrderResult placeOrderResult = new PlaceOrderResult();
            placeOrderResult.setExtOrderId(placeCoinOrderDTO.getExtOrderId());
            placeOrderResult.setOrderId(orderId);
            respList.add(placeOrderResult);
            long transferTime = System.currentTimeMillis();
            Map<String, Object> newMap = new HashMap<>();
            newMap.put("username", username);

            UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
            usdkOrderDTO.setId(orderId);
            usdkOrderDTO.setUserId(userId);
            usdkOrderDTO.setAssetId(assetId);
            usdkOrderDTO.setAssetName(placeCoinOrderDTO.getSubjectName());
            usdkOrderDTO.setOrderDirection(placeCoinOrderDTO.getOrderDirection());
            usdkOrderDTO.setOrderType(placeCoinOrderDTO.getOrderType());
            BigDecimal feeRate;
            boolean isMarket = marketAccountListService.contains(userId);
            if (isMarket) {
                feeRate = BigDecimal.ZERO;
            } else {
                feeRate = getFeeRateByBrokerId(placeCoinOrderDTO.getBrokerId(), assetId);
            }
            usdkOrderDTO.setFee(feeRate);
            usdkOrderDTO.setBrokerId(placeCoinOrderDTO.getBrokerId());
            usdkOrderDTO.setStatus(COMMIT.getCode());
            usdkOrderDTO.setTotalAmount(placeCoinOrderDTO.getTotalAmount());
            usdkOrderDTO.setUnfilledAmount(placeCoinOrderDTO.getTotalAmount());
            usdkOrderDTO.setPrice(placeCoinOrderDTO.getPrice());
            usdkOrderDTO.setGmtCreate(new Date(transferTime));
            usdkOrderDTO.setGmtModified(new Date(transferTime));
            usdkOrderDTO.setOrderContext(newMap);
            if(usdkOrderDTO.getOrderType() == null){
                usdkOrderDTO.setOrderType(OrderTypeEnum.LIMIT.getCode());
            }
            UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
            BeanUtils.copyProperties(usdkOrderDTO, usdkOrderDO);
            usdkOrderDO.setOrderContext(JSONObject.toJSONString(usdkOrderDTO.getOrderContext()));
            usdkOrderDOList.add(usdkOrderDO);
            if (usdkOrderDTO.getOrderType() != OrderTypeEnum.ENFORCE.getCode()){
                BigDecimal totalAmount = usdkOrderDTO.getTotalAmount();
                if (!marketAccountListService.contains(userId)) {
                    Result checkValidAmountResult = checkOrderAmount(userId, usdkOrderDO.getBrokerId(), usdkOrderDO.getAssetId(), totalAmount, usdkOrderDO.getOrderDirection());
                    if (!checkValidAmountResult.isSuccess()) {
                        return checkValidAmountResult;
                    }
                }
                Result<BigDecimal> checkPriceRes = computeAndCheckOrderPriceWrapped(usdkOrderDTO.getPrice(), usdkOrderDTO.getOrderType(), usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getAssetId(), usdkOrderDTO.getBrokerId() ,isMarket);
                profiler.complelete("computeAndCheckOrderPrice");
                if (usdkOrderDTO.getOrderType() == RIVAL.getCode()) {
                    usdkOrderDTO.setOrderType(LIMIT.getCode());
                }
                if (!checkPriceRes.isSuccess()) {
                    return Result.fail(checkPriceRes.getCode(), checkPriceRes.getMessage());
                }
                usdkOrderDTO.setPrice(checkPriceRes.getData());
                BigDecimal price = usdkOrderDTO.getPrice();
                BigDecimal orderValue = totalAmount.multiply(price);
                int assetTypeId = 0;
                BigDecimal entrustValue;
                if (usdkOrderDTO.getOrderDirection() == OrderDirectionEnum.BID.getCode()){
                    assetTypeId = AssetTypeEnum.BTC.getCode();
                    entrustValue = orderValue;
                }else {
                    assetTypeId = usdkOrderDTO.getAssetId();
                    entrustValue = usdkOrderDTO.getTotalAmount();
                }
                if (map.get(assetTypeId) == null){
                    map.put(assetTypeId, BigDecimal.ZERO);
                }
                map.put(assetTypeId , map.get(assetTypeId).add(entrustValue));
            }
            monitorLogManager.placeCoinOrderInfo(usdkOrderDO, username);
        }

        try {
            //todo assetId修改
            Map<Integer, UserCapitalDTO> userCapitalDTOMap = userCapitalDTOList.stream()
                    .collect(toMap(UserCapitalDTO::getAssetId, Function.identity()));
            //UserCapitalDTO btcCapital = userCapitalDTOMap.get(AssetTypeEnum.BTC.getCode());

            Map<Integer, List<UsdkOrderDO>> mapByOrderDirection = usdkOrderDOList.stream()
                    .collect(groupingBy(UsdkOrderDO::getOrderDirection));
            for (Map.Entry<Integer, List<UsdkOrderDO>> entry : mapByOrderDirection.entrySet()) {
                Integer orderDirection = entry.getKey();
                Map<Integer, List<UsdkOrderDO>> mapByAssetId = entry.getValue()
                        .stream()
                        .collect(groupingBy(UsdkOrderDO::getAssetId));
                if (orderDirection == BID.getCode()) {
                    for (Map.Entry<Integer, List<UsdkOrderDO>> askEntry : mapByAssetId.entrySet()) {
                        Integer assetId = askEntry.getKey();
                        BrokerTradingPairConfig tradingPairConfig = brokerTradingPairManager.getTradingPairById(assetId.longValue());
                        UserCapitalDTO userCapitalDTO = userCapitalDTOMap.get(tradingPairConfig.getQuoteId());
                        if (Objects.isNull(userCapitalDTO) || Objects.isNull(userCapitalDTO.getAmount())) {
                            return Result.fail(COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(),
                                    COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
                        } else {
                            BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                            BigDecimal lockedAmount = StringUtils.isEmpty(userCapitalDTO.getLockedAmount()) ? BigDecimal.ZERO : new BigDecimal(userCapitalDTO.getLockedAmount());
                            BigDecimal availableAmount = amount.subtract(lockedAmount);
                            BigDecimal totalPrice = entry.getValue().stream()
                                    .map(usdkOrderDO -> usdkOrderDO.getTotalAmount().multiply(usdkOrderDO.getPrice()))
                                    .reduce(BigDecimal::add)
                                    .orElse(BigDecimal.ZERO);
                            if (totalPrice.compareTo(availableAmount) > 0) {
                                return Result.fail(COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(),
                                        COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
                            }
                        }
                    }
                } else if (orderDirection == ASK.getCode()) {
                    for (Map.Entry<Integer, List<UsdkOrderDO>> askEntry : mapByAssetId.entrySet()) {
                        Integer assetId = askEntry.getKey();
                        BrokerTradingPairConfig tradingPairConfig = brokerTradingPairManager.getTradingPairById(assetId.longValue());
                        UserCapitalDTO userCapitalDTO = userCapitalDTOMap.get(tradingPairConfig.getBaseId());
                        if (Objects.isNull(userCapitalDTO) || Objects.isNull(userCapitalDTO.getAmount())) {
                            return Result.fail(COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(),
                                    COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
                        } else {
                            BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                            BigDecimal lockedAmount = StringUtils.isEmpty(userCapitalDTO.getLockedAmount()) ? BigDecimal.ZERO : new BigDecimal(userCapitalDTO.getLockedAmount());
                            BigDecimal availableAmount = amount.subtract(lockedAmount);
                            BigDecimal askedTotalAmount = askEntry.getValue()
                                    .stream()
                                    .map(UsdkOrderDO::getTotalAmount)
                                    .reduce(BigDecimal::add)
                                    .orElse(BigDecimal.ZERO);
                            if (askedTotalAmount.compareTo(availableAmount) > 0) {
                                return Result.fail(COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(),
                                        COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
                            }
                        }
                    }
                }
            }

            Map<Long, List<PlaceCoinOrderDTO>> reqListMap = reqList.stream()
                    .collect(groupingBy(PlaceCoinOrderDTO::getSubjectId));
            for (Map.Entry<Long, List<PlaceCoinOrderDTO>> entry : reqListMap.entrySet()) {
                Map<String, Object> criteriaMap = new HashMap<>();
                criteriaMap.put("userId", userId);
                criteriaMap.put("assetId", entry.getKey());
                criteriaMap.put("orderStatus", Arrays.asList(COMMIT.getCode(), PART_MATCH.getCode()));
                int count = usdkOrderMapper.countByQuery(criteriaMap);
                profiler.complelete("count 8,9 orders");
                if (entry.getValue().size() + count > 200) {
                    log.warn("user: {} too much {} orders", userId, entry.getKey());
                    return Result.fail(TOO_MUCH_ORDERS.getCode(), TOO_MUCH_ORDERS.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("error when check batch usdt order", e);
        }

        //插入委托订单记录
        int ret = batchInsertUsdkOrder(usdkOrderDOList);
        profiler.complelete("insertUsdkOrder");
        if (ret < usdkOrderDOList.size()){
            LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), batchOrderId.toString(), usdkOrderDOList, "batchInsertUsdkOrder failed");
            throw new RuntimeException("batchInsertUsdkOrder failed");
        }

        //非强平单委托冻结
        if (map.size() > 0){
            List<CapitalAccountAddAmountDTO> capitalAccountAddAmountDTOS = new ArrayList<>();
            for(Integer key : map.keySet()){
                CapitalAccountAddAmountDTO capitalAccountAddAmountDTO = new CapitalAccountAddAmountDTO();
                capitalAccountAddAmountDTO.setAssetId(key);
                capitalAccountAddAmountDTO.setAddOrderLocked(map.get(key));
                capitalAccountAddAmountDTO.setUserId(userId);
                capitalAccountAddAmountDTOS.add(capitalAccountAddAmountDTO);
            }
            Result<Boolean> updateLockedAmountRet;
            try {
                updateLockedAmountRet = assetWriteService.batchAddCapitalAmount(capitalAccountAddAmountDTOS, batchOrderId.toString(), AssetOperationTypeEnum.USDT_EXCHANGE_BATCH_PLACE_ORDER.getCode());
            }catch (Exception e){
                LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), batchOrderId.toString(), capitalAccountAddAmountDTOS, "assetWriteService.batchAddCapitalAmount exception", e);
                throw new RuntimeException("assetWriteService.batchAddCapitalAmount exception", e);
            }
            if (updateLockedAmountRet == null ||
                    !updateLockedAmountRet.isSuccess() ||
                    updateLockedAmountRet.getData() == null ||
                    !updateLockedAmountRet.getData()){
                String detailMsg = updateLockedAmountRet == null ? "updateLockedAmountRet is null" : updateLockedAmountRet.getMessage();
                LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), batchOrderId.toString(), capitalAccountAddAmountDTOS, detailMsg);
                throw new Exception("assetWriteService.batchAddCapitalAmount failed");
            }
        }
        //批量发送mq消息一定要在事务外发，不然会出现收到下单消息，db还没有这个订单
        Runnable postTask = () -> {
            List<CoinPlaceOrderMessage> placeOrderMessages = new ArrayList<>();
            for (UsdkOrderDO usdkOrderDO : usdkOrderDOList){
                placeOrderMessages.add(toCoinPlaceOrderMessage(usdkOrderDO));
            }
            Boolean sendRet = rocketMqManager.batchSendMessage(TopicConstants.TRD_COIN_ORDER, x -> x.getSubjectId() + "", x -> x.getUserId() + "_" + x.getOrderId(), placeOrderMessages);
            if (!sendRet){
                LogUtil.error( TradeBizTypeEnum.COIN_ORDER.toString(), batchOrderId.toString(), placeOrderMessages, "batchSendMessage Failed");
            }
        };
        ThreadContextUtil.setPostTask(postTask);
        result.success(respList);
        return result;
    }


    public CoinPlaceOrderMessage toCoinPlaceOrderMessage(UsdkOrderDO usdkOrderDO){
        CoinPlaceOrderMessage placeOrderMessage = new CoinPlaceOrderMessage();
        placeOrderMessage.setOrderId(usdkOrderDO.getId());
        placeOrderMessage.setUserId(usdkOrderDO.getUserId());
        placeOrderMessage.setSubjectId(usdkOrderDO.getAssetId().longValue());
        placeOrderMessage.setSubjectName(usdkOrderDO.getAssetName());
        placeOrderMessage.setTotalAmount(usdkOrderDO.getTotalAmount());
        placeOrderMessage.setOrderDirection(usdkOrderDO.getOrderDirection());
        placeOrderMessage.setOrderType(usdkOrderDO.getOrderType());
        placeOrderMessage.setPrice(usdkOrderDO.getPrice());
        placeOrderMessage.setBrokerId(usdkOrderDO.getBrokerId());
        return placeOrderMessage;
    }

    private Result<BigDecimal> computeAndCheckOrderPriceWrapped(BigDecimal orderPrice, int orderType, int orderDirection, int assetId, Long brokerId, boolean isMarket) {
        Result<BigDecimal> result = this.computeAndCheckOrderPrice(orderPrice, orderType, orderDirection, assetId, brokerId);
        if (result.isSuccess()) {
            Result<Long> result1 = this.checkSpotOrderPriceLimit(brokerId, assetId, result.getData(), orderDirection, isMarket);
            if (!result1.isSuccess()) {
                return Result.fail(result1.getCode(), result1.getMessage());
            }
        }
        return result;
    }

    private Result checkOrderAmount(Long userId, Long brokerId, int tradingPairId, BigDecimal amount, int orderDirection) {
        Result result = Result.create();
        BrokerTradingPairConfig tradingPairConfig = brokerTradingPairManager.getTradingPairById((long) tradingPairId);
        if (Objects.nonNull(tradingPairConfig.getMinTradingAmount())) {
            if (amount.compareTo(tradingPairConfig.getMinTradingAmount()) < 0) {
                return Result.fail(LESS_THAN_MIN_AMOUNT.getCode(), tradingPairConfig.getMinTradingAmount().stripTrailingZeros().toPlainString());
            }
        }
        if (Objects.nonNull(tradingPairConfig.getMaxTradingAmount())) {
            if (amount.compareTo(tradingPairConfig.getMaxTradingAmount()) > 0) {
                return Result.fail(MORE_THAN_MAX_AMOUNT.getCode(), tradingPairConfig.getMaxTradingAmount().stripTrailingZeros().toPlainString());
            }
        }
//        if (orderDirection == ASK.getCode()) {
//            if (Objects.nonNull(tradingPairConfig.getMaxDailyShortTradingAmount())) {
//                Double value = redisManager.incr(RedisKey.getUsdkDailyMaxShortAmountKey(userId), amount.doubleValue());
//                if (Objects.isNull(value) || value > tradingPairConfig.getMaxDailyShortTradingAmount().doubleValue()) {
//                    return Result.fail(MORE_THAN_MAX_DAILY_ASK_AMOUNT.getCode(), MORE_THAN_MAX_DAILY_ASK_AMOUNT.getMessage());
//                }
//            }
//        } else if (orderDirection == BID.getCode()) {
//            if (Objects.nonNull(tradingPairConfig.getMaxDailyLongTradingAmount())) {
//                Double value = redisManager.incr(RedisKey.getUsdkDailyMaxLongAmountKey(userId), amount.doubleValue());
//                if (Objects.isNull(value) || value > tradingPairConfig.getMaxDailyLongTradingAmount().doubleValue()) {
//                    return Result.fail(MORE_THAN_MAX_DAIY_BID_AMOUNT.getCode(), MORE_THAN_MAX_DAIY_BID_AMOUNT.getMessage());
//                }
//            }
//        }

        return result;
    }

    private Result<BigDecimal> computeAndCheckOrderPrice(BigDecimal orderPrice, int orderType, int orderDirection, int assetId, Long brokerId){
        int scale = brokerTradingPairManager.getTradingPairById((long) assetId).getTradingPricePrecision();
        if (orderType == PriceTypeEnum.RIVAL_PRICE.getCode()){

            List<CompetitorsPriceDTO> competitorsPriceList = realTimeEntrustManager.getUsdtCompetitorsPriceOrder();
            Integer opDirection = ASK.getCode() + BID.getCode() - orderDirection;

            CompetitorsPriceDTO competitorsPriceDTO = competitorsPriceList.stream().filter(competitorsPrice-> competitorsPrice.getOrderDirection() == opDirection &&
                    competitorsPrice.getId() == assetId).findFirst().orElse(null);

            if (null == competitorsPriceDTO || null == competitorsPriceDTO.getPrice() || BigDecimal.ZERO.compareTo(competitorsPriceDTO.getPrice()) >= 0){
                log.warn("bizType:{},\037traceId:{},\037param:{},\037detailMsg:{}\037", TradeBizTypeEnum.COIN_ORDER,
                        null, assetId, "subjectId:"+assetId + ",orderDirection:" + orderDirection);
                return Result.fail(NO_COMPETITORS_PRICE.getCode(), NO_COMPETITORS_PRICE.getMessage());
            }
            return Result.suc(competitorsPriceDTO.getPrice().setScale(scale, BigDecimal.ROUND_DOWN));
        }

        //市场单
        if (orderType == MARKET_PRICE.getCode()) {
            //如果是fota，获取最新成交价
            BigDecimal curPrice = realTimeEntrustManager.getUsdtLatestPrice(assetId).setScale(scale, BigDecimal.ROUND_DOWN);
            BigDecimal buyMaxPrice = curPrice.multiply(new BigDecimal("1.9")).setScale(scale, RoundingMode.UP);
            BigDecimal sellMinPrice = curPrice.multiply(new BigDecimal("0.1")).setScale(scale, BigDecimal.ROUND_DOWN);
            orderPrice = orderDirection == ASK.getCode() ? sellMinPrice : buyMaxPrice;
            if (orderPrice.compareTo(BigDecimal.ZERO) <=0) {
                log.error("=== assetId:{}, scale:{}, curPrice:{}, buyMaxPrice:{}, sellMinPrice:{}", assetId, scale, curPrice, buyMaxPrice, sellMinPrice);
                LogUtil.error( "computeAndCheckOrderPrice", null, orderPrice, AMOUNT_ILLEGAL.getMessage());
                return Result.fail(AMOUNT_ILLEGAL.getCode(), AMOUNT_ILLEGAL.getMessage());
            }
            return Result.suc(orderPrice);
        }
        if (orderType != SPECIFIED_PRICE.getCode() && orderType != PASSIVE.getCode()){
            return Result.fail(PRICE_TYPE_ILLEGAL.getCode(), PRICE_TYPE_ILLEGAL.getMessage());
        }
        if (null == orderPrice || orderPrice.compareTo(BigDecimal.ZERO) <= 0 || orderPrice.scale() > scale){
            return Result.fail(AMOUNT_ILLEGAL.getCode(), AMOUNT_ILLEGAL.getMessage());
        }
        return Result.suc(orderPrice);
    }
    private int insertUsdkOrder(UsdkOrderDO usdkOrderDO) {
        return usdkOrderMapper.insert(usdkOrderDO);
    }
    private int batchInsertUsdkOrder(List<UsdkOrderDO> list) {
        return usdkOrderMapper.batchInsert(list);
    }

    public ResultCode cancelOrder(Long userId, Long orderId, Map<String, String> userInfoMap) throws Exception{
        ResultCode resultCode = ResultCode.success();
        if (Objects.isNull(userId) || Objects.isNull(orderId)) {
            LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), String.valueOf(orderId), orderId, ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByUserIdAndId(userId, orderId);
        if (Objects.isNull(usdkOrderDO)) {
            LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), String.valueOf(orderId), usdkOrderDO, ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        if (usdkOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), String.valueOf(orderId), orderId, ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getMessage());
            return ResultCode.error(ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getCode(),
                    ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getMessage());
        }
        List<Long> orderIdList = new ArrayList<Long>();
        orderIdList.add(orderId);
        sendCancelReq(orderIdList, userId);
        return resultCode;
    }

    public Result batchCancelOrder(Long userId, List<Long> orderIds) throws Exception{
        Result result = Result.suc("success");
        if (Objects.isNull(userId) || CollectionUtils.isEmpty(orderIds)) {
            LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), null, orderIds, ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return Result.fail(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        if (orderIds.size() > Constant.BATCH_ORDER_MAX_SIZE){
            LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), null, orderIds, ResultCodeEnum.BATCH_SIZE_OUT_OF_LIMIT.getMessage());
            return result.error(ResultCodeEnum.BATCH_SIZE_OUT_OF_LIMIT.getCode(),ResultCodeEnum.BATCH_SIZE_OUT_OF_LIMIT.getMessage());
        }
        List<UsdkOrderDO> usdkOrderDOList = usdkOrderMapper.listByUserIdAndIds(userId, orderIds);
        if (CollectionUtils.isEmpty(usdkOrderDOList) || usdkOrderDOList.size() != orderIds.size()) {
            LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), null, orderIds, ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return Result.fail(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        for (UsdkOrderDO usdkOrderDO : usdkOrderDOList){
            if (usdkOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
                LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), String.valueOf(usdkOrderDO.getId()), usdkOrderDO, ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getMessage());
                return Result.fail(ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getCode(),
                        ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getMessage());
            }
        }
        sendCancelReq(orderIds, userId);
        return result;
    }

    /**
     * 根据撮合发出的MQ消息撤单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultCode cancelOrderByMessage(BaseCanceledMessage baseCanceledMessage) {

        ResultCode resultCode;
        Integer toStatus = baseCanceledMessage.getStatus();
        long userId=baseCanceledMessage.getUserId();
        long orderId = baseCanceledMessage.getOrderId();
        BigDecimal unfilledAmount = baseCanceledMessage.getUnfilledAmount();
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByUserIdAndId(userId, orderId);

        //更新usdk委托表
        int ret = usdkOrderMapper.cancel(userId, orderId, toStatus);
        Map<String, Object> parameter = new HashMap<>();
        if (ret > 0){
            Integer orderDirection = usdkOrderDO.getOrderDirection();
            Integer assetId = 0;
            BigDecimal unlockAmount ;
            BrokerTradingPairConfig tradingPairConfig = brokerTradingPairManager.getTradingPairById(usdkOrderDO.getAssetId().longValue());
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                assetId = tradingPairConfig.getQuoteId();
                BigDecimal price = usdkOrderDO.getPrice();
                unlockAmount = unfilledAmount.multiply(price);
            }else {
                assetId = tradingPairConfig.getBaseId();
                unlockAmount = unfilledAmount;
            }
            //解冻Coin钱包账户
            Result<Boolean> updateLockedAmountRet;
            try{
                CapitalAccountAddAmountDTO capitalAccountAddAmountDTO = new CapitalAccountAddAmountDTO();
                capitalAccountAddAmountDTO.setAddOrderLocked(unlockAmount.negate());
                capitalAccountAddAmountDTO.setUserId(userId);
                capitalAccountAddAmountDTO.setAssetId(assetId);

                updateLockedAmountRet = assetWriteService.addCapitalAmountWithoutLocked(capitalAccountAddAmountDTO, String.valueOf(orderId), AssetOperationTypeEnum.USDT_EXCHANGE_CANCLE_ORDER.getCode());
            }catch (Exception e){
                parameter.put("assetId", assetId);
                parameter.put("lockedAmount", unlockAmount.negate().toString());
                LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), String.valueOf(orderId), parameter, "Asset RPC Error!, assetWriteService.addCapitalAmount.updateLockedAmount exception", e);
                throw new BizException(BIZ_ERROR.getCode(),"cancelOrder assetWriteService.addCapitalAmount exception");
            }
            if (!updateLockedAmountRet.isSuccess() || !updateLockedAmountRet.getData()){
                parameter.put("assetId", assetId);
                parameter.put("lockedAmount", unlockAmount.negate().toString());
                LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), String.valueOf(orderId), parameter, "errorCode:"+ updateLockedAmountRet.getCode() + ", errorMsg:"+ updateLockedAmountRet.getMessage());
                throw new BizException(BIZ_ERROR.getCode(),"cancelOrder assetWriteService.addCapitalAmount failed");
            }
            monitorLogManager.cancelCoinOrderInfo(usdkOrderDO);
            sendCanceledMessage(usdkOrderDO, unfilledAmount);
            resultCode = ResultCode.success();
        }else {
            parameter.put("userId", userId);
            parameter.put("toStatus", toStatus);
            LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), String.valueOf(orderId), parameter, "usdkOrderMapper.cancel failed");
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "usdkOrderMapper.cancel failed" + usdkOrderDO.getId());
        }
        return resultCode;
    }

    public void sendCanceledMessage(UsdkOrderDO usdkOrderDO, BigDecimal unfilledAmount){
        BaseCanceledMessage orderMessage = new BaseCanceledMessage();
        orderMessage.setOrderId(usdkOrderDO.getId());
        orderMessage.setUserId(usdkOrderDO.getUserId());
        orderMessage.setSubjectId(usdkOrderDO.getAssetId().longValue());
        orderMessage.setTotalAmount(usdkOrderDO.getTotalAmount());
        orderMessage.setUnfilledAmount(unfilledAmount);
        Boolean sendRet = rocketMqManager.sendMessage(TRD_COIN_CANCELED, orderMessage.getSubjectId()+"", orderMessage.getOrderId()+"", orderMessage);
        if (!sendRet){
            LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), String.valueOf(usdkOrderDO.getId()), orderMessage, "Send RocketMQ Message Failed");
        }
    }

    public void sendCancelReq(List<Long> orderIdList, Long userId) {
        if (CollectionUtils.isEmpty(orderIdList)) {
            return;
        }
        //发送MQ消息到match

        List<List<Long>> splitList = Lists.partition(orderIdList, 50);
        splitList.forEach(subList -> {
            BaseCancelReqMessage cancelReqMessage = new BaseCancelReqMessage();
            cancelReqMessage.setUserId(userId);
            cancelReqMessage.setCancelType(CancelTypeEnum.CANCEL_BY_ORDERID);
            cancelReqMessage.setIdList(subList);
            Boolean sendRet = rocketMqManager.sendMessage(TRD_COIN_CANCEL_REQ, "coin",
                    Joiner.on("_").join(subList), cancelReqMessage);
            if (BooleanUtils.isNotTrue(sendRet)){
                LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), null, cancelReqMessage, "failed to send cancel usdk mq");
            }
        });

    }


    public ResultCode cancelAllOrder(Long userId, Map<String, String> userInfoMap) throws Exception{
        if (Objects.isNull(userId)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }

        List<UsdkOrderDO> list = usdkOrderMapper.selectUnfinishedOrderByUserId(userId);
        if (list != null) {
            List<Long> orderIdList = list.stream()
                    .filter(usdkOrderDO -> usdkOrderDO.getOrderType() != OrderTypeEnum.ENFORCE.getCode())
                    .map(UsdkOrderDO::getId)
                    .collect(toList());
            sendCancelReq(orderIdList, userId);
        }

        return ResultCode.success();
    }

    @Transactional(rollbackFor = Throwable.class)
    public ResultCode updateOrderByMatch(UsdkMatchedOrderDTO usdkMatchedOrderDTO) throws Exception {
        Profiler profiler =  null == ThreadContextUtil.getProfiler() ? new Profiler("UsdkOrderManager.updateOrderByMatch", usdkMatchedOrderDTO.getId().toString()) : ThreadContextUtil.getProfiler();
        if (usdkMatchedOrderDTO == null) {
            LogUtil.error( TradeBizTypeEnum.COIN_DEAL.toString(), String.valueOf(usdkMatchedOrderDTO.getId()), usdkMatchedOrderDTO, ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "illegal usdkMatchedOrderDTO" + usdkMatchedOrderDTO);
        }
        Long transferTime = System.currentTimeMillis();

        UsdkOrderDO askUsdkOrder = usdkOrderMapper.selectByUserIdAndId(usdkMatchedOrderDTO.getAskUserId(), usdkMatchedOrderDTO.getAskOrderId());
        UsdkOrderDO bidUsdkOrder = usdkOrderMapper.selectByUserIdAndId(usdkMatchedOrderDTO.getBidUserId(), usdkMatchedOrderDTO.getBidOrderId());
        profiler.complelete("select order");
        if (null == askUsdkOrder ) {
            LogUtil.error( TradeBizTypeEnum.COIN_DEAL.toString(), String.valueOf(usdkMatchedOrderDTO.getId()), askUsdkOrder, "null askOrder!!! ");
            return ResultCode.error(ILLEGAL_PARAM.getCode(), "null askOrder!!! ");
        }
        if (null == bidUsdkOrder) {
            LogUtil.error( TradeBizTypeEnum.COIN_DEAL.toString(), String.valueOf(usdkMatchedOrderDTO.getId()), bidUsdkOrder, "null bidOrder!!! ");
            return ResultCode.error(ILLEGAL_PARAM.getCode(), "null bidOrder!!! ");
        }
        List<UsdkOrderDO> usdkOrderDOS = Arrays.asList(askUsdkOrder, bidUsdkOrder);
        usdkOrderDOS.sort((a, b) -> a.getId().compareTo(b.getId()));

        BigDecimal filledAmount = new BigDecimal(usdkMatchedOrderDTO.getFilledAmount());
        Map<String, Object> parameter = new HashMap<>();
        if (BasicUtils.gt(filledAmount, askUsdkOrder.getUnfilledAmount())){
            parameter.put("filledAmount", filledAmount);
            parameter.put("askUnfilledAmount", askUsdkOrder.getUnfilledAmount());
            LogUtil.error( TradeBizTypeEnum.COIN_DEAL.toString(), String.valueOf(usdkMatchedOrderDTO.getId()), parameter, "askOrder unfilledAmount not enough");
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "askOrder unfilledAmount not enough. order="+askUsdkOrder);
        }
        if (BasicUtils.gt(filledAmount, bidUsdkOrder.getUnfilledAmount())){
            parameter.put("filledAmount", filledAmount);
            parameter.put("bidUnfilledAmount", bidUsdkOrder.getUnfilledAmount());
            LogUtil.error( TradeBizTypeEnum.COIN_DEAL.toString(), String.valueOf(usdkMatchedOrderDTO.getId()), parameter, "bidOrder unfilledAmount not enough");
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "bidOrder unfilledAmount not enough. order="+bidUsdkOrder);
        }

        BigDecimal filledPrice = new BigDecimal(usdkMatchedOrderDTO.getFilledPrice());

        for (UsdkOrderDO usdkOrderDO : usdkOrderDOS) {
            int updateAskOrderRet = doUpdateUsdkOrder(usdkOrderDO.getUserId(), usdkOrderDO.getId(),  filledAmount, filledPrice, new Date(transferTime));
            if (updateAskOrderRet <= 0) {
                parameter.put("userId", usdkOrderDO.getUserId());
                parameter.put("orderId", usdkOrderDO.getId());
                parameter.put("filledAmount", filledAmount);
                parameter.put("filledPrice", filledPrice);
                LogUtil.error( TradeBizTypeEnum.COIN_DEAL.toString(), String.valueOf(usdkMatchedOrderDTO.getId()), parameter, "doUpdateUsdkOrder failed");
                throw new BizException(ResultCodeEnum.BIZ_ERROR.getCode(), "doUpdateUsdkOrder failed, order=" + usdkOrderDO);
            }
        }
        profiler.complelete("update usdt order");

        //写成交记录
        UsdkMatchedOrderDO askMatchRecordDO = com.fota.trade.common.BeanUtils.extractUsdtRecord(usdkMatchedOrderDTO, OrderDirectionEnum.ASK.getCode(), askUsdkOrder.getBrokerId());
        UsdkMatchedOrderDO bidMatchRecordDO = com.fota.trade.common.BeanUtils.extractUsdtRecord(usdkMatchedOrderDTO, OrderDirectionEnum.BID.getCode(), bidUsdkOrder.getBrokerId());
        int ret = usdkMatchedOrder.insert(Arrays.asList(askMatchRecordDO, bidMatchRecordDO));
        profiler.complelete("insert match record");
        if (ret < 2){
            LogUtil.error( TradeBizTypeEnum.COIN_DEAL.toString(), String.valueOf(usdkMatchedOrderDTO.getId()), Arrays.asList(askMatchRecordDO, bidMatchRecordDO), "usdkMatchedOrder.insert failed");
            throw new RuntimeException("usdkMatchedOrder.insert failed");
        }


        // 买币 bid +totalAsset = filledAmount - filledAmount * feeRate
        // 买币 bid -totalUsdk = filledAmount * filledPrice
        // 买币 bid -lockedUsdk = filledAmount * bidOrderPrice
        // 卖币 ask +totalUsdk = filledAmount * filledPrice - filledAmount * filledPrice * feeRate
        // 卖币 ask -lockedAsset = filledAmount
        // 卖币 ask -totalAsset = filledAmount
        BigDecimal addLockedBTC = BigDecimal.ZERO;
        BigDecimal addBidTotalAsset = filledAmount.subtract(BigDecimal.ZERO);
        BigDecimal addTotalBTC = filledAmount.multiply(filledPrice);
        if (usdkMatchedOrderDTO.getBidOrderPrice() != null){
            addLockedBTC = filledAmount.multiply(new BigDecimal(usdkMatchedOrderDTO.getBidOrderPrice()));
        }
        BigDecimal addAskTotalBTC = filledAmount.multiply(filledPrice);
        BigDecimal addLockedAsset = filledAmount;
        BigDecimal addTotalAsset = filledAmount;
        List<CapitalAccountAddAmountDTO> updateList = new ArrayList<>();

        Integer tradingPairId = usdkMatchedOrderDTO.getAssetId();
        BrokerTradingPairConfig tradingPairConfig = brokerTradingPairManager.getTradingPairById(tradingPairId.longValue());
        Integer baseAssetId = tradingPairConfig.getBaseId();
        Integer quoteAssetId = tradingPairConfig.getQuoteId();
        if (!askUsdkOrder.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode())){
            //卖方BTC账户增加
            //现货交易收取手续费
            BigDecimal askFeeRate = askUsdkOrder.getFee() == null ? BigDecimal.ZERO : askUsdkOrder.getFee();
            BigDecimal fee = askFeeRate.multiply(addAskTotalBTC);
            recordUsdkOrderFee(askUsdkOrder.getBrokerId(), fee, quoteAssetId);

            CapitalAccountAddAmountDTO askBtcCapital = new CapitalAccountAddAmountDTO();
            askBtcCapital.setUserId(askUsdkOrder.getUserId());
            askBtcCapital.setAssetId(quoteAssetId);
            askBtcCapital.setAddTotal(addAskTotalBTC.subtract(fee));
            updateList.add(askBtcCapital);
            //卖方对应资产账户的冻结和总金额减少
            CapitalAccountAddAmountDTO askMatchAssetCapital = new CapitalAccountAddAmountDTO();
            askMatchAssetCapital.setUserId(askUsdkOrder.getUserId());
            askMatchAssetCapital.setAssetId(baseAssetId);
            askMatchAssetCapital.setAddTotal(addTotalAsset.negate());
            askMatchAssetCapital.setAddOrderLocked(addLockedAsset.negate());
            updateList.add(askMatchAssetCapital);
        }
        if (!bidUsdkOrder.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode())){
            //买方BTC账户总金额和冻结减少
            CapitalAccountAddAmountDTO bidBtcCapital = new CapitalAccountAddAmountDTO();
            bidBtcCapital.setUserId(bidUsdkOrder.getUserId());
            bidBtcCapital.setAssetId(quoteAssetId);
            bidBtcCapital.setAddTotal(addTotalBTC.negate());
            bidBtcCapital.setAddOrderLocked(addLockedBTC.negate());
            updateList.add(bidBtcCapital);
            //买方对应资产账户总金额增加
            //现货交易收取手续费
            BigDecimal bidFeeRate = bidUsdkOrder.getFee() == null ? BigDecimal.ZERO : bidUsdkOrder.getFee();
            BigDecimal fee = bidFeeRate.multiply(addBidTotalAsset);
            recordUsdkOrderFee(bidUsdkOrder.getBrokerId(), fee, baseAssetId);

            CapitalAccountAddAmountDTO bidMatchAssetCapital = new CapitalAccountAddAmountDTO();
            bidMatchAssetCapital.setUserId(bidUsdkOrder.getUserId());
            bidMatchAssetCapital.setAssetId(baseAssetId);
            bidMatchAssetCapital.setAddTotal(addBidTotalAsset.subtract(fee));
            updateList.add(bidMatchAssetCapital);
        }
        Result<Boolean> updateRet;
        try {
            updateRet = assetWriteService.batchAddCapitalAmount(updateList, String.valueOf(usdkMatchedOrderDTO.getId()), AssetOperationTypeEnum.USDT_EXCHANGE_ORDER_DEALED.getCode());
            profiler.complelete("updateBalance");
        }catch (Exception e){
            LogUtil.error( TradeBizTypeEnum.COIN_DEAL.toString(), String.valueOf(usdkMatchedOrderDTO.getId()), updateList, "Asset RPC Error!, assetWriteService.batchAddCapitalAmount exception", e);
            throw new BizException(BIZ_ERROR.getCode(), "assetWriteService.batchAddCapitalAmount exception, updateList:{}" + updateList);
        }
        if (!updateRet.isSuccess() || !updateRet.getData()) {
            LogUtil.error( TradeBizTypeEnum.COIN_DEAL.toString(), String.valueOf(usdkMatchedOrderDTO.getId()), updateList, "errorCode:"+ updateRet.getCode() + ", errorMsg:"+ updateRet.getMessage());
            throw new BizException(BIZ_ERROR.getCode(), "assetWriteService.batchAddCapitalAmount failed, updateList:{}" + updateList+ "updateRet="+updateRet);
        }

        long matchId = usdkMatchedOrderDTO.getId();
        Runnable runnable = () -> {
            postProcessOrder(askUsdkOrder, filledAmount, filledPrice, matchId);
            postProcessOrder(bidUsdkOrder, filledAmount, filledPrice, matchId);
        };
        ThreadContextUtil.setPostTask(runnable);

        return ResultCode.success();
    }


    private BigDecimal getFeeRateByBrokerId(Long brokerId, long tradingPairId){
        BrokerTradingPairConfig tradingPairConfig = brokerTradingPairManager.getTradingPairById(tradingPairId);

        if (tradingPairConfig != null && Objects.equals(brokerId, tradingPairConfig.getBrokerId())){
            return tradingPairConfig.getFeeRate();
        } else {
            return defaultFee;
        }
    }

    public void recordUsdkOrderFee(Long brokerId, BigDecimal fee, Integer assetId){
        if (fee.compareTo(ZERO) > 0){
            String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
            Double currentFee = redisManager.counter(Constant.USDK_TODAY_FEE + brokerId + assetId + dateStr, fee);
            if (null == currentFee) {
                log.error("recordUsdkOrderFee failed, fee={}", fee);
            }
        }
    }

    private void postProcessOrder(UsdkOrderDO usdkOrderDO, BigDecimal filledAmount, BigDecimal filledPrice, long matchId) {
        monitorLogManager.coinDealOrderInfo(usdkOrderDO, filledAmount);
        CoinDealedMessage coinDealedMessage = new CoinDealedMessage();
        coinDealedMessage.setUserId(usdkOrderDO.getUserId());
        coinDealedMessage.setOrderId(usdkOrderDO.getId());
        coinDealedMessage.setSubjectId(usdkOrderDO.getAssetId());
        coinDealedMessage.setSubjectName(usdkOrderDO.getAssetName());
        coinDealedMessage.setOrderDirection(usdkOrderDO.getOrderDirection());
        coinDealedMessage.setOrderType(usdkOrderDO.getOrderType());
        coinDealedMessage.setFilledAmount(filledAmount);
        coinDealedMessage.setFilledPrice(usdkOrderDO.getPrice());
        coinDealedMessage.setMatchId(matchId);
        //set brokerId businessType feeRate
        coinDealedMessage.setBrokerId(usdkOrderDO.getBrokerId());
        coinDealedMessage.setBusinessType(BusinessTypeEnum.SPOT);
        coinDealedMessage.setFeeRate(usdkOrderDO.getFee());
        coinDealedMessage.setFilledPriceTwo(filledPrice);

        rocketMqManager.sendMessage(TRD_COIN_DEAL, usdkOrderDO.getAssetId()+"", matchId + "_" + usdkOrderDO.getId(), coinDealedMessage);
    }


    private int doUpdateUsdkOrder(long userId, long id, BigDecimal filledAmount, BigDecimal filledPrice, Date gmtModified) {
        return usdkOrderMapper.updateByFilledAmount(userId, id, filledAmount, filledPrice, gmtModified);
    }

    /**
     * 后台灵活上币会将限制条件写入对象中，apolo进行推送。
     * @param brokerId
     * @param tradingPairId
     * @param price
     * @param orderDirection
     * @return
     */
    public Result<Long> checkSpotOrderPriceLimit(Long brokerId, int tradingPairId, BigDecimal price, Integer orderDirection, boolean isMarket) {
        Result<Long> result = new Result<>();
        result.setCode(0);
        if (brokerId == null || price == null || orderDirection == null) {
            log.warn("checkSpotOrderPriceLimit param illegal brokerId={} tradingPairId={} price={} orderDirection={}", brokerId, tradingPairId, price, orderDirection);
            return result;
        }
        if(isMarket)
        {
            return result;
        }

        try {
            //限制最高买价 最低卖价
            Long tradingPairIdLong = Long.valueOf(tradingPairId);
            BrokerTradingPairConfig tradingPairConfig = brokerTradingPairManager.getTradingPairById(tradingPairIdLong);
            log.info("check order price, tradingPairId {}, price {}, orderdirection {} tradingPairConfig {}", tradingPairId, price, orderDirection, tradingPairConfig.toString());

            if (tradingPairConfig.isTradingPriceLimitEnabled()) {
                if (orderDirection.equals(OrderDirectionEnum.BID.getCode())) {
                    BigDecimal standardPrice = null;
                    BigDecimal percent = null;
                    BigDecimal maxPrice = null;
                    //根据前台传过来的类型来判断使用固定值还是百分比
                    if(tradingPairConfig.isTradingFixedPriceLimitEnabled())
                    {
                        maxPrice = tradingPairConfig.getMaxLongTradingPrice();
                    }
                    if(tradingPairConfig.isTradingPriceRateLimitEnabled())
                    {
                        standardPrice = getLimitPrice(tradingPairConfig, orderDirection, tradingPairId);
                        percent = tradingPairConfig.getMaxLongTradingPriceRate();
                    }

                    return checkBidPriceLimit(price, standardPrice, percent, maxPrice);
                } else if (orderDirection.equals(OrderDirectionEnum.ASK.getCode())) {
                    BigDecimal standardPrice = null;
                    BigDecimal percent =  null;
                    BigDecimal minPrice = null;
                    //根据前台传过来的类型来判断使用固定值还是百分比
                    if(tradingPairConfig.isTradingFixedPriceLimitEnabled())
                    {
                        minPrice = tradingPairConfig.getMinShortTradingPrice();
                    }
                    if(tradingPairConfig.isTradingPriceRateLimitEnabled())
                    {
                        standardPrice = getLimitPrice(tradingPairConfig, orderDirection, tradingPairId);
                        percent = tradingPairConfig.getMinShortTradingPriceRate();
                    }

                    return checkAskPriceLimit(price, standardPrice, percent, minPrice);
                }
            }
        } catch (Exception e) {
            log.error("checkSpotOrderPriceLimit error", e);
        }

        return result;
    }

    public BigDecimal getLimitPrice(BrokerTradingPairConfig tradingPairConfig,  Integer orderDirection, int assetId)
    {
        if(tradingPairConfig.getTradingPriceRateType() == TradingPriceRateTypeEnum.ASK_BID_CURRENT_PRICE.getCode())
        {
            return getcurrentPrice(orderDirection, assetId);
        }
        else if(tradingPairConfig.getTradingPriceRateType() == TradingPriceRateTypeEnum.LATEST_PRICE.getCode())
        {
            //最新成交价
            return getUsdtLatestPrice(assetId);
        }
        else
        {
            return null;
        }
    }

    /**
     * get usdt latest price
     * @param assetId
     * @return
     */
    public BigDecimal getUsdtLatestPrice(int assetId)
    {
        BigDecimal usdtLatestPrice = realTimeEntrustManager.getUsdtLatestPrice(assetId);
        if(ZERO.equals(usdtLatestPrice))
        {
            return null;
        }
        else
        {
            return usdtLatestPrice;
        }
    }

    /**
     * 获取买一卖一价
     * @param orderDirectionType
     * @param assetId
     * @return
     */
    public BigDecimal getcurrentPrice(Integer orderDirectionType, int assetId)
    {
        if(null == orderDirectionType)
        {
            return null;
        }
        //获取USDT买一卖一价
        List<CompetitorsPriceDTO> competitorsPriceDTOList = realTimeEntrust.getUsdtCompetitorsPriceOrder();
        //获取买一卖一价
        Optional<CompetitorsPriceDTO> competitorsPriceDTOOptional = competitorsPriceDTOList
                .stream()
                .filter(competitorsPrice -> orderDirectionType.equals(competitorsPrice.getOrderDirection()) && competitorsPrice.getId() == assetId)
                .findFirst();

        if (competitorsPriceDTOOptional.isPresent() && competitorsPriceDTOOptional.get().getPrice().compareTo(BigDecimal.ZERO) > 0) {
            return competitorsPriceDTOOptional.get().getPrice();
        }
        return null;
    }

    /**
     * check bid price X<=min(B*(1+C%) ， A)
     * @return
     */
    public Result<Long> checkBidPriceLimit(BigDecimal price, BigDecimal standardPrice, BigDecimal percent, BigDecimal maxPrice)
    {
        log.debug("begin check bid price limit. price {}, standard price {}, percent {}, max price {}.", price, standardPrice, percent, maxPrice);
        Result<Long> result = new Result<>();
        result.setCode(0);
        //如果限定价格和标准价格均为空，则不进行校验
        if(null == standardPrice && null == maxPrice)
        {
            return result;
        }
        BigDecimal bigPercent = new BigDecimal(0);
        if(null != percent)
        {
            bigPercent =  percent.divide(new BigDecimal(100)).add(new BigDecimal(1));
        }
        BigDecimal limitPrice = new BigDecimal(Integer.MAX_VALUE);
        //如果限定价格不为空
        if(null != maxPrice && null == standardPrice)
        {
            limitPrice = maxPrice;
        }
        //如果标准价格不为空
        if(null == maxPrice && null != standardPrice)
        {
            limitPrice = standardPrice.multiply(bigPercent);
        }
        //如果两者都不为空
        if(null != maxPrice && null != standardPrice)
        {
            limitPrice = maxPrice.min(standardPrice.multiply(bigPercent));
        }

        if(price.compareTo(limitPrice) > 0)
        {
            result.error(ResultCodeEnum.ORDER_PRICE_LIMIT_CHECK_BID_FAILED.getCode(), limitPrice.toPlainString());
        }
        else
        {
            result.setCode(0);
        }

        return result;
    }

    /**
     * check ask price Y>=Max(E*(1-F%), D)
     * @return
     */
    public Result<Long> checkAskPriceLimit(BigDecimal price, BigDecimal standardPrice, BigDecimal percent, BigDecimal minPrice)
    {
        log.debug("begin check ask price limit. price {}, standard price {}, percent {}, max price {}.", price, standardPrice, percent, minPrice);
        Result<Long> result = new Result<>();
        result.setCode(0);
        //如果限定价格和标准价格均为空，则不进行校验
        if(null == standardPrice && null == minPrice)
        {
            return result;
        }
        BigDecimal bigPercent = new BigDecimal(0);
        if(null != percent)
        {
            bigPercent =  new BigDecimal(1).subtract(percent.divide(new BigDecimal(100)));
        }
        BigDecimal limitPrice = new BigDecimal(0);
        //如果限定价格不为空
        if(null != minPrice && null == standardPrice)
        {
            limitPrice = minPrice;
        }
        //如果标准价格不为空
        if(null == minPrice && null != standardPrice)
        {
            limitPrice = standardPrice.multiply(bigPercent);
        }
        //如果两者都不为空
        if(null != minPrice && null != standardPrice)
        {
            limitPrice = minPrice.max(standardPrice.multiply(bigPercent));
        }

        if(price.compareTo(limitPrice) < 0)
        {
            result.error(ResultCodeEnum.ORDER_PRICE_LIMIT_CHECK_ASK_FAILED.getCode(), limitPrice.toPlainString());
        }
        else
        {
            result.setCode(0);
        }

        return result;
    }
}



