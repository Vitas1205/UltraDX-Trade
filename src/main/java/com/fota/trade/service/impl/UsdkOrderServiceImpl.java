package com.fota.trade.service.impl;

import com.alibaba.fastjson.JSON;
import com.fota.common.Page;
import com.fota.common.Result;
import com.fota.common.enums.FotaApplicationEnum;
import com.fota.common.manager.BrokerTradingPairManager;
import com.fota.trade.client.*;
import com.fota.trade.client.constants.Constants;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderTypeEnum;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.sharding.UsdkMatchedOrderMapper;
import com.fota.trade.mapper.sharding.UsdkOrderMapper;
import com.fota.trade.service.UsdkOrderService;
import com.fota.trade.service.internal.MarketAccountListService;
import com.fota.trade.util.DateUtil;
import com.fota.trade.util.Profiler;
import com.fota.trade.util.ThreadContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.fota.trade.client.constants.Constants.TABLE_NUMBER;
import static com.fota.trade.common.ResultCodeEnum.DATABASE_EXCEPTION;
import static com.fota.trade.common.ResultCodeEnum.SYSTEM_ERROR;


/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午3:22 2018/7/6
 * @Modified:
 */
public class UsdkOrderServiceImpl implements UsdkOrderService {

    private static final Logger log = LoggerFactory.getLogger(UsdkOrderServiceImpl.class);

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Autowired
    private UsdkMatchedOrderMapper usdkMatchedOrderMapper;

    @Autowired
    private MarketAccountListService marketAccountListService;

    @Autowired
    private BrokerTradingPairManager brokerTradingPairManager;

    private ExecutorService executorService = Executors.newWorkStealingPool();


    @Override
    public Page<UsdkOrderDTO> listUsdkOrderByQuery(BaseQuery usdkOrderQuery) {
        Page<UsdkOrderDTO> usdkOrderDTOPage = new Page<UsdkOrderDTO>();
        if (usdkOrderQuery == null) {
            return usdkOrderDTOPage;
        }
        if (usdkOrderQuery.getPageNo() <= 0) {
            usdkOrderQuery.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        usdkOrderDTOPage.setPageNo(usdkOrderQuery.getPageNo());
        if (usdkOrderQuery.getPageSize() <= 0
                || usdkOrderQuery.getPageSize() > Constant.DEFAULT_MAX_PAGE_SIZE) {
            usdkOrderQuery.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        usdkOrderDTOPage.setPageNo(usdkOrderQuery.getPageNo());
        usdkOrderDTOPage.setPageSize(usdkOrderQuery.getPageSize());
        Map<String, Object> paramMap = null;
        int total = 0;
        try {
            paramMap = ParamUtil.objectToMap(usdkOrderQuery);
            paramMap.put("assetId", usdkOrderQuery.getSourceId());
            total = usdkOrderMapper.countByQuery(paramMap);
        } catch (Exception e) {
            log.error("usdkOrderMapper.countByQuery({})", usdkOrderQuery, e);
            return usdkOrderDTOPage;
        }
        usdkOrderDTOPage.setTotal(total);
        if (total == 0) {
            return usdkOrderDTOPage;
        }
        List<UsdkOrderDO> usdkOrderDOList = null;
        try {
            usdkOrderDOList = usdkOrderMapper.listByQuery(paramMap);
        } catch (Exception e) {
            log.error("usdkOrderMapper.listByQuery({})", usdkOrderQuery, e);
            return usdkOrderDTOPage;
        }
        List<UsdkOrderDTO> list = new ArrayList<>();
        if (usdkOrderDOList != null && usdkOrderDOList.size() > 0) {
            for (UsdkOrderDO usdkOrderDO : usdkOrderDOList) {
                list.add(BeanUtils.copy(usdkOrderDO));
            }
        }
        usdkOrderDTOPage.setData(list);
        return usdkOrderDTOPage;
    }



    @Override
    public Result<RecoveryMetaData> getRecoveryMetaData() {
        Date date;
        try {
            date = usdkOrderMapper.getMaxCreateTime();
        }catch (Throwable t) {
            log.error("getMaxCreateTime exception", t);
            return Result.fail(DATABASE_EXCEPTION.getCode(), DATABASE_EXCEPTION.getMessage());
        }
        RecoveryMetaData recoveryMetaData = new RecoveryMetaData();
        recoveryMetaData.setMaxGmtCreate(date);
        recoveryMetaData.setTableNumber(TABLE_NUMBER);
        return Result.suc(recoveryMetaData);
    }

    @Override
    public Result<Page<UsdkOrderDTO>> listUsdtOrder4Recovery(RecoveryQuery recoveryQuery) {
        List<UsdkOrderDTO> usdkOrderDTOS = null;
        try {
            List<UsdkOrderDO> usdkOrderDOS = usdkOrderMapper.queryForRecovery(recoveryQuery.getTableIndex(), recoveryQuery.getMaxGmtCreate(), recoveryQuery.getStart(), recoveryQuery.getPageSize());
            if (!CollectionUtils.isEmpty(usdkOrderDOS)) {
                usdkOrderDTOS = usdkOrderDOS.stream().map( x -> BeanUtils.copy(x)).collect(Collectors.toList());
            }
        }catch (Throwable t) {
            log.error("queryForRecovery exception, query={}", recoveryQuery, t);
            return Result.<Page<UsdkOrderDTO>>create().error(com.fota.common.ResultCodeEnum.DATABASE_EXCEPTION);
        }
        Page<UsdkOrderDTO> usdtPage = new Page<>();
        usdtPage.setPageSize(recoveryQuery.getPageSize());
        usdtPage.setPageNo(recoveryQuery.getPageIndex());
        usdtPage.setData(usdkOrderDTOS);
        return Result.suc(usdtPage);
    }

    @Override
    public ResultCode order(UsdkOrderDTO usdkOrderDTO, Map<String, String> userInfoMap) {

        log.error("order:{}, userInfoMap:{}", usdkOrderDTO, userInfoMap);

        com.fota.common.Result<Long> result = orderReturnId(usdkOrderDTO, userInfoMap);
        if (result.isSuccess()) {
            return ResultCode.success();
        }
        return ResultCode.error(result.getCode(), result.getMessage());
    }

    @Override
    public com.fota.common.Result<Long> orderReturnId(UsdkOrderDTO usdkOrderDTO, Map<String, String> userInfoMap) {
        com.fota.common.Result<Long> result = new com.fota.common.Result<Long>();
        try {
            result = usdkOrderManager.placeOrder(usdkOrderDTO, userInfoMap);
            if (result.isSuccess()) {
                Runnable postTask = ThreadContextUtil.getPostTask();
                if (null != postTask) {
                    executorService.submit(postTask);
                }
//                tradeLog.info("下单@@@" + usdkOrderDTO);
            }
            return result;
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                result.setCode(businessException.getCode());
                result.setMessage(businessException.getMessage());
                return result;
            }
            log.error("USDK order() failed", e);
        }finally {
            Profiler profiler = ThreadContextUtil.getProfiler();
            if (null != profiler) {
                profiler.log();
            }
            ThreadContextUtil.clear();
        }
        result.setCode(ResultCodeEnum.ORDER_FAILED.getCode());
        result.setMessage(ResultCodeEnum.ORDER_FAILED.getMessage());
        return result;
    }

    @Override
    public Result<List<PlaceOrderResult>> batchOrder(PlaceOrderRequest<PlaceCoinOrderDTO> placeOrderRequest) {
        Result<List<PlaceOrderResult>> result = new Result<>();
        if (placeOrderRequest.getCaller() == null){
            placeOrderRequest.setCaller(FotaApplicationEnum.TRADE);
        }
        List<PlaceCoinOrderDTO> reqList = placeOrderRequest.getPlaceOrderDTOS();
        Result<Long> result1;
        for (PlaceCoinOrderDTO placeCoinOrderDTO : reqList){
            if (placeCoinOrderDTO.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode())){
                return result.error(ResultCodeEnum.ORDER_TYPE_ERROR.getCode(), ResultCodeEnum.ORDER_TYPE_ERROR.getMessage());
            }
        }
        try{
            result = usdkOrderManager.batchOrder(placeOrderRequest);
            if (result.isSuccess()) {
                Runnable postTask = ThreadContextUtil.getPostTask();
                if (null != postTask) {
                    executorService.submit(postTask);
                }
            }
        }catch (Exception e){
            if (e instanceof BusinessException) {
                BusinessException bE = (BusinessException)e;
                return result.error(bE.getCode(), bE.getMessage());
            }
            log.error("batchOrder exception, placeOrderRequest = ", placeOrderRequest, e);
            return result.error(ResultCodeEnum.ORDER_FAILED.getCode(),ResultCodeEnum.ORDER_FAILED.getMessage());
        }
        return result;
    }

    @Override
    public Result batchCancel(CancelOrderRequest cancelOrderRequest) {
        Result result = new Result();
        Long userId = cancelOrderRequest.getUserId();
        List<Long> orderIds = cancelOrderRequest.getOrderIds();
        try {
            result = usdkOrderManager.batchCancelOrder(userId, orderIds);
            return result;
        }catch (Exception e){
            log.error("USDK batchCancel() failed", e);
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                result.setCode(businessException.getCode());
                result.setMessage(businessException.getMessage());
                return result;
            }
        }
        result = Result.fail(ResultCodeEnum.BATCH_CANCEL_ORDER_FAILED.getCode(), ResultCodeEnum.BATCH_CANCEL_ORDER_FAILED.getMessage());
        return result;
    }



    @Override
    public ResultCode cancelOrder(long userId, long orderId, Map<String, String> userInfoMap) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = usdkOrderManager.cancelOrder(userId, orderId, userInfoMap);
            return resultCode;
        }catch (Exception e){
            log.error("USDK cancelOrder() failed", e);
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
        }
        resultCode = ResultCode.error(ResultCodeEnum.CANCEL_ORDER_FAILED.getCode(), ResultCodeEnum.CANCEL_ORDER_FAILED.getMessage());
        return resultCode;
    }


    @Override
    public ResultCode cancelAllOrder(long userId, Map<String, String> userInfoMap) {
        ResultCode resultCode = new ResultCode();
        try {
            return usdkOrderManager.cancelAllOrder(userId, userInfoMap);
        }catch (Exception e){
            log.error("USDK cancelAllOrder() failed", e);
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
        }
        resultCode = ResultCode.error(ResultCodeEnum.CANCEL_ALL_ORDER_FAILED.getCode(), ResultCodeEnum.CANCEL_ALL_ORDER_FAILED.getMessage());
        return resultCode;
    }


    @Override
    public ResultCode updateOrderByMatch(UsdkMatchedOrderDTO usdkMatchedOrderDTO) {
        ResultCode resultCode = new ResultCode();
        Profiler profiler = new Profiler("UsdkOrderManager.updateOrderByMatch", usdkMatchedOrderDTO.getId().toString());
        profiler.setStart(usdkMatchedOrderDTO.getGmtCreate().getTime());
        try {
            profiler.complelete("receive message");
            ThreadContextUtil.setProfiler(profiler);
            resultCode = usdkOrderManager.updateOrderByMatch(usdkMatchedOrderDTO);
            if (resultCode.isSuccess()) {
                Runnable postTask = ThreadContextUtil.getPostTask();
                if (null != postTask) {
                    executorService.submit(postTask);
                }
            }
            return resultCode;
        }catch (Exception e){
            if (e instanceof BizException) {
                BizException bE = (BizException) e;
                return ResultCode.error(bE.getCode(), bE.getMessage());
            }else {
                log.error("usdk match exception, match_order={}", usdkMatchedOrderDTO, e);
                return ResultCode.error(SYSTEM_ERROR.getCode(), SYSTEM_ERROR.getMessage());
            }
        }finally {
            profiler.log();

            ThreadContextUtil.clear();
        }
    }

    @Override
    public UsdkMatchedOrderTradeDTOPage getUsdkMatchRecord(Long userId, List<Long> assetIds, Integer pageNo, Integer pageSize, Long startTime, Long endTime) {
        // todo 如果是做市账号，不显示成交的数据
        boolean isMarket = marketAccountListService.contains(userId);
        if (isMarket) {
            return new UsdkMatchedOrderTradeDTOPage();
        }

        if (pageNo <= 0) {
            pageNo = 1;
        }
        if (pageSize <= 0) {
            pageSize = 20;
        }
        // todo 因为涉及到需要和数据库里面的date进行比较，
        log.info("getListByUserId userId {} startTime {}, endTime {}", userId, startTime, endTime);
        if (startTime != null) {
            if (startTime.toString().length() == 13) {
                startTime = startTime/1000;
            }
        }
        if (endTime != null) {
            if (endTime.toString().length() == 13) {
                endTime = endTime/1000;
            }
        }

        UsdkMatchedOrderTradeDTOPage usdkMatchedOrderTradeDTOPage = new UsdkMatchedOrderTradeDTOPage();
        usdkMatchedOrderTradeDTOPage.setPageNo(pageNo);
        usdkMatchedOrderTradeDTOPage.setPageSize(pageSize);

        int count = 0;
        try {
            count = usdkMatchedOrderMapper.countByUserId(userId, assetIds, startTime, endTime);
        } catch (Exception e) {
            log.error("usdkMatchedOrderMapper.countByUserId({})", userId, e);
            return usdkMatchedOrderTradeDTOPage;
        }
        usdkMatchedOrderTradeDTOPage.setTotal(count);
        if (count == 0){
            return usdkMatchedOrderTradeDTOPage;
        }
        int startRow = (pageNo - 1) * pageSize;
        int endRow = pageSize;

        List<UsdkMatchedOrderDO> usdkMatchedOrders = null;
        List<UsdkMatchedOrderTradeDTO> list = new ArrayList<>();

        try {
            usdkMatchedOrders = usdkMatchedOrderMapper.listByUserId(userId, assetIds, startRow, endRow, startTime, endTime);
            if (null != usdkMatchedOrders && usdkMatchedOrders.size() > 0){
                for (UsdkMatchedOrderDO temp : usdkMatchedOrders){
                    UsdkMatchedOrderTradeDTO tempTarget = new UsdkMatchedOrderTradeDTO();
                    BigDecimal feeRate;
                    if (isMarket) {
                        feeRate = BigDecimal.ZERO;
                    } else {
                        feeRate = getFeeRateByOrderId(temp.getUserId(), temp.getOrderId());

                        //feeRate = getFeeRateByBrokerId(temp.getBrokerId(), temp.getAssetId());
                    }
                    if (OrderDirectionEnum.ASK.getCode() == temp.getOrderDirection()){
                        tempTarget.setAskOrderId(temp.getOrderId());
                        tempTarget.setAskOrderPrice(temp.getOrderPrice()==null?"0":temp.getOrderPrice().toString());
                        tempTarget.setAskUserId(temp.getUserId());
                        tempTarget.setBidUserId(temp.getMatchUserId());
                        tempTarget.setFee(feeRate.multiply(temp.getFilledPrice()).multiply(temp.getFilledAmount())
                                .setScale(Constants.feePrecision, BigDecimal.ROUND_DOWN)
                                .toPlainString());
                        log.info("usdkMatchedOrderMapper error:{}", JSON.toJSONString(temp));
                        if (temp.getAssetName().split("/").length > 1){
                            tempTarget.setFeeAssetUnit(temp.getAssetName().split("/")[1]);
                        }

                    }else {
                        tempTarget.setBidOrderId(temp.getOrderId());
                        tempTarget.setBidOrderPrice(temp.getOrderPrice()==null?"0":temp.getOrderPrice().toString());
                        tempTarget.setBidUserId(temp.getUserId());
                        tempTarget.setAskUserId(temp.getMatchUserId());
                        tempTarget.setFee(feeRate.multiply(temp.getFilledAmount())
                                .setScale(Constants.feePrecision, BigDecimal.ROUND_DOWN)
                                .toPlainString());
                        if (temp.getAssetName().split("/").length > 1){
                            tempTarget.setFeeAssetUnit(temp.getAssetName().split("/")[0]);
                        }
                    }

                    tempTarget.setAssetName(temp.getAssetName());
                    tempTarget.setAssetId((long)temp.getAssetId());
                    tempTarget.setUsdkMatchedOrderId(temp.getId());
                    //tempTarget.setFee(temp.getFee().toString());
                    tempTarget.setFilledAmount(temp.getFilledAmount());
                    tempTarget.setFilledDate(temp.getGmtCreate());
                    tempTarget.setFilledPrice(temp.getFilledPrice());
                    tempTarget.setMatchType(temp.getMatchType().intValue());
                    list.add(tempTarget);
                }
            }
        } catch (Exception e){
            log.error("usdkMatchedOrderMapper.countByUserId({})", userId, e);
            return usdkMatchedOrderTradeDTOPage;
        }
        usdkMatchedOrderTradeDTOPage.setData(list);
        return usdkMatchedOrderTradeDTOPage;
    }

    private BigDecimal getFeeRateByOrderId(Long userId, Long orderId) {
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByUserIdAndId(userId, orderId);
        if(null != usdkOrderDO) {
            BigDecimal feeRate = usdkOrderDO.getFee();
            if(null == feeRate) {
                return BigDecimal.ZERO;
            } else {
                return feeRate;
            }
        }
        else {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal getFeeRateByBrokerId(Long brokerId, Integer assetId){
        return brokerTradingPairManager.getTradingPairById(assetId.longValue()).getFeeRate();
    }

    /**
     * 根据订单id查询订单信息
     *
     * @param orderId
     * @param userId
     * @return
     */
    @Override
    public UsdkOrderDTO getUsdkOrderById(Long orderId, Long userId) {
        try {
            UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByUserIdAndId(userId, orderId);
            if (usdkOrderDO != null){
                return BeanUtils.copy(usdkOrderDO);
            }
            return new UsdkOrderDTO();
        }catch (Exception e){
            log.error("usdkOrderMapper.selectByIdAndUserId failed{}", orderId);
            throw new RuntimeException("usdkOrderMapper.selectByIdAndUserId failed", e);
        }
    }

    @Override
    public List<Long> getTradeMatchUserId(UsdkMatchedOrderTradeQueryDTO queryDTO) {
        return null;
    }


}
