package com.fota.trade.service.impl;

import com.fota.common.Page;
import com.fota.common.Result;
import com.fota.common.enums.FotaApplicationEnum;
import com.fota.trade.client.*;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;


import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.DealManager;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.service.ContractOrderService;
import com.fota.trade.service.internal.MarketAccountListService;
import com.fota.trade.util.ConvertUtils;
import com.fota.trade.util.DateUtil;

import com.fota.trade.util.Profiler;
import com.fota.trade.util.ThreadContextUtil;
import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;

import java.util.stream.Collectors;

import static com.fota.trade.client.constants.Constants.TABLE_NUMBER;
import static com.fota.trade.common.ResultCodeEnum.DATABASE_EXCEPTION;

import static com.fota.trade.common.ResultCodeEnum.SYSTEM_ERROR;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午11:16 2018/7/5
 * @Modified:
 */
@Slf4j
public class ContractOrderServiceImpl implements ContractOrderService {

    private static final Logger tradeLog = LoggerFactory.getLogger("trade");

    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Autowired
    private ContractOrderManager contractOrderManager;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Autowired
    private DealManager dealManager;

    @Autowired
    private MarketAccountListService marketAccountListService;

    @Override
    public com.fota.common.Page<ContractOrderDTO> listContractOrderByQuery(BaseQuery contractOrderQueryDTO) {
        com.fota.common.Page<ContractOrderDTO> contractOrderDTOPageRet = new com.fota.common.Page<>();
        if (null == contractOrderQueryDTO || contractOrderQueryDTO.getUserId() == null){
            return null;
        }

        com.fota.common.Page<ContractOrderDTO> contractOrderDTOPage = new com.fota.common.Page<>();
        if (contractOrderQueryDTO.getPageNo() <= 0) {
            contractOrderQueryDTO.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        contractOrderDTOPage.setPageNo(contractOrderQueryDTO.getPageNo());
        if (contractOrderQueryDTO.getPageSize() <= 0
                || contractOrderQueryDTO.getPageSize() > Constant.DEFAULT_MAX_PAGE_SIZE) {
            contractOrderQueryDTO.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        contractOrderDTOPage.setPageNo(contractOrderQueryDTO.getPageNo());
        contractOrderDTOPage.setPageSize(contractOrderQueryDTO.getPageSize());

        Map<String, Object> paramMap = null;

        int total = 0;
        try {
            paramMap = ParamUtil.objectToMap(contractOrderQueryDTO);
            paramMap.put("contractId", contractOrderQueryDTO.getSourceId());
            total = contractOrderMapper.countByQuery(paramMap);
        } catch (Exception e) {
            log.error("contractOrderMapper.countByQuery({})", contractOrderQueryDTO, e);
            return contractOrderDTOPageRet;
        }
        contractOrderDTOPage.setTotal(total);
        if (total == 0) {
            return contractOrderDTOPageRet;
        }
        List<ContractOrderDO> contractOrderDOList = null;
        List<com.fota.trade.domain.ContractOrderDTO> list = new ArrayList<>();
        try {
            contractOrderDOList = contractOrderMapper.listByQuery(paramMap);
            if (contractOrderDOList != null && contractOrderDOList.size() > 0) {

                for (ContractOrderDO contractOrderDO : contractOrderDOList) {
                    list.add(BeanUtils.copy(contractOrderDO));
                }
            }
        } catch (Exception e) {
            log.error("contractOrderMapper.listByQuery({})", contractOrderQueryDTO, e);
            return contractOrderDTOPageRet;
        }
        contractOrderDTOPage.setData(list);
        return contractOrderDTOPage;
    }



    @Override
    public Result<RecoveryMetaData> getRecoveryMetaData() {
        Date date;
        try {
            date = contractOrderMapper.getMaxCreateTime();
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
    public Result<Page<ContractOrderDTO>> listContractOrder4Recovery(RecoveryQuery recoveryQuery) {
        List<ContractOrderDTO> contractOrderDTOS = null;
        try {
            List<ContractOrderDO> contractOrderDOS = contractOrderMapper.queryForRecovery(recoveryQuery.getTableIndex(), recoveryQuery.getMaxGmtCreate(), recoveryQuery.getStart(), recoveryQuery.getPageSize());
            if (!CollectionUtils.isEmpty(contractOrderDOS)) {
                contractOrderDTOS = contractOrderDOS.stream().map( x -> BeanUtils.copy(x)).collect(Collectors.toList());
            }
        }catch (Throwable t) {
            log.error("queryForRecovery exception, query={}", recoveryQuery, t);
            return Result.<Page<ContractOrderDTO>>create().error(com.fota.common.ResultCodeEnum.DATABASE_EXCEPTION);
        }
        Page<ContractOrderDTO> contractOrderDTOPage = new Page<>();
        contractOrderDTOPage.setPageSize(recoveryQuery.getPageSize());
        contractOrderDTOPage.setPageNo(recoveryQuery.getPageIndex());
        contractOrderDTOPage.setData(contractOrderDTOS);
        return Result.suc(contractOrderDTOPage);
    }

    /**
     * @param contractOrderQuery
     * * @荆轲
     * @return
     */
    @Override
    public List<ContractOrderDTO> getAllContractOrder(BaseQuery contractOrderQuery) {
        Map<String, Object> paramMap = null;
        if (null == contractOrderQuery.getUserId()) {
            return null;
        }

        List<com.fota.trade.domain.ContractOrderDTO> list = new ArrayList<>();
        try {
            paramMap = ParamUtil.objectToMap(contractOrderQuery);
            paramMap.put("startRow", 0);
            paramMap.put("endRow", Integer.MAX_VALUE);
            List<ContractOrderDO> contractOrderDOList = contractOrderMapper.listByQuery(paramMap);
            if (contractOrderDOList != null && contractOrderDOList.size() > 0) {
                for (ContractOrderDO contractOrderDO : contractOrderDOList) {
                    list.add(BeanUtils.copy(contractOrderDO));
                }
            }
        } catch (Exception e) {
            log.error("contractOrderMapper.listByQuery({})", contractOrderQuery, e);
        }
        return list;
    }

    @Override
    public ResultCode order(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap) {
        PlaceOrderRequest placeOrderRequest = ConvertUtils.toPlaceOrderRequest(contractOrderDTO, userInfoMap, UserLevelEnum.DEFAULT, FotaApplicationEnum.WEB);
        Result<List<PlaceOrderResult>> result = batchOrder(placeOrderRequest);

        ResultCode resultCode = new ResultCode();
        resultCode.setCode(result.getCode());
        resultCode.setMessage(result.getMessage());
        return resultCode;
    }

    @Override
    public Result<Long> orderWithEnforce(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap) {
        PlaceOrderRequest placeOrderRequest = ConvertUtils.toPlaceOrderRequest(contractOrderDTO, userInfoMap, UserLevelEnum.DEFAULT, FotaApplicationEnum.MARGIN);
        Result<List<PlaceOrderResult>> result = batchOrder(placeOrderRequest);
        if (!result.isSuccess()) {
            return Result.fail(result.getCode(), result.getMessage());
        }
        return Result.suc(result.getData().get(0).getOrderId());
    }

    @Override
    public com.fota.common.Result<Long> orderReturnId(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap) {
        PlaceOrderRequest placeOrderRequest = ConvertUtils.toPlaceOrderRequest(contractOrderDTO, userInfoMap, UserLevelEnum.FREE, FotaApplicationEnum.TRADING_API);
        Result<List<PlaceOrderResult>> result = batchOrder(placeOrderRequest);
        if (!result.isSuccess()) {
            return Result.fail(result.getCode(), result.getMessage());
        }
        return Result.suc(result.getData().get(0).getOrderId());
    }

    @Override
    public Result<List<PlaceOrderResult>> batchOrder(PlaceOrderRequest<PlaceContractOrderDTO> placeOrderRequest) {
        com.fota.common.Result<List<PlaceOrderResult>> result = new com.fota.common.Result<>();
        Profiler profiler = new Profiler("ContractOrderManager.placeOrder");
        ThreadContextUtil.setPrifiler(profiler);
        try {
            result = contractOrderManager.placeOrder(placeOrderRequest, false);
            if (result.isSuccess()) {
                profiler.setTraceId(result.getData()+"");
                //redisManager.contractOrderSaveForMatch(contractOrderDTO);
                Runnable postTask = ThreadContextUtil.getPostTask();
                if (null == postTask) {
                    log.error("null postTask");
                }else {
                    postTask.run();
                }
            }
            return result;
        }catch (Exception e){
            if (e instanceof BizException){
                BizException bizException = (BizException) e;
                log.error("place order({}) failed, code={}, msg={}", placeOrderRequest, bizException.getCode(), bizException.getMessage());
                result.fail(bizException.getCode(), bizException.getMessage());
                return result;
            }else {
                log.error("Contract order({}) failed", placeOrderRequest,  e);
            }
        }finally {
            profiler.log();
            ThreadContextUtil.clear();
        }
        result.setCode(ResultCodeEnum.ORDER_FAILED.getCode());
        result.setMessage(ResultCodeEnum.ORDER_FAILED.getMessage());
        return result;
    }

    @Override
    public ResultCode cancelOrder(long userId, long orderId, Map<String, String> userInfoMap) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.cancelOrder(userId, orderId, userInfoMap);
            return resultCode;
        }catch (Exception e){
            log.error("Contract cancelOrder() failed", e);
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
    public Result batchCancel(CancelOrderRequest cancelOrderRequest) {
        return null;
    }


    @Override
    public ResultCode cancelAllOrder(long userId, Map<String, String> userInfoMap) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.cancelAllOrder(userId, userInfoMap);
            return resultCode;
        }catch (Exception e){
            log.error("Contract cancelAllOrder() failed", e);
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


    /**
     * 撤销用户非强平单
     * * @荆轲
     * @param userId
     * @param orderTypes
     * @return
     */
    @Override
    public ResultCode cancelOrderByOrderType(long userId, List<Integer> orderTypes, Map<String, String> userInfoMap) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.cancelOrderByOrderType(userId, orderTypes, userInfoMap);
            return resultCode;
        }catch (Exception e){
            log.error("Contract cancelOrderByOrderType() failed", e);
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
        }
        return resultCode;
    }


    /**
     * 撤销该合约的所有委托订单
     ** * @王冕
     * @param contractId
     * @return
     */
    @Override
    public ResultCode cancelOrderByContractId(long contractId, Map<String, String> userInfoMap) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.cancelOrderByContractId(contractId, userInfoMap);
            return resultCode;
        }catch (Exception e){
            log.error("Contract cancelOrderByContractId() failed", e);
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
        }
        return resultCode;
    }

    @Override
    public ResultCode updateOrderByMatch(ContractMatchedOrderDTO contractMatchedOrderDTO) {
        ResultCode checkRes = contractOrderManager.checkMatchOrderDTO(contractMatchedOrderDTO);
        if (!checkRes.isSuccess()) {
            return checkRes;
        }

        String messageKey = Joiner.on("-").join(contractMatchedOrderDTO.getAskOrderId().toString(),
                contractMatchedOrderDTO.getAskOrderStatus(), contractMatchedOrderDTO.getBidOrderId(),
                contractMatchedOrderDTO.getBidOrderStatus());
        Profiler profiler = new Profiler("ContractOrderManager.updateOrderByMatch", messageKey);
        profiler.setStart(contractMatchedOrderDTO.getGmtCreate().getTime());
        profiler.complelete("receive message");
        ThreadContextUtil.setPrifiler(profiler);

        try {
            return dealManager.deal(contractMatchedOrderDTO);

        } catch (Exception e) {
            if (e instanceof BizException) {
                BizException bE = (BizException) e;
                return ResultCode.error(bE.getCode(), bE.getMessage());
            }else {
                log.error("updateOrderByMatch exception, match_order={}", contractMatchedOrderDTO, e);
                return ResultCode.error(SYSTEM_ERROR.getCode(), SYSTEM_ERROR.getMessage());
            }
        }finally {
            profiler.log();
            ThreadContextUtil.clear();
        }
    }



    /**
     * 获取昨天六点到今天六点的平台手续费
     */
    @Override
    @Deprecated
    public BigDecimal getTodayFee() {
        BigDecimal totalFee = BigDecimal.ZERO;
        return totalFee;
    }

    @Override
    @Deprecated
    public BigDecimal getFeeByDate(Date startDate, Date endDate) {
        BigDecimal totalFee = BigDecimal.ZERO;
        return totalFee;
    }


    @Override
    public ContractMatchedOrderTradeDTOPage getContractMacthRecord(Long userId, List<Long> contractIds, Integer pageNo, Integer pageSize, Long startTime, Long endTime) {
        if (pageNo == null || pageNo <= 0) {
            pageNo = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 20;
        }
        Date startTimeD = null, endTimeD = null;
        if (startTime != null && startTime != 0){
            startTimeD = DateUtil.LongTurntoDate(startTime);
        }
        if (endTime != null && endTime != 0){
            endTimeD = DateUtil.LongTurntoDate(endTime);
        }
        log.info("getListByUserId userId {} startTime {}, endTime {}", userId, startTimeD, endTimeD);

        ContractMatchedOrderTradeDTOPage contractMatchedOrderTradeDTOPage = new ContractMatchedOrderTradeDTOPage();
        contractMatchedOrderTradeDTOPage.setPageNo(pageNo);
        contractMatchedOrderTradeDTOPage.setPageSize(pageSize);

        if (marketAccountListService.contains(userId)) {
            contractMatchedOrderTradeDTOPage.setTotal(100_000);
            contractMatchedOrderTradeDTOPage.setPageSize(4);

            return contractMatchedOrderTradeDTOPage;
        }

        int count = 0;
        try {
            count = contractMatchedOrderMapper.countByUserId(userId, contractIds, startTimeD, endTimeD);
        } catch (Exception e) {
            log.error("contractMatchedOrderMapper.countByUserId({})", userId, e);
            return contractMatchedOrderTradeDTOPage;
        }
        contractMatchedOrderTradeDTOPage.setTotal(count);
        if (count == 0) {
            return contractMatchedOrderTradeDTOPage;
        }
        int startRow = (pageNo - 1) * pageSize;
        int endRow = pageSize;
        List<ContractMatchedOrderDO> contractMatchedOrders = null;
        List<ContractMatchedOrderTradeDTO> list = new ArrayList<>();
        try {
            contractMatchedOrders = contractMatchedOrderMapper.listByUserId(userId, contractIds, startRow, endRow, startTimeD, endTimeD);
            if (contractMatchedOrders != null && contractMatchedOrders.size() > 0) {
                for (ContractMatchedOrderDO temp : contractMatchedOrders) {
                    // 获取更多信息
                    ContractMatchedOrderTradeDTO tempTarget = new ContractMatchedOrderTradeDTO();
                    tempTarget.setContractMatchedOrderId(temp.getId());
                    tempTarget.setContractId(temp.getContractId());
                    tempTarget.setContractName(temp.getContractName());
                    tempTarget.setFilledAmount(temp.getFilledAmount());
                    tempTarget.setFilledDate(temp.getGmtCreate());
                    tempTarget.setFilledPrice(temp.getFilledPrice());
                    tempTarget.setMatchType(temp.getMatchType().intValue());

                    if (ASK.getCode() == temp.getOrderDirection()) {
                        tempTarget.setAskUserId(temp.getUserId());
                        tempTarget.setAskOrderId(temp.getOrderId());
                        tempTarget.setAskOrderPrice(temp.getOrderPrice()==null?"0":temp.getOrderPrice().toString());
                        tempTarget.setAskFee(String.valueOf(temp.getFee()));
                        tempTarget.setAskCloseType(temp.getCloseType());
                    }else {
                        tempTarget.setBidUserId(temp.getUserId());
                        tempTarget.setBidOrderId(temp.getOrderId());
                        tempTarget.setBidCloseType(temp.getCloseType());
                        tempTarget.setBidOrderPrice(Objects.isNull(temp.getOrderPrice()) ? "0" : temp.getOrderPrice().toString());
                        tempTarget.setBidFee(String.valueOf(temp.getFee()));
                    }

                    list.add(tempTarget);
                }
            }
        } catch (Exception e) {
            log.error("contractMatchedOrderMapper.countByUserId({})", userId, e);
            return contractMatchedOrderTradeDTOPage;
        }
        contractMatchedOrderTradeDTOPage.setData(list);
        return contractMatchedOrderTradeDTOPage;
    }

    /**
     * 根据订单id查询订单信息
     *
     * @param orderId
     * @param userId
     * @return
     */
    @Override
    public ContractOrderDTO getContractOrderById(Long orderId, Long userId) {
        try {
            ContractOrderDO contractOrderDO = contractOrderMapper.selectByIdAndUserId(userId, orderId);
            if (contractOrderDO != null){
                return BeanUtils.copy(contractOrderDO);
            }
            return new ContractOrderDTO();
        }catch (Exception e){
            log.error("contractOrderMapper.selectByIdAndUserId failed{}", orderId);
            throw new RuntimeException("contractOrderMapper.selectByIdAndUserId failed", e);
        }
    }

}
