package com.fota.trade.service.impl;

import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.manager.ContractLeverManager;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.ContractOrderService;
import com.fota.trade.util.DateUtil;
import com.fota.trade.util.PriceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.*;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午11:16 2018/7/5
 * @Modified:
 */
public class ContractOrderServiceImpl implements
        ContractOrderService {

    private static final Logger log = LoggerFactory.getLogger(ContractOrderServiceImpl.class);
    private static final Logger tradeLog = LoggerFactory.getLogger("trade");

    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Autowired
    private ContractOrderManager contractOrderManager;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private ContractLeverManager contractLeverManager;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Autowired
    private AssetService assetService;
    private AssetService getAssetService() { return assetService; }

    @Autowired
    private ContractService contractService;
    private ContractService getContractService() {
        return contractService;
    }


    @Override
    public com.fota.common.Page<ContractOrderDTO> listContractOrderByQuery(BaseQuery contractOrderQueryDTO) {
        com.fota.common.Page<ContractOrderDTO> contractOrderDTOPageRet = new com.fota.common.Page<>();
        if (null == contractOrderQueryDTO){
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
        List<ContractOrderDTO> contractOrderDTOList = null;
//        try {
//            contractOrderDTOList = BeanUtils.copyList(contractOrderDOList, ContractOrderDTO.class);
//        } catch (Exception e) {
//            log.error("bean copy exception", e);
//            return contractOrderDTOPageRet
//        }
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
        ResultCode resultCode = new ResultCode();
        try {
            com.fota.common.Result<Long> result = contractOrderManager.placeOrder(contractOrderDTO, userInfoMap);
            resultCode.setCode(result.getCode());
            resultCode.setMessage(result.getMessage());
            if (resultCode.isSuccess()) {
                tradeLog.info("下单@@@" + contractOrderDTO);
                redisManager.contractOrderSaveForMatch(contractOrderDTO);
            }
            return resultCode;
        }catch (Exception e){
            log.error("Contract order() failed", e);
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
        }
        resultCode = ResultCode.error(ResultCodeEnum.ORDER_FAILED.getCode(), ResultCodeEnum.ORDER_FAILED.getMessage());
        return resultCode;
    }

    @Override
    public com.fota.common.Result<Long> orderReturnId(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap) {
        com.fota.common.Result<Long> result = new com.fota.common.Result<Long>();
        try {
            result = contractOrderManager.placeOrder(contractOrderDTO, userInfoMap);
            return result;
        }catch (Exception e){
            log.error("Contract order() failed", e);
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                result.setCode(businessException.getCode());
                result.setMessage(businessException.getMessage());
                result.setData(0L);
                return result;
            }
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
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.updateOrderByMatch(contractMatchedOrderDTO);
            return resultCode;
        } catch (Exception e) {
            log.error("updateOrderByMatch error", e);
        }
        resultCode.setCode(ResultCodeEnum.DATABASE_EXCEPTION.getCode());
        resultCode.setMessage(ResultCodeEnum.DATABASE_EXCEPTION.getMessage());
        return resultCode;
    }

    /**
     * 获取昨天六点到今天六点的平台手续费
     */
    @Override
    public BigDecimal getTodayFee() {
        Date date=new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 18);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date endDate = calendar.getTime();
        calendar.add(Calendar.DATE,-1);
        Date startDate = calendar.getTime();
        BigDecimal totalFee = BigDecimal.ZERO;
        try {
            totalFee = contractMatchedOrderMapper.getAllFee(startDate, endDate);
            return totalFee;
        }catch (Exception e){
            log.error("getTodayFee failed",e);
        }
        return totalFee;
    }

    @Override
    public BigDecimal getFeeByDate(Date startDate, Date endDate) {
        BigDecimal totalFee = BigDecimal.ZERO;
        try {
            totalFee = contractMatchedOrderMapper.getAllFee(startDate, endDate);
            return totalFee;
        }catch (Exception e){
            log.error("getTodayFee failed",e);
        }
        return totalFee;
    }

    @Override
    public ContractMatchedOrderTradeDTOPage getContractMacthRecord(Long userId, List<Long> contractIds, Integer pageNo, Integer pageSize, Long startTime, Long endTime) {
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

        ContractMatchedOrderTradeDTOPage contractMatchedOrderTradeDTOPage = new ContractMatchedOrderTradeDTOPage();
        contractMatchedOrderTradeDTOPage.setPageNo(pageNo);
        contractMatchedOrderTradeDTOPage.setPageSize(pageSize);

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
                    tempTarget.setAskOrderPrice(temp.getAskOrderPrice().toString());
                    tempTarget.setAskUserId(temp.getAskUserId());
                    tempTarget.setBidCloseType(temp.getBidCloseType().intValue());
                    tempTarget.setBidOrderId(temp.getBidOrderId());
                    tempTarget.setBidOrderPrice(temp.getBidOrderPrice().toString());
                    tempTarget.setBidUserId(temp.getBidUserId());
                    //tempTarget.setContractId(temp.getC)
                    tempTarget.setContractName(temp.getContractName());
                    tempTarget.setContractMatchedOrderId(temp.getId());
                    tempTarget.setFee(temp.getFee().toString());
                    tempTarget.setFilledAmount(temp.getFilledAmount());
                    tempTarget.setFilledDate(temp.getGmtCreate());
                    tempTarget.setFilledPrice(temp.getFilledPrice());
                    tempTarget.setMatchType(temp.getMatchType().intValue());
                    //BeanUtil.fieldCopy(temp, tempTarget);
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
            return BeanUtils.copy(contractOrderDO);
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
                    new BigDecimal(contractOrderDO.getTotalAmount()).subtract(new BigDecimal(contractOrderDO.getUnfilledAmount())),
                    new BigDecimal(filledAmount),
                    new BigDecimal(filledPrice));
            ret = contractOrderMapper.updateByFilledAmount(contractOrderDO.getId(), contractOrderDO.getStatus(), filledAmount, averagePrice);
        }catch (Exception e){
            log.error("失败({})", contractOrderDO, e);
        }
        return ret;
    }
}
