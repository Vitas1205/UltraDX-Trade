package com.fota.trade.service.impl;

import com.fota.asset.domain.BalanceTransferDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.CapitalService;
import com.fota.client.common.*;
import com.fota.client.domain.UsdkMatchedOrderDTO;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.client.domain.query.UsdkOrderQuery;
import com.fota.client.service.UsdkOrderService;
import com.fota.thrift.ThriftJ;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.Data;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;


/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午3:22 2018/7/6
 * @Modified:
 */
@Service
@Data
public class UsdkOrderServiceImpl implements UsdkOrderService {

    private static final Logger log = LoggerFactory.getLogger(UsdkOrderServiceImpl.class);

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

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
    public Result<Page<UsdkOrderDTO>> listUsdkOrderByQuery(UsdkOrderQuery usdkOrderQuery) {
        Result<Page<UsdkOrderDTO>> result = Result.create();
        if (usdkOrderQuery == null || usdkOrderQuery.getUserId() == null || usdkOrderQuery.getUserId() <= 0) {
            return result.error(ResultCodeEnum.ILLEGAL_PARAM);
        }
        Page<UsdkOrderDTO> usdkOrderDTOPage = new Page<>();
        if (usdkOrderQuery.getPageNo() == null || usdkOrderQuery.getPageNo() <= 0) {
            usdkOrderQuery.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        usdkOrderDTOPage.setPageNo(usdkOrderQuery.getPageNo());
        if (usdkOrderQuery.getPageSize() == null
                || usdkOrderQuery.getPageSize() <= 0
                || usdkOrderQuery.getPageSize() > Constant.DEFAULT_MAX_PAGE_SIZE) {
            usdkOrderQuery.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        usdkOrderDTOPage.setPageNo(usdkOrderQuery.getPageNo());
        usdkOrderDTOPage.setPageSize(usdkOrderQuery.getPageSize());
        int total = 0;
        try {
            total = usdkOrderMapper.countByQuery(ParamUtil.objectToMap(usdkOrderQuery));
        } catch (Exception e) {
            log.error("usdkOrderMapper.countByQuery({})", usdkOrderQuery, e);
            return result.error(ResultCodeEnum.DATABASE_EXCEPTION);
        }
        usdkOrderDTOPage.setTotal(total);
        if (total == 0) {
            return result.success(usdkOrderDTOPage);
        }
        List<UsdkOrderDO> usdkOrderDOList = null;
        try {
            usdkOrderDOList = usdkOrderMapper.listByQuery(ParamUtil.objectToMap(usdkOrderQuery));
        } catch (Exception e) {
            log.error("usdkOrderMapper.listByQuery({})", usdkOrderQuery, e);
            return result.error(ResultCodeEnum.DATABASE_EXCEPTION);
        }
        List<UsdkOrderDTO> usdkOrderDTOList = null;
        try {
            usdkOrderDTOList = BeanUtils.copyList(usdkOrderDOList, UsdkOrderDTO.class);
        } catch (Exception e) {
            log.error("bean copy exception", e);
            return result.error(ResultCodeEnum.BEAN_COPY_EXCEPTION);
        }
        usdkOrderDTOPage.setData(usdkOrderDTOList);
        return result.success(usdkOrderDTOPage);
    }

    @Override
    public ResultCode order(UsdkOrderDTO usdkOrderDTO) {
        try {
            ResultCode resultCode = usdkOrderManager.placeOrder(usdkOrderDTO);
            return resultCode;
        }catch (Exception e){
            log.error("USDK order() failed", e);
        }
        return null;
    }

    @Override
    public ResultCode cancelOrder(Long userId, Long orderId) {
        try {
            ResultCode resultCode = usdkOrderManager.cancelOrder(userId, orderId);
            return resultCode;
        }catch (Exception e){
            log.error("USDK cancelOrder() failed", e);
        }
        return null;
    }

    @Override
    public ResultCode cancelAllOrder(Long userId) {
        try {
            ResultCode resultCode = usdkOrderManager.cancelAllOrder(userId);
            return resultCode;
        }catch (Exception e){
            log.error("USDK cancelAllOrder() failed", e);
        }
        return null;
    }

    @Override
    public ResultCode updateOrderByMatch(UsdkMatchedOrderDTO usdkMatchedOrderDTO) {
        if (usdkMatchedOrderDTO == null) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM);
        }
        UsdkOrderDTO askUsdkOrder = usdkMatchedOrderDTO.getAskUsdkOrder();
        UsdkOrderDTO bidUsdkOrder = usdkMatchedOrderDTO.getBidUsdkOrder();
        BigDecimal filledAmount = usdkMatchedOrderDTO.getFilledAmount();
        BigDecimal filledPrice = usdkMatchedOrderDTO.getFilledPrice();
        updateSingleOrderByFilledAmount(askUsdkOrder, filledAmount);
        updateSingleOrderByFilledAmount(bidUsdkOrder, filledAmount);

        // todo 买币 bid +totalAsset = filledAmount - filledAmount * feeRate
        // todo 买币 bid -totalUsdk = filledAmount * filledPrice
        // todo 买币 bid -lockedUsdk = filledAmount * bidOrderPrice
        // todo 卖币 ask +totalUsdk = filledAmount * filledPrice - filledAmount * filledPrice * feeRate
        // todo 卖币 ask -lockedAsset = filledAmount
        // todo 卖币 ask -totalAsset = filledAmount

        BigDecimal addBidTotalAsset = filledAmount.subtract(filledAmount.multiply(Constant.FEE_RATE));
        BigDecimal addTotalUsdk = filledAmount.multiply(filledPrice).negate();
        BigDecimal addLockedUsdk = filledAmount.multiply(usdkMatchedOrderDTO.getBidOrderPrice());
        BigDecimal addAskTotalUsdk = filledAmount.multiply(filledPrice).multiply(new BigDecimal("1").subtract(Constant.FEE_RATE));
        BigDecimal addLockedAsset = filledAmount.negate();
        BigDecimal addTotalAsset = filledAmount.negate();

        BalanceTransferDTO balanceTransferDTO = new BalanceTransferDTO();

        balanceTransferDTO.setBidTotalAsset(addBidTotalAsset.toString());
        balanceTransferDTO.setBidTotalUsdk(addTotalUsdk.toString());
        balanceTransferDTO.setBidLockedUsdk(addLockedUsdk.toString());
        balanceTransferDTO.setAskTotalUsdk(addAskTotalUsdk.toString());
        balanceTransferDTO.setAskLockedAsset(addLockedAsset.toString());
        balanceTransferDTO.setAskTotalAsset(addTotalAsset.toString());
        balanceTransferDTO.setAssetId(usdkMatchedOrderDTO.getAssetId());
        balanceTransferDTO.setAskUserId(usdkMatchedOrderDTO.getAskUsdkOrder().getUserId());
        balanceTransferDTO.setBidUserId(usdkMatchedOrderDTO.getBidUsdkOrder().getUserId());

        boolean updateRet = false;
        try {

            updateRet = getService().updateBalance(balanceTransferDTO);
        } catch (TException e) {
            log.error("capitalService.updateBalance({})", balanceTransferDTO, e);
            return ResultCode.error(ResultCodeEnum.SERVICE_EXCEPTION);
        }
        if (!updateRet) {
            return ResultCode.error(ResultCodeEnum.SERVICE_FAILED);
        }

        //todo 存redis，发消息？

        return ResultCode.success();
    }

    /**
     * 如果撮合的量等于unfilled的量，则更新状态为已成
     * 如果撮合的量小于unfilled的量并且状态为已报，增更新状态为部成，
     * 更新unfilledAmount为减去成交量后的值
     * @param usdkOrderDTO
     * @param filledAmount
     * @return
     */
    private int updateSingleOrderByFilledAmount(UsdkOrderDTO usdkOrderDTO, BigDecimal filledAmount) {
        if (usdkOrderDTO.getUnfilledAmount().compareTo(filledAmount) == 0) {
            usdkOrderDTO.setStatus(OrderStatusEnum.MATCH.getCode());
        } else if (usdkOrderDTO.getStatus() == OrderStatusEnum.COMMIT.getCode()) {
            usdkOrderDTO.setStatus(OrderStatusEnum.PART_MATCH.getCode());
        }
        usdkOrderDTO.setUnfilledAmount(usdkOrderDTO.getUnfilledAmount().subtract(filledAmount));
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        BeanUtils.copy(usdkOrderDTO, usdkOrderDO);
        return usdkOrderMapper.updateByPrimaryKey(usdkOrderDO);
    }
}
