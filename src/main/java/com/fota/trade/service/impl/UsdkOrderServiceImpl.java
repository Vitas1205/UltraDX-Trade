package com.fota.trade.service.impl;

import com.fota.asset.domain.BalanceTransferDTO;
import com.fota.asset.service.CapitalService;
import com.fota.common.Page;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.UsdkMatchedOrderMapper;
import com.fota.trade.mapper.UsdkOrderMapper;
import com.fota.trade.service.UsdkOrderService;
import com.fota.trade.util.DateUtil;
import com.fota.trade.util.ThreadContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.fota.trade.common.ResultCodeEnum.SYSTEM_ERROR;


/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午3:22 2018/7/6
 * @Modified:
 */
public class UsdkOrderServiceImpl implements UsdkOrderService {

    private static final Logger log = LoggerFactory.getLogger(UsdkOrderServiceImpl.class);

    private static final Logger tradeLog = LoggerFactory.getLogger("trade");

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private UsdkMatchedOrderMapper usdkMatchedOrderMapper;

    @Autowired
    private CapitalService capitalService;
    private CapitalService getService() {
        return capitalService;
    }

    ExecutorService executorService = Executors.newWorkStealingPool();


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
        usdkOrderQuery.setStartRow((usdkOrderQuery.getPageNo() - 1) * usdkOrderQuery.getPageSize());
        usdkOrderQuery.setEndRow(usdkOrderQuery.getPageSize());
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
    public Integer countUsdkOrderByQuery4Recovery(BaseQuery usdkOrderQuery) {
        Map<String, Object> paramMap = null;
        Integer total = 0;
        try {
            paramMap = ParamUtil.objectToMap(usdkOrderQuery);
            paramMap.put("assetId", usdkOrderQuery.getSourceId());
            total = usdkOrderMapper.countByQuery(paramMap);
        } catch (Exception e) {
            log.error("usdkOrderMapper.countByQuery4Recovery({})", usdkOrderQuery, e);
        }
        return total;
    }

    @Override
    public Page<UsdkOrderDTO> listUsdkOrderByQuery4Recovery(BaseQuery usdkOrderQuery) {
        return null;
    }

    @Override
    public ResultCode order(UsdkOrderDTO usdkOrderDTO, Map<String, String> userInfoMap) {
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
                tradeLog.info("下单@@@" + usdkOrderDTO);
            }
            return result;
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                log.error("usdk order fialed, code={}, message={}", businessException.getCode(), businessException.getMessage());
                result.setCode(businessException.getCode());
                result.setMessage(businessException.getMessage());
                return result;
            }
            log.error("USDK order() failed", e);
        }finally {
            ThreadContextUtil.clear();
        }
        result.setCode(ResultCodeEnum.ORDER_FAILED.getCode());
        result.setMessage(ResultCodeEnum.ORDER_FAILED.getMessage());
        return result;
    }

    @Override
    public ResultCode order(UsdkOrderDTO usdkOrderDTO) {
        return null;
    }


    @Override
    public ResultCode cancelOrder(long userId, long orderId, Map<String, String> userInfoMap) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = usdkOrderManager.cancelOrder(userId, orderId, userInfoMap);
            if (resultCode.isSuccess()) {
                tradeLog.info("撤销@@@" + userId+ "@@@" + orderId);
            }
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
    public ResultCode cancelOrder(long l, long l1) {
        return null;
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
    public ResultCode cancelAllOrder(long l) {
        return null;
    }

    @Override
    public ResultCode updateOrderByMatch(UsdkMatchedOrderDTO usdkMatchedOrderDTO) {
        ResultCode resultCode = new ResultCode();
        try {

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
            ThreadContextUtil.clear();
        }
    }

    @Override
    public UsdkMatchedOrderTradeDTOPage getUsdkMatchRecord(Long userId, List<Long> assetIds, Integer pageNo, Integer pageSize, Long startTime, Long endTime) {
        if (pageNo <= 0) {
            pageNo = 1;
        }
        if (pageSize <= 0) {
            pageSize = 20;
        }
        Date startTimeD = null, endTimeD = null;
        if (startTime != null){
            startTimeD = DateUtil.LongTurntoDate(startTime);
        }
        if (endTime != null){
            endTimeD = DateUtil.LongTurntoDate(endTime);
        }
        log.info("getListByUserId userId {} startTime {}, endTime {}", userId, startTimeD, endTimeD);

        UsdkMatchedOrderTradeDTOPage usdkMatchedOrderTradeDTOPage = new UsdkMatchedOrderTradeDTOPage();
        usdkMatchedOrderTradeDTOPage.setPageNo(pageNo);
        usdkMatchedOrderTradeDTOPage.setPageSize(pageSize);

        int count = 0;
        try {
            count = usdkMatchedOrderMapper.countByUserId(userId, assetIds, startTimeD, endTimeD);
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
            usdkMatchedOrders = usdkMatchedOrderMapper.listByUserId(userId, assetIds, startRow, endRow, startTimeD, endTimeD);
            if (null != usdkMatchedOrders && usdkMatchedOrders.size() > 0){
                for (UsdkMatchedOrderDO temp : usdkMatchedOrders){
                    UsdkMatchedOrderTradeDTO tempTarget = new UsdkMatchedOrderTradeDTO();
                    tempTarget.setAskCloseType(temp.getAskCloseType().intValue());
                    tempTarget.setAskOrderId(temp.getAskOrderId());
                    tempTarget.setAskOrderPrice(temp.getAskOrderPrice()==null?"0":temp.getAskOrderPrice().toString());
                    tempTarget.setAskUserId(temp.getAskUserId());
                    tempTarget.setBidCloseType(temp.getBidCloseType().intValue());
                    tempTarget.setBidOrderId(temp.getBidOrderId());
                    tempTarget.setBidOrderPrice(temp.getBidOrderPrice()==null?"0":temp.getBidOrderPrice().toString());
                    tempTarget.setBidUserId(temp.getBidUserId());
                    tempTarget.setAssetName(temp.getAssetName());
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
    public Long getLatestMatchedUsdk (Integer type) {
        return null;
    }

    @Override
    public List<UsdkMatchedOrderTradeDTO> getLatestUsdkMatchedList (Long id ,Integer assetId) {
        return null;
    }

    @Override
    public List<ContractMatchedOrderTradeDTO> getLatestContractMatchedList (Long id ,Long contractId){
        return null;
    }

}
