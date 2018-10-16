package com.fota.trade.service.impl;

import com.fota.common.Page;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.DealManager;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.service.ContractOrderService;
import com.fota.trade.service.internal.BlackListService;
import com.fota.trade.util.DateUtil;
import com.fota.trade.util.PriceUtil;
import com.fota.trade.util.Profiler;
import com.fota.trade.util.ThreadContextUtil;
import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

import static com.fota.trade.common.ResultCodeEnum.SYSTEM_ERROR;

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

    @Resource
    private DealManager dealManager;

    @Autowired
    private BlackListService blackListService;

    @Override
    public com.fota.common.Page<ContractOrderDTO> listContractOrderByQuery(BaseQuery contractOrderQueryDTO) {
        com.fota.common.Page<ContractOrderDTO> contractOrderDTOPageRet = new com.fota.common.Page<>();
        if (null == contractOrderQueryDTO || contractOrderQueryDTO.getUserId() == null){
            return null;
        }

        if (blackListService.contains(contractOrderQueryDTO.getUserId())) {
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
        contractOrderQueryDTO.setStartRow((contractOrderQueryDTO.getPageNo() - 1) * contractOrderQueryDTO.getPageSize());
        contractOrderQueryDTO.setEndRow(contractOrderQueryDTO.getPageSize());

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
    public Integer countContractOrderByQuery4Recovery(BaseQuery contractOrderQuery) {
        Map<String, Object> paramMap = null;
        Integer total = null;
        try {
            paramMap = ParamUtil.objectToMap(contractOrderQuery);
            paramMap.put("contractId", contractOrderQuery.getSourceId());
            total = contractOrderMapper.countByQuery(paramMap);
        } catch (Exception e) {
            log.error("contractOrderMapper.countContractOrderByQuery4Recovery({})", contractOrderQuery, e);
        }
        return total;
    }

    @Override
    public Page<ContractOrderDTO> listContractOrderByQuery4Recovery(BaseQuery contractOrderQuery) {
        com.fota.common.Page<ContractOrderDTO> contractOrderDTOPageRet = new com.fota.common.Page<>();
        if (null == contractOrderQuery){
            return null;
        }
        com.fota.common.Page<ContractOrderDTO> contractOrderDTOPage = new com.fota.common.Page<>();
        if (contractOrderQuery.getPageNo() <= 0) {
            contractOrderQuery.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        contractOrderDTOPage.setPageNo(contractOrderQuery.getPageNo());
        if (contractOrderQuery.getPageSize() <= 0
                || contractOrderQuery.getPageSize() > 1000) {
            contractOrderQuery.setPageSize(1000);

        }
        contractOrderDTOPage.setPageNo(contractOrderQuery.getPageNo());
        contractOrderDTOPage.setPageSize(contractOrderQuery.getPageSize());
        contractOrderQuery.setStartRow((contractOrderQuery.getPageNo() - 1) * contractOrderQuery.getPageSize());
        contractOrderQuery.setEndRow(contractOrderQuery.getPageSize());
        Map<String, Object> paramMap = null;
        List<ContractOrderDO> contractOrderDOList = null;
        List<com.fota.trade.domain.ContractOrderDTO> list = new ArrayList<>();
        try {
            paramMap = ParamUtil.objectToMap(contractOrderQuery);
            paramMap.put("contractId", contractOrderQuery.getSourceId());
            contractOrderDOList = contractOrderMapper.listByQuery4Recovery(paramMap);
            if (contractOrderDOList != null && contractOrderDOList.size() > 0) {
                for (ContractOrderDO contractOrderDO : contractOrderDOList) {
                    list.add(BeanUtils.copy(contractOrderDO));
                }
            }
        } catch (Exception e) {
            log.error("contractOrderMapper.listByQuery({})", contractOrderQuery, e);
            return contractOrderDTOPageRet;
        }
        contractOrderDTOPage.setData(list);
        return contractOrderDTOPage;
    }

    /**
     * @param contractOrderQuery
     * * @荆轲
     * @return
     */
    @Override
    public List<ContractOrderDTO> getAllContractOrder(BaseQuery contractOrderQuery) {
        Map<String, Object> paramMap = null;
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
        com.fota.common.Result<Long> result = orderReturnId(contractOrderDTO, userInfoMap);
        ResultCode resultCode = new ResultCode();
        resultCode.setCode(result.getCode());
        resultCode.setMessage(result.getMessage());
        return resultCode;
    }

    @Override
    public com.fota.common.Result<Long> orderReturnId(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap) {
        com.fota.common.Result<Long> result = new com.fota.common.Result<Long>();
        Profiler profiler = new Profiler("ContractOrderManager.placeOrder");
        ThreadContextUtil.setPrifiler(profiler);
        try {
            result = contractOrderManager.placeOrder(contractOrderDTO, userInfoMap);
            if (result.isSuccess()) {
                tradeLog.info("下单@@@" + contractOrderDTO);
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
                log.error("place order failed, code={}, msg={}", bizException.getCode(), bizException.getMessage());
                result.setCode(bizException.getCode());
                result.setMessage(bizException.getMessage());
                result.setData(0L);
                return result;
            }else {
                log.error("Contract order() failed", e);
            }
        }finally {
            profiler.log();
            ThreadContextUtil.clear();
        }
        result.setCode(ResultCodeEnum.ORDER_FAILED.getCode());
        result.setMessage(ResultCodeEnum.ORDER_FAILED.getMessage());
        result.setData(0L);
        return result;
    }

    @Override
    public ResultCode order(ContractOrderDTO contractOrderDTO) {
        return null;
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
    public ResultCode cancelOrder(long l, long l1) {
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

    @Override
    public ResultCode cancelAllOrder(long l) {
        return null;
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

    @Override
    public ResultCode cancelOrderByOrderType(long l, int i) {
        return null;
    }

    @Override
    public ResultCode cancelOrderByContractId(long l) {
        return null;
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

        if (blackListService.contains(userId)) {
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
                    tempTarget.setAskCloseType(temp.getAskCloseType().intValue());
                    tempTarget.setAskOrderId(temp.getAskOrderId());
                    tempTarget.setAskOrderPrice(temp.getAskOrderPrice()==null?"0":temp.getAskOrderPrice().toString());
                    tempTarget.setAskUserId(temp.getAskUserId());
                    tempTarget.setBidCloseType(temp.getBidCloseType().intValue());
                    tempTarget.setBidOrderId(temp.getBidOrderId());
                    tempTarget.setBidOrderPrice(Objects.isNull(temp.getBidOrderPrice()) ? "0" : temp.getBidOrderPrice().toString());
                    tempTarget.setBidUserId(temp.getBidUserId());
                    //tempTarget.setContractId(temp.getC)
                    tempTarget.setContractName(temp.getContractName());
                    tempTarget.setContractMatchedOrderId(temp.getId());
                    tempTarget.setAskFee(String.valueOf(temp.getAskFee()));
                    tempTarget.setBidFee(String.valueOf(temp.getBidFee()));
                    tempTarget.setFilledAmount(temp.getFilledAmount());
                    tempTarget.setFilledDate(temp.getGmtCreate());
                    tempTarget.setFilledPrice(temp.getFilledPrice());
                    tempTarget.setMatchType(temp.getMatchType().intValue());
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
            ContractOrderDO contractOrderDO = contractOrderMapper.selectByIdAndUserId(orderId, userId);
            if (contractOrderDO != null){
                return BeanUtils.copy(contractOrderDO);
            }
            return new ContractOrderDTO();
        }catch (Exception e){
            log.error("contractOrderMapper.selectByIdAndUserId failed{}", orderId);
            throw new RuntimeException("contractOrderMapper.selectByIdAndUserId failed", e);
        }
    }


    private void updateContractAccount(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO contractMatchedOrderDTO) {
    }

    private void buildPosition(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO matchedOrderDTO) {
    }

    private int updateUserPosition(UserPositionDO userPositionDO, BigDecimal oldTotalPrice, BigDecimal addedTotalPrice, long newTotalAmount) {
        return 0;
    }


    //todo 合约账户amoutn: + (oldPositionAmount - 当前持仓)*合约价格 - 手续费
    //todo 合约账户冻结：解冻委托价*合约份数 + 手续费
    private int updateBalance(ContractOrderDO contractOrderDO,
                              long oldPositionAmount,
                              long newPositionAmount,
                              ContractMatchedOrderDTO matchedOrderDTO){
        return 0;
    }

    /**
     * 如果撮合的量等于unfilled的量，则更新状态为已成
     * 如果撮合的量小于unfilled的量并且状态为已报，增更新状态为部成，
     * 更新unfilledAmount为减去成交量后的值
     * @param contractOrderDO
     * @param filledAmount
     * @return
     */
    private int updateSingleOrderByFilledAmount(ContractOrderDO contractOrderDO, long filledAmount, String filledPrice) {

        /*if (contractOrderDO.getUnfilledAmount() == filledAmount) {
            contractOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
        } else if (contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode()) {
            contractOrderDO.setStatus(OrderStatusEnum.PART_MATCH.getCode());
        }*/
        //contractOrderDO.setUnfilledAmount(contractOrderDO.getUnfilledAmount() - filledAmount);
        int ret = -1;
        try {
            BigDecimal averagePrice = PriceUtil.getAveragePrice(contractOrderDO.getAveragePrice(),
                    contractOrderDO.getTotalAmount().subtract(contractOrderDO.getUnfilledAmount()),
                    new BigDecimal(filledAmount),
                    new BigDecimal(filledPrice));
            ret = contractOrderMapper.updateByFilledAmount(contractOrderDO.getId(), contractOrderDO.getStatus(), filledAmount, averagePrice);
        }catch (Exception e){
            log.error("失败({})", contractOrderDO, e);
        }
        return ret;
    }
}
