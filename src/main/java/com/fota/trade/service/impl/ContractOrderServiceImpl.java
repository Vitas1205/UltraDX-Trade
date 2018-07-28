package com.fota.trade.service.impl;

import com.alibaba.fastjson.JSON;
import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.client.common.Page;
import com.fota.client.common.ResultCodeEnum;
import com.fota.client.domain.CompetitorsPriceDTO;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.BusinessException;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.ContractLeverManager;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.ContractOrderService;
import com.fota.trade.util.PriceUtil;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午11:16 2018/7/5
 * @Modified:
 */
public class ContractOrderServiceImpl implements ContractOrderService {

    private static final Logger log = LoggerFactory.getLogger(ContractOrderServiceImpl.class);

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
        if (contractOrderQueryDTO.getUserId() <= 0) {
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

    /*@Override
    public ResultCode order(ContractOrderDTO contractOrderDTO) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.placeOrder(BeanUtils.copy(contractOrderDTO));
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("Contract order() failed", e);
        }
        return resultCode;
    }*/

    @Override
    public ResultCode order(ContractOrderDTO contractOrderDTO) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.placeOrder(BeanUtils.copy(contractOrderDTO));
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("Contract order() failed", e);
        }
        return resultCode;
    }

    @Override
    public ResultCode cancelOrder(long userId, long orderId) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.cancelOrder(userId, orderId);
            return resultCode;
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("Contract cancelOrder() failed", e);
        }
        return resultCode;
    }

    @Override
    public ResultCode cancelAllOrder(long userId) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.cancelAllOrder(userId);
            return resultCode;
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("Contract cancelAllOrder() failed", e);
        }
        return resultCode;
    }

    @Override
    @Transactional(rollbackFor = {Exception.class, RuntimeException.class, TException.class})
    public ResultCode updateOrderByMatch(ContractMatchedOrderDTO contractMatchedOrderDTO) {
        ResultCode resultCode = new ResultCode();
        if (contractMatchedOrderDTO == null) {
            resultCode.setCode(ResultCodeEnum.ILLEGAL_PARAM.getCode());
            return resultCode;
        }
        ContractOrderDO askContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getAskOrderId());
        ContractOrderDO bidContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getBidOrderId());
        log.info("---------------"+contractMatchedOrderDTO.toString());
        log.info("---------------"+askContractOrder.toString());
        log.info("---------------"+bidContractOrder.toString());
        if (askContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0
                || bidContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0){
            return BeanUtils.copy(com.fota.client.common.ResultCode.error(ResultCodeEnum.ORDER_UNFILLEDAMOUNT_NOT_ENOUGHT));
        }
        if (askContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()
                && bidContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            return BeanUtils.copy(com.fota.client.common.ResultCode.error(ResultCodeEnum.ASK_AND_BID_ILLEGAL));
        }
        if (askContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            return BeanUtils.copy(com.fota.client.common.ResultCode.error(ResultCodeEnum.ASK_ILLEGAL));
        }
        if (bidContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            return BeanUtils.copy(com.fota.client.common.ResultCode.error(ResultCodeEnum.BID_ILLEGAL));
        }
        askContractOrder.setStatus(contractMatchedOrderDTO.getAskOrderStatus());
        bidContractOrder.setStatus(contractMatchedOrderDTO.getBidOrderStatus());
        updateContractAccount(askContractOrder, contractMatchedOrderDTO);
        updateContractAccount(bidContractOrder, contractMatchedOrderDTO);

        com.fota.client.domain.ContractOrderDTO bidContractOrderDTO = new com.fota.client.domain.ContractOrderDTO();
        com.fota.client.domain.ContractOrderDTO askContractOrderDTO = new com.fota.client.domain.ContractOrderDTO();
        org.springframework.beans.BeanUtils.copyProperties(askContractOrder, askContractOrderDTO);
        askContractOrderDTO.setContractId(askContractOrder.getContractId().intValue());
        org.springframework.beans.BeanUtils.copyProperties(bidContractOrder, bidContractOrderDTO);
        bidContractOrderDTO.setContractId(bidContractOrder.getContractId().intValue());
        askContractOrderDTO.setCompleteAmount(new BigDecimal(contractMatchedOrderDTO.getFilledAmount()));
        bidContractOrderDTO.setCompleteAmount(new BigDecimal(contractMatchedOrderDTO.getFilledAmount()));
        redisManager.contractOrderSave(askContractOrderDTO);
        redisManager.contractOrderSave(bidContractOrderDTO);
        resultCode.setCode(ResultCodeEnum.SUCCESS.getCode());
        return resultCode;
    }

    private void updateContractAccount(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO contractMatchedOrderDTO) {
        BigDecimal filledAmount = new BigDecimal(contractMatchedOrderDTO.getFilledAmount());
        BigDecimal filledPrice = new BigDecimal(contractMatchedOrderDTO.getFilledPrice());
        Long userId = contractOrderDO.getUserId();
        Long contractId = contractOrderDO.getContractId();
        UserPositionDO userPositionDO = null;
        try {
            userPositionDO = userPositionMapper.selectByUserIdAndId(userId, contractId.intValue());
        } catch (Exception e) {
            log.error("userPositionMapper.selectByUserIdAndId({}, {})", userId, contractId, e);
            return;
        }
        if (userPositionDO == null) {
            // 建仓
            buildPosition(contractOrderDO, contractMatchedOrderDTO);
            return;
        }
        long oldPositionAmount = userPositionDO.getUnfilledAmount();
        if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
            //todo 成交单和持仓是同方向
            long newTotalAmount = userPositionDO.getUnfilledAmount() + filledAmount.longValue();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
            updateUserPosition(userPositionDO, oldTotalPrice, addedTotalPrice, newTotalAmount);
            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO);
            return;
        }

        //成交单和持仓是反方向 （平仓）
        if (filledAmount.longValue() - userPositionDO.getUnfilledAmount() <= 0) {
            //todo 不改变仓位方向
            long newTotalAmount = userPositionDO.getUnfilledAmount() - filledAmount.longValue();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount).negate();
            updateUserPosition(userPositionDO, oldTotalPrice, addedTotalPrice, newTotalAmount);
            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO);
        } else {
            //todo 改变仓位方向
            long newTotalAmount = filledAmount.longValue() - userPositionDO.getUnfilledAmount();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount())).negate();
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
            userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
            updateUserPosition(userPositionDO, oldTotalPrice, addedTotalPrice, newTotalAmount);
            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO);
        }
    }

    private void buildPosition(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO matchedOrderDTO) {
        UserPositionDO newUserPositionDO = new UserPositionDO();
        newUserPositionDO.setPositionType(contractOrderDO.getOrderDirection());
        newUserPositionDO.setAveragePrice(new BigDecimal(matchedOrderDTO.getFilledPrice()));
        newUserPositionDO.setUnfilledAmount(matchedOrderDTO.getFilledAmount());
        newUserPositionDO.setStatus(1);
        newUserPositionDO.setUserId(contractOrderDO.getUserId());
        newUserPositionDO.setContractName(contractOrderDO.getContractName());
        newUserPositionDO.setContractId(contractOrderDO.getContractId());
        //todo fixme 根据接口获取杠杆
        Integer lever = new Integer(contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(),contractOrderDO.getContractId()));
        newUserPositionDO.setLever(lever);
        try {
            userPositionMapper.insert(newUserPositionDO);
        } catch (Exception e) {
            log.error("userPositionMapper.insert({})", newUserPositionDO, e);
        }
        updateBalance(contractOrderDO, 0L, matchedOrderDTO.getFilledAmount(), matchedOrderDTO);
    }

    private int updateUserPosition(UserPositionDO userPositionDO, BigDecimal oldTotalPrice, BigDecimal addedTotalPrice, long newTotalAmount) {
        BigDecimal newTotalPrice = oldTotalPrice.add(addedTotalPrice);
        if (newTotalAmount != 0){
            BigDecimal newAvaeragePrice = newTotalPrice.divide(new BigDecimal(newTotalAmount), 8,BigDecimal.ROUND_DOWN);
            userPositionDO.setAveragePrice(newAvaeragePrice);
        }
        userPositionDO.setUnfilledAmount(newTotalAmount);

        int updateRet = 0;
        try {
            updateRet = userPositionMapper.updateByPrimaryKey(userPositionDO);
        } catch (Exception e) {
            log.error("userPositionMapper.insert({})", userPositionDO, e);
        }
        return updateRet;
    }


    //todo 合约账户amoutn: + (oldPositionAmount - 当前持仓)*合约价格 - 手续费
    //todo 合约账户冻结：解冻委托价*合约份数 + 手续费
    private int updateBalance(ContractOrderDO contractOrderDO,
                              long oldPositionAmount,
                              long newPositionAmount,
                              ContractMatchedOrderDTO matchedOrderDTO){
        long filledAmount = matchedOrderDTO.getFilledAmount();
        BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(),contractOrderDO.getContractId()));
        BigDecimal filledPrice = new BigDecimal(matchedOrderDTO.getFilledPrice());
        BigDecimal fee = contractOrderDO.getFee();
        BigDecimal actualFee = filledPrice.multiply(new BigDecimal(filledAmount)).multiply(fee).multiply(Constant.CONTRACT_SIZE);
        BigDecimal addedTotalAmount = new BigDecimal(oldPositionAmount - newPositionAmount)
                .multiply(filledPrice)
                .multiply(Constant.CONTRACT_SIZE)
                .divide(lever, 8, BigDecimal.ROUND_DOWN)
                .subtract(actualFee);
        /*BigDecimal entrustFee = contractOrderDO.getPrice().multiply(new BigDecimal(filledAmount)).multiply(fee).multiply(new BigDecimal(0.01));
        BigDecimal addedTotalLocked = new BigDecimal(filledAmount)
                .multiply(contractOrderDO.getPrice())
                .multiply(new BigDecimal(0.01))
                .multiply(new BigDecimal(0.1))
                .add(entrustFee).negate();*/
        Object competiorsPriceObj = redisManager.get(Constant.COMPETITOR_PRICE_KEY);
        List<CompetitorsPriceDTO> competitorsPriceList = JSON.parseArray(competiorsPriceObj.toString(),CompetitorsPriceDTO.class);
        if (competitorsPriceList == null || competitorsPriceList.size() == 0){
            log.error("get competitors price list failed");
        }
        UserContractDTO userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
        BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
        BigDecimal totalLockAmount = null;
        updateSingleOrderByFilledAmount(contractOrderDO, matchedOrderDTO.getFilledAmount(), matchedOrderDTO.getFilledPrice());
        try {
            totalLockAmount = contractOrderManager.getTotalLockAmount(contractOrderDO.getUserId());
        } catch (Exception e) {
            log.error("get totalLockAmount failed",e);
        }
        BigDecimal addedTotalLocked = totalLockAmount.subtract(lockedAmount);
        //todo 更新余额
        try {
            getContractService().updateContractBalance(contractOrderDO.getUserId(),
                    addedTotalAmount.toString(),
                    addedTotalLocked.toString());
        } catch (Exception e) {
            log.error("update contract balance failed",e);
        }
        return 1;
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
            log.info("打印的内容----------------------"+contractOrderDO);
            BigDecimal averagePrice = PriceUtil.getAveragePrice(contractOrderDO.getAveragePrice(),
                    new BigDecimal(contractOrderDO.getTotalAmount()),
                    new BigDecimal(filledAmount),
                    new BigDecimal(filledPrice));
            ret = contractOrderMapper.updateByFilledAmount(contractOrderDO.getId(), contractOrderDO.getStatus(), filledAmount, averagePrice);
        }catch (Exception e){
            log.error("失败({})", contractOrderDO, e);
        }
        return ret;
    }
}
