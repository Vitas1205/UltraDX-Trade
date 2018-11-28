package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.BalanceTransferDTO;
import com.fota.asset.domain.CoinExchangeOrderBatchLock;
import com.fota.asset.domain.UserCapitalDTO;
import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.CapitalService;
import com.fota.common.Result;
import com.fota.match.service.UsdkMatchedOrderService;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.PriceTypeEnum;
import com.fota.trade.client.CancelTypeEnum;
import com.fota.trade.client.PlaceCoinOrderDTO;
import com.fota.trade.client.PlaceOrderRequest;
import com.fota.trade.client.PlaceOrderResult;
import com.fota.trade.common.BizException;
import com.fota.trade.common.BusinessException;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderTypeEnum;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.UsdkMatchedOrderMapper;
import com.fota.trade.mapper.UsdkOrderMapper;
import com.fota.trade.msg.*;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ContractUtils;
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

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.fota.trade.PriceTypeEnum.MARKET_PRICE;
import static com.fota.trade.PriceTypeEnum.SPECIFIED_PRICE;
import static com.fota.trade.common.ResultCodeEnum.*;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;
import static com.fota.trade.domain.enums.OrderStatusEnum.COMMIT;
import static com.fota.trade.domain.enums.OrderStatusEnum.PART_MATCH;
import static com.fota.trade.domain.enums.OrderTypeEnum.*;
import static com.fota.trade.msg.TopicConstants.*;
import static java.util.stream.Collectors.toList;


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

    @Resource
    private UsdkMatchedOrderMapper usdkMatchedOrderMapper;

    @Resource
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private RocketMqManager rocketMqManager;

    @Autowired
    private CapitalService capitalService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private UsdkMatchedOrderService usdkMatchedOrderService;

    @Autowired
    private UsdkMatchedOrderMapper usdkMatchedOrder;
    @Autowired
    private RealTimeEntrustManager realTimeEntrustManager;
    @Autowired
    private CurrentPriceManager currentPriceManager;

    private CapitalService getCapitalService() {
        return capitalService;
    }
    private AssetService getAssetService() {
        return assetService;
    }


    //TODO 优化: 先更新账户，再insert订单，而不是先insert订单再更新账户
    @Transactional(rollbackFor={Throwable.class})
    public com.fota.common.Result<Long> placeOrder(UsdkOrderDTO usdkOrderDTO, Map<String, String> userInfoMap)throws Exception {
        Profiler profiler = new Profiler("UsdkOrderManager.placeOrder");
        ThreadContextUtil.setPrifiler(profiler);
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
        if (count >= 200) {
            profiler.complelete("too much orders");
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
        Long userId = usdkOrderDO.getUserId();
        Integer orderDirection = usdkOrderDO.getOrderDirection();
        List<UserCapitalDTO> list = getAssetService().getUserCapital(userId);
        profiler.complelete("getUserCapital");
        usdkOrderDO.setFee(usdkFee);
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
            Result<BigDecimal> checkPriceRes = computeAndCheckOrderPrice(usdkOrderDO.getPrice(), usdkOrderDO.getOrderType(), usdkOrderDO.getOrderDirection(), usdkOrderDO.getAssetId());
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
                log.error("insert contractOrder failed");
                throw new RuntimeException("insert contractOrder failed");
            }
            orderId = usdkOrderDO.getId();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
            int assetTypeId = 0;
            BigDecimal entrustValue = BigDecimal.ZERO;
            int errorCode = 0;
            String errorMsg;
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                assetTypeId = AssetTypeEnum.BTC.getCode();
                entrustValue = orderValue;
                errorCode = ResultCodeEnum.CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode();
                errorMsg = ResultCodeEnum.CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage();
            }else {
                assetTypeId = assetId;
                entrustValue = usdkOrderDO.getTotalAmount();
                errorCode = ResultCodeEnum.COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode();
                errorMsg = ResultCodeEnum.COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage();
            }
            //查询账户可用余额
            for(UserCapitalDTO userCapitalDTO : list){
                if (userCapitalDTO.getAssetId() == assetTypeId){
                    BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                    BigDecimal lockedAmount = new BigDecimal(userCapitalDTO.getLockedAmount());
                    BigDecimal availableAmount = amount.subtract(lockedAmount);
                    //判断账户可用余额是否大于orderValue
                    if (availableAmount.compareTo(entrustValue) >= 0){
                        Date gmtModified = userCapitalDTO.getGmtModified();
                        try{
                            Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,
                                    userCapitalDTO.getAssetId(), String.valueOf(entrustValue), gmtModified.getTime());
                            profiler.complelete("updateLockedAmount");
                            if (!updateLockedAmountRet){
                                log.error("placeOrder getCapitalService().updateLockedAmount failed usdkOrderDO:{}", usdkOrderDO);
                                throw new BusinessException(errorCode, errorMsg);
                            }
                        }catch (Exception e){
                            log.error("Asset RPC Error!, placeOrder getCapitalService().updateLockedAmount exception usdkOrderDO:{}", usdkOrderDO, e);
                            throw new RuntimeException("placeOrder getCapitalService().updateLockedAmount exception");
                        }
                    }else {
                        log.error("totalAmount:{}, entrustValue:{}, availableAmount:{}", amount, entrustValue, availableAmount);
                        throw new BusinessException(errorCode, errorMsg);
                    }
                }
            }
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    1, usdkOrderDTO.getAssetName(), username, ipAddress, usdkOrderDTO.getTotalAmount(), transferTime, 2, usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getUserId(), 1);
        } else if (usdkOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()){
            //强平单处理
            int ret = insertUsdkOrder(usdkOrderDO);
            if (ret <= 0){
                log.error("insert contractOrder failed");
                throw new RuntimeException("insert contractOrder failed");
            }
            orderId = usdkOrderDO.getId();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    1, usdkOrderDTO.getAssetName(), username, ipAddress, usdkOrderDTO.getTotalAmount(), transferTime, 3, usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getUserId(), 2);
        }
        usdkOrderDTO.setCompleteAmount(BigDecimal.ZERO);
        //消息一定要在事务外发，不然会出现收到下单消息，db还没有这个订单
       Runnable postTask = () -> {
           CoinPlaceOrderMessage placeOrderMessage = toCoinPlaceOrderMessage(usdkOrderDO);
           Boolean sendRet = rocketMqManager.sendMessage(TopicConstants.TRD_COIN_ORDER, placeOrderMessage.getSubjectId()+"", placeOrderMessage.getOrderId()+"", placeOrderMessage);
           if (!sendRet){
               log.error("Send RocketMQ Message Failed ");
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
        if (!placeOrderRequest.checkParam()){
            log.error("checkParam failed, placOrderRequest");
            return Result.fail(ILLEGAL_PARAM.getCode(), ILLEGAL_PARAM.getMessage());
        }
        Profiler profiler = new Profiler("UsdkOrderManager.batchOrder");

        Result<List<PlaceOrderResult>> result = new Result<>();
        List<PlaceOrderResult> respList = new ArrayList<>();
        List<PlaceCoinOrderDTO> reqList = placeOrderRequest.getPlaceOrderDTOS();
        List<UsdkOrderDO> usdkOrderDOList = new ArrayList<>();
        if (CollectionUtils.isEmpty(reqList) || reqList.size() > Constant.BATCH_ORDER_MAX_SIZE){
            log.error("out of max order size");
            return result.error(ResultCodeEnum.BATCH_SIZE_OUT_OF_LIMIT.getCode(),ResultCodeEnum.BATCH_SIZE_OUT_OF_LIMIT.getMessage());
        }
        Map<Integer, BigDecimal> map = new HashMap<>();
        Long userId = placeOrderRequest.getUserId();
        String username = placeOrderRequest.getUserName();
        String ipAddress = placeOrderRequest.getIp();
        BigDecimal fee = placeOrderRequest.getUserLevel().getFeeRate();

        for(PlaceCoinOrderDTO placeCoinOrderDTO : reqList){
            Long orederId = BasicUtils.generateId();
            PlaceOrderResult placeOrderResult = new PlaceOrderResult();
            placeOrderResult.setExtOrderId(placeCoinOrderDTO.getExtOrderId());
            placeOrderResult.setOrderId(orederId);
            respList.add(placeOrderResult);
            long transferTime = System.currentTimeMillis();
            Map<String, Object> newMap = new HashMap<>();
            newMap.put("username", username);

            UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
            usdkOrderDTO.setId(orederId);
            usdkOrderDTO.setUserId(userId);
            usdkOrderDTO.setAssetId(Integer.valueOf(String.valueOf(placeCoinOrderDTO.getSubjectId())));
            usdkOrderDTO.setAssetName(placeCoinOrderDTO.getSubjectName());
            usdkOrderDTO.setOrderDirection(placeCoinOrderDTO.getOrderDirection());
            usdkOrderDTO.setOrderType(placeCoinOrderDTO.getOrderType());
            usdkOrderDTO.setFee(fee);
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
                Result<BigDecimal> checkPriceRes = computeAndCheckOrderPrice(usdkOrderDTO.getPrice(), usdkOrderDTO.getOrderType(), usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getAssetId());
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
                tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                        1, usdkOrderDTO.getAssetName(), username, ipAddress, usdkOrderDTO.getTotalAmount(), transferTime, 2, usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getUserId(), 1);
            } else if (usdkOrderDTO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()){
                tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                        1, usdkOrderDTO.getAssetName(), username, ipAddress, usdkOrderDTO.getTotalAmount(), transferTime, 3, usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getUserId(), 2);
            }
        }

        //插入委托订单记录
        int ret = batchInsertUsdkOrder(usdkOrderDOList);
        profiler.complelete("insertUsdkOrder");
        if (ret <= 0){
            log.error("insert contractOrder failed");
            throw new RuntimeException("insert contractOrder failed");
        }

        //非强平单委托冻结
        if (map.size() > 0){
            CoinExchangeOrderBatchLock coinExchangeOrderBatchLock = new CoinExchangeOrderBatchLock();
            List<CoinExchangeOrderBatchLock.CoinExchangeOrderLockAmount> coinExchangeOrderLockAmountList = new ArrayList<>();
            for(Integer key : map.keySet()){
                CoinExchangeOrderBatchLock.CoinExchangeOrderLockAmount coinExchangeOrderLockAmount = new CoinExchangeOrderBatchLock().new CoinExchangeOrderLockAmount();
                coinExchangeOrderLockAmount.setAssetId(key);
                coinExchangeOrderLockAmount.setOrderLockAmount(map.get(key));
                coinExchangeOrderLockAmountList.add(coinExchangeOrderLockAmount);
            }
            coinExchangeOrderBatchLock.setCoinExchangeOrderLockAmount(coinExchangeOrderLockAmountList);
            coinExchangeOrderBatchLock.setUserId(userId);
            try {
                Result<Boolean> updateLockedAmountRet = getCapitalService().batchUpdateLockedAmount(coinExchangeOrderBatchLock);
                if (!updateLockedAmountRet.getData() || !updateLockedAmountRet.isSuccess()){
                    log.error("CapitalService().batchUpdateLockedAmount failed, coinExchangeOrderBatchLock = ", coinExchangeOrderBatchLock);
                    throw new Exception("CapitalService().batchUpdateLockedAmount failed");
                }
            }catch (Exception e){
                log.error("CapitalService().batchUpdateLockedAmount exception, coinExchangeOrderBatchLock = ", coinExchangeOrderBatchLock , e);
                throw new Exception("CapitalService().batchUpdateLockedAmount exception");
            }
        }
        //批量发送mq消息一定要在事务外发，不然会出现收到下单消息，db还没有这个订单
        Runnable postTask = () -> {
            List<CoinPlaceOrderMessage> placeOrderMessages = new ArrayList<>();
            for (UsdkOrderDO usdkOrderDO : usdkOrderDOList){
                placeOrderMessages.add(toCoinPlaceOrderMessage(usdkOrderDO));
            }
            Boolean sendRet = rocketMqManager.batchSendMessage(TopicConstants.TRD_COIN_ORDER, x -> x.getSubjectId() + "", x -> x.getOrderId()+"", placeOrderMessages);
            if (!sendRet){
                log.error("Send RocketMQ Message Failed ");
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
        return placeOrderMessage;
    }

    private Result<BigDecimal> computeAndCheckOrderPrice(BigDecimal orderPrice, int orderType, int orderDirection, int assetId){

        Integer scale = AssetTypeEnum.getUsdkPricePrecisionByAssetId(assetId);

        if (orderType == PriceTypeEnum.RIVAL_PRICE.getCode()){

            List<CompetitorsPriceDTO> competitorsPriceList = realTimeEntrustManager.getUsdtCompetitorsPriceOrder();
            Integer opDirection = ASK.getCode() + BID.getCode() - orderDirection;

            CompetitorsPriceDTO competitorsPriceDTO = competitorsPriceList.stream().filter(competitorsPrice-> competitorsPrice.getOrderDirection() == opDirection &&
                    competitorsPrice.getId() == assetId).findFirst().orElse(null);

            if (null == competitorsPriceDTO || null == competitorsPriceDTO.getPrice() || BigDecimal.ZERO.compareTo(competitorsPriceDTO.getPrice()) >= 0){
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
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByUserIdAndId(userId, orderId);
        if (Objects.isNull(usdkOrderDO)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        if (usdkOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
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
            return Result.fail(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        if (orderIds.size() > Constant.BATCH_ORDER_MAX_SIZE){
            return result.error(ResultCodeEnum.BATCH_SIZE_OUT_OF_LIMIT.getCode(),ResultCodeEnum.BATCH_SIZE_OUT_OF_LIMIT.getMessage());
        }
        List<UsdkOrderDO> usdkOrderDOList = usdkOrderMapper.listByUserIdAndIds(userId, orderIds);
        if (CollectionUtils.isEmpty(usdkOrderDOList) || usdkOrderDOList.size() != orderIds.size()) {
            return Result.fail(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        for (UsdkOrderDO usdkOrderDO : usdkOrderDOList){
            if (usdkOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
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
        Long transferTime = System.currentTimeMillis();
        if (ret > 0){
            Integer orderDirection = usdkOrderDO.getOrderDirection();
            Integer assetId = 0;
            BigDecimal unlockAmount ;
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                assetId = AssetTypeEnum.BTC.getCode();
                BigDecimal price = usdkOrderDO.getPrice();
                unlockAmount = unfilledAmount.multiply(price);
            }else {
                assetId = usdkOrderDO.getAssetId();
                unlockAmount = unfilledAmount;
            }
            //解冻Coin钱包账户
            try{
                Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(usdkOrderDO.getUserId(),assetId,unlockAmount.negate().toString(), 0L);
                if (!updateLockedAmountRet){
                    log.error("cancelOrder getCapitalService().updateLockedAmount failed usdkOrderDO:{}", usdkOrderDO);
                    throw new BizException(BIZ_ERROR.getCode(),"cancelOrder getCapitalService().updateLockedAmount failed");
                }
            }catch (Exception e){
                log.error("Asset RPC Error!, cancelOrder getCapitalService().updateLockedAmount exception usdkOrderDO:{}", usdkOrderDO, e);
                throw new BizException(BIZ_ERROR.getCode(),"cancelOrder getCapitalService().updateLockedAmount exception");
            }

            JSONObject jsonObject = JSONObject.parseObject(usdkOrderDO.getOrderContext());
            String username = "";
            if (jsonObject != null && !jsonObject.isEmpty()) {
                username = jsonObject.get("username") == null ? "" : jsonObject.get("username").toString();
            }
            String ipAddress = "";
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    1, usdkOrderDO.getAssetName(), username, ipAddress, unfilledAmount, System.currentTimeMillis(), 1,  usdkOrderDO.getOrderDirection(), usdkOrderDO.getUserId(), 1);
            sendCanceledMessage(usdkOrderDO, unfilledAmount);
            resultCode = ResultCode.success();
        }else {
            return ResultCode.error(ResultCodeEnum.BIZ_ERROR.getCode(), "usdkOrderMapper.updateByOpLock failed" + usdkOrderDO.getId());
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
            log.error("Send RocketMQ Message Failed ");
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
                log.error("failed to send cancel usdk mq, {}", userId);
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
        Profiler profiler =  null == ThreadContextUtil.getPrifiler() ? new Profiler("UsdkOrderManager.updateOrderByMatch", usdkMatchedOrderDTO.getId().toString()) : ThreadContextUtil.getPrifiler();
        if (usdkMatchedOrderDTO == null) {
            log.error(ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "illegal usdkMatchedOrderDTO" + usdkMatchedOrderDTO);
        }
        Long transferTime = System.currentTimeMillis();

        UsdkOrderDO askUsdkOrder = usdkOrderMapper.selectByUserIdAndId(usdkMatchedOrderDTO.getAskUserId(), usdkMatchedOrderDTO.getAskOrderId());
        UsdkOrderDO bidUsdkOrder = usdkOrderMapper.selectByUserIdAndId(usdkMatchedOrderDTO.getBidUserId(), usdkMatchedOrderDTO.getBidOrderId());
        profiler.complelete("select order");
        if (null == askUsdkOrder ) {
            return ResultCode.error(ILLEGAL_PARAM.getCode(), "null askOrder!!! ");
        }
        if (null == bidUsdkOrder) {
            return ResultCode.error(ILLEGAL_PARAM.getCode(), "null bidOrder!!! ");
        }
        List<UsdkOrderDO> usdkOrderDOS = Arrays.asList(askUsdkOrder, bidUsdkOrder);
        usdkOrderDOS.sort((a, b) -> a.getId().compareTo(b.getId()));

        BigDecimal filledAmount = new BigDecimal(usdkMatchedOrderDTO.getFilledAmount());
        if (BasicUtils.gt(filledAmount, askUsdkOrder.getUnfilledAmount())){
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "askOrder unfilledAmount not enough. order="+askUsdkOrder);
        }
        if (BasicUtils.gt(filledAmount, bidUsdkOrder.getUnfilledAmount())){
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "bidOrder unfilledAmount not enough. order="+bidUsdkOrder);
        }

        BigDecimal filledPrice = new BigDecimal(usdkMatchedOrderDTO.getFilledPrice());

        for (UsdkOrderDO usdkOrderDO : usdkOrderDOS) {
            int updateAskOrderRet = doUpdateUsdkOrder(usdkOrderDO.getUserId(), usdkOrderDO.getId(),  filledAmount, filledPrice, new Date(transferTime));
            if (updateAskOrderRet <= 0) {
                throw new BizException(ResultCodeEnum.BIZ_ERROR.getCode(), "update askOrder failed, order=" + usdkOrderDO);
            }
        }
        profiler.complelete("update usdt order");



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
        BalanceTransferDTO balanceTransferDTO = new BalanceTransferDTO();
        balanceTransferDTO.setAskUserId(askUsdkOrder.getUserId());
        balanceTransferDTO.setBidUserId(bidUsdkOrder.getUserId());
        if (askUsdkOrder.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode())){
            balanceTransferDTO.setAskTotalUsdk("0");
            balanceTransferDTO.setAskLockedAsset("0");
            balanceTransferDTO.setAskTotalAsset("0");
        }else {
            balanceTransferDTO.setAskTotalUsdk(addAskTotalBTC.toString());
            balanceTransferDTO.setAskLockedAsset(addLockedAsset.toString());
            balanceTransferDTO.setAskTotalAsset(addTotalAsset.toString());
        }
        if (bidUsdkOrder.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode())){
            balanceTransferDTO.setBidTotalAsset("0");
            balanceTransferDTO.setBidTotalUsdk("0");
            balanceTransferDTO.setBidLockedUsdk("0");
        }else {
            balanceTransferDTO.setBidTotalAsset(addBidTotalAsset.toString());
            balanceTransferDTO.setBidTotalUsdk(addTotalBTC.toString());
            balanceTransferDTO.setBidLockedUsdk(addLockedBTC.toString());
        }
        balanceTransferDTO.setAssetId(usdkMatchedOrderDTO.getAssetId());
        boolean updateRet = false;
        try {
            updateRet = getCapitalService().updateBalance(balanceTransferDTO);
            profiler.complelete("updateBalance");
        }catch (Exception e){
            log.error("Asset RPC Error!, getCapitalService().updateBalance exception, balanceTransferDTO:{}", balanceTransferDTO, e);
            throw new BizException(BIZ_ERROR.getCode(), "getCapitalService().updateBalance exception, balanceTransferDTO:{}" + balanceTransferDTO);
        }
        if (!updateRet) {
            log.error("getCapitalService().updateBalance failed, balanceTransferDTO:{}", balanceTransferDTO);
            throw new BizException(BIZ_ERROR.getCode(), "getCapitalService().updateBalance failed, balanceTransferDTO:{}" + balanceTransferDTO);
        }
        UsdkMatchedOrderDO askMatchRecordDO = com.fota.trade.common.BeanUtils.extractUsdtRecord(usdkMatchedOrderDTO, OrderDirectionEnum.ASK.getCode());
        UsdkMatchedOrderDO bidMatchRecordDO = com.fota.trade.common.BeanUtils.extractUsdtRecord(usdkMatchedOrderDTO, OrderDirectionEnum.BID.getCode());
        // 保存订单数据到数据库
        try {
            int ret = usdkMatchedOrder.insert(Arrays.asList(askMatchRecordDO, bidMatchRecordDO));
            profiler.complelete("insert match record");
            if (ret < 2){
                throw new RuntimeException("usdkMatchedOrder.insert failed{}");
            }
        } catch (Exception e) {
            throw new RuntimeException("usdkMatchedOrder.insert exception{}",e);
        }

        long matchId = usdkMatchedOrderDTO.getId();
        Runnable runnable = () -> {
            Map<String, Object> askOrderContext = new HashMap<>();
            Map<String, Object> bidOrderContext = new HashMap<>();
            if (askUsdkOrder.getOrderContext() != null){
                askOrderContext  = JSON.parseObject(askUsdkOrder.getOrderContext());
            }
            if (bidUsdkOrder.getOrderContext() != null){
                bidOrderContext  = JSON.parseObject(bidUsdkOrder.getOrderContext());
            }

            postProcessOrder(askUsdkOrder, filledAmount, matchId);
            postProcessOrder(bidUsdkOrder, filledAmount, matchId);

        };
        ThreadContextUtil.setPostTask(runnable);

        return ResultCode.success();
    }

    private void postProcessOrder(UsdkOrderDO usdkOrderDO, BigDecimal filledAmount, long matchId) {
        Map<String, Object> context = BasicUtils.exeWhitoutError(() -> JSON.parseObject(usdkOrderDO.getOrderContext()));
        String userName = null == context || null == context.get("username") ? "": String.valueOf(context.get("username"));
        int dir = ContractUtils.toDirection(usdkOrderDO.getOrderDirection());
        usdkOrderDO.fillAmount(filledAmount);
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                1, usdkOrderDO.getAssetName(), userName,filledAmount, System.currentTimeMillis(), 4,  usdkOrderDO.getOrderDirection(), usdkOrderDO.getUserId(), 1);

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

        rocketMqManager.sendMessage(TRD_COIN_DEAL, usdkOrderDO.getAssetId()+"", matchId + "_" + usdkOrderDO.getId(), coinDealedMessage);
    }


    private int doUpdateUsdkOrder(long userId, long id, BigDecimal filledAmount, BigDecimal filledPrice, Date gmtModified) {
        return usdkOrderMapper.updateByFilledAmount(userId, id, filledAmount, filledPrice, gmtModified);
    }

}



