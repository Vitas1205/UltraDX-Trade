package com.fota.trade.service.impl;

import com.fota.asset.domain.BalanceTransferDTO;
import com.fota.asset.service.CapitalService;
import com.fota.client.common.*;

import com.fota.thrift.ThriftJ;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.Data;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午3:22 2018/7/6
 * @Modified:
 */
@Service
@Data
public class UsdkOrderServiceImpl implements com.fota.trade.service.UsdkOrderService.Iface {

    private static final Logger log = LoggerFactory.getLogger(UsdkOrderServiceImpl.class);

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private ThriftJ thriftJ;
    @Value("${fota.asset.server.thrift.port}")
    private int thriftPort;
    @PostConstruct
    public void init() {
        thriftJ.initService("FOTA-ASSET", thriftPort);
    }
    private CapitalService.Client getService() {
        CapitalService.Client serviceClient =
                thriftJ.getServiceClient("FOTA-ASSET")
                        .iface(CapitalService.Client.class, "capitalService");
        return serviceClient;
    }


    @Override
    public UsdkOrderDTOPage listUsdkOrderByQuery(UsdkOrderQuery usdkOrderQuery) {
        UsdkOrderDTOPage usdkOrderDTOPage = new UsdkOrderDTOPage();
        if (usdkOrderQuery == null || usdkOrderQuery.getUserId() <= 0) {
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
        int total = 0;
        try {
            total = usdkOrderMapper.countByQuery(ParamUtil.objectToMap(usdkOrderQuery));
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
            usdkOrderDOList = usdkOrderMapper.listByQuery(ParamUtil.objectToMap(usdkOrderQuery));
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
    public ResultCode order(UsdkOrderDTO usdkOrderDTO) {
        try {
            return BeanUtils.copy(usdkOrderManager.placeOrder(BeanUtils.copy(usdkOrderDTO)));
        }catch (Exception e){
            log.error("USDK order() failed", e);
        }
        ResultCode resultCode = new ResultCode();
        resultCode.setCode(ResultCodeEnum.DATABASE_EXCEPTION.getCode());
        resultCode.setMessage(ResultCodeEnum.DATABASE_EXCEPTION.getMessage());
        return resultCode;
    }

    @Override
    public ResultCode cancelOrder(long userId, long orderId) {
        try {
            return BeanUtils.copy(usdkOrderManager.cancelOrder(userId, orderId));
        }catch (Exception e){
            log.error("USDK cancelOrder() failed", e);
        }
        ResultCode resultCode = new ResultCode();
        resultCode.setCode(ResultCodeEnum.DATABASE_EXCEPTION.getCode());
        resultCode.setMessage(ResultCodeEnum.DATABASE_EXCEPTION.getMessage());
        return resultCode;
    }

    @Override
    public ResultCode cancelAllOrder(long userId) {
        try {
            return BeanUtils.copy(usdkOrderManager.cancelAllOrder(userId));
        }catch (Exception e){
            log.error("USDK cancelAllOrder() failed", e);
        }
        ResultCode resultCode = new ResultCode();
        resultCode.setCode(ResultCodeEnum.DATABASE_EXCEPTION.getCode());
        resultCode.setMessage(ResultCodeEnum.DATABASE_EXCEPTION.getMessage());
        return resultCode;
    }

    @Override
    @Transactional(rollbackFor = {Exception.class, RuntimeException.class, TException.class})
    public ResultCode updateOrderByMatch(UsdkMatchedOrderDTO usdkMatchedOrderDTO) throws TException {
        if (usdkMatchedOrderDTO == null) {
            return BeanUtils.copy(com.fota.client.common.ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM));
        }
        com.fota.client.domain.UsdkOrderDTO bidUsdkOrderDTO = new com.fota.client.domain.UsdkOrderDTO();
        com.fota.client.domain.UsdkOrderDTO askUsdkOrderDTO = new com.fota.client.domain.UsdkOrderDTO();
        UsdkOrderDO askUsdkOrder = usdkOrderMapper.selectByPrimaryKey(usdkMatchedOrderDTO.getAskOrderId());
        UsdkOrderDO bidUsdkOrder = usdkOrderMapper.selectByPrimaryKey(usdkMatchedOrderDTO.getBidOrderId());
        log.info("---------------"+usdkMatchedOrderDTO.toString());
        log.info("---------------"+askUsdkOrder.toString());
        log.info("---------------"+bidUsdkOrder.toString());
        if (askUsdkOrder.getUnfilledAmount().compareTo(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount())) < 0
                || bidUsdkOrder.getUnfilledAmount().compareTo(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount())) < 0){
            return BeanUtils.copy(com.fota.client.common.ResultCode.error(ResultCodeEnum.ORDER_UNFILLEDAMOUNT_NOT_ENOUGHT));
        }
        if (askUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()
                && bidUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            return BeanUtils.copy(com.fota.client.common.ResultCode.error(ResultCodeEnum.ASK_AND_BID_ILLEGAL));
        }
        if (askUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            return BeanUtils.copy(com.fota.client.common.ResultCode.error(ResultCodeEnum.ASK_ILLEGAL));
        }
        if (bidUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            return BeanUtils.copy(com.fota.client.common.ResultCode.error(ResultCodeEnum.BID_ILLEGAL));
        }
        BigDecimal filledAmount = new BigDecimal(usdkMatchedOrderDTO.getFilledAmount());
        BigDecimal filledPrice = new BigDecimal(usdkMatchedOrderDTO.getFilledPrice());
        int updateAskOrderRet = updateSingleOrderByFilledAmount(askUsdkOrder, filledAmount);
        if (updateAskOrderRet <= 0){
            throw new RuntimeException("ask:"+ResultCodeEnum.UPDATE_USDKORDER_FAILED.getMessage());
        }
        int updateBIdOrderRet = updateSingleOrderByFilledAmount(bidUsdkOrder, filledAmount);
        if (updateBIdOrderRet <= 0){
            throw new RuntimeException("bid:"+ResultCodeEnum.UPDATE_USDKORDER_FAILED.getMessage());
        }
        // todo 买币 bid +totalAsset = filledAmount - filledAmount * feeRate
        // todo 买币 bid -totalUsdk = filledAmount * filledPrice
        // todo 买币 bid -lockedUsdk = filledAmount * bidOrderPrice
        // todo 卖币 ask +totalUsdk = filledAmount * filledPrice - filledAmount * filledPrice * feeRate
        // todo 卖币 ask -lockedAsset = filledAmount
        // todo 卖币 ask -totalAsset = filledAmount
        BigDecimal addBidTotalAsset = filledAmount.subtract(filledAmount.multiply(Constant.FEE_RATE));
        BigDecimal addTotalUsdk = filledAmount.multiply(filledPrice);
        BigDecimal addLockedUsdk = filledAmount.multiply(new BigDecimal(usdkMatchedOrderDTO.getBidOrderPrice()));
        BigDecimal addAskTotalUsdk = filledAmount.multiply(filledPrice).multiply(new BigDecimal("1").subtract(Constant.FEE_RATE));
        BigDecimal addLockedAsset = filledAmount;
        BigDecimal addTotalAsset = filledAmount;

        BalanceTransferDTO balanceTransferDTO = new BalanceTransferDTO();

        balanceTransferDTO.setBidTotalAsset(addBidTotalAsset.toString());
        balanceTransferDTO.setBidTotalUsdk(addTotalUsdk.toString());
        balanceTransferDTO.setBidLockedUsdk(addLockedUsdk.toString());
        balanceTransferDTO.setAskTotalUsdk(addAskTotalUsdk.toString());
        balanceTransferDTO.setAskLockedAsset(addLockedAsset.toString());
        balanceTransferDTO.setAskTotalAsset(addTotalAsset.toString());
        balanceTransferDTO.setAssetId(usdkMatchedOrderDTO.getAssetId());
        balanceTransferDTO.setAskUserId(askUsdkOrder.getUserId());
        balanceTransferDTO.setBidUserId(bidUsdkOrder.getUserId());

        boolean updateRet = false;
        updateRet = getService().updateBalance(balanceTransferDTO);
        if (!updateRet) {
            throw new RuntimeException("update balance failed");
        }

        //todo 存redis，发消息？
        org.springframework.beans.BeanUtils.copyProperties(askUsdkOrder, askUsdkOrderDTO);
        org.springframework.beans.BeanUtils.copyProperties(bidUsdkOrder, bidUsdkOrderDTO);
        askUsdkOrderDTO.setCompleteAmount(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()));
        bidUsdkOrderDTO.setCompleteAmount(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()));
        redisManager.usdkOrderSave(askUsdkOrderDTO);
        redisManager.usdkOrderSave(bidUsdkOrderDTO);
        return BeanUtils.copy(com.fota.client.common.ResultCode.success());
    }

    /**
     * 如果撮合的量等于unfilled的量，则更新状态为已成
     * 如果撮合的量小于unfilled的量并且状态为已报，增更新状态为部成，
     * 更新unfilledAmount为减去成交量后的值
     * @param usdkOrderDO
     * @param filledAmount
     * @return
     */
    private int updateSingleOrderByFilledAmount(UsdkOrderDO usdkOrderDO, BigDecimal filledAmount) {
        if (usdkOrderDO.getUnfilledAmount().compareTo(filledAmount) == 0) {
            usdkOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
        } else if (usdkOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode()) {
            usdkOrderDO.setStatus(OrderStatusEnum.PART_MATCH.getCode());
        }
        usdkOrderDO.setUnfilledAmount(usdkOrderDO.getUnfilledAmount().subtract(filledAmount));
        return usdkOrderMapper.updateByPrimaryKeyAndOpLock(usdkOrderDO);
    }
}
