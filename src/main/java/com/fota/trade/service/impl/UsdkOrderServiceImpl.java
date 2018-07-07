package com.fota.trade.service.impl;

import com.fota.client.common.Page;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.client.domain.query.UsdkOrderQuery;
import com.fota.client.service.UsdkOrderService;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
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
public class UsdkOrderServiceImpl implements UsdkOrderService {

    private static final Logger log = LoggerFactory.getLogger(UsdkOrderServiceImpl.class);
    public  static BigDecimal usdkFee = BigDecimal.valueOf(0.001);

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Override
    public Page<UsdkOrderDTO> listUsdkOrderByQuery(UsdkOrderQuery usdkOrderQuery) {
        return null;
    }

    @Override
    public ResultCode order(UsdkOrderDTO usdkOrderDTO) {
        Integer assetId = usdkOrderDTO.getAssetId();
        String assetName = usdkOrderDTO.getAssetName();
        Long userId = usdkOrderDTO.getUserId();
        Integer orderDirection = usdkOrderDTO.getOrderDirection();
        Integer orderType = usdkOrderDTO.getOrderType();
        BigDecimal totalAmount = usdkOrderDTO.getTotalAmount();
        BigDecimal price = usdkOrderDTO.getPrice();
        if (StringUtils.isEmpty(assetName)){

        }
        BigDecimal unfilledAmount = totalAmount;
        Integer status = OrderStatusEnum.COMMIT.getCode();
        BigDecimal fee = usdkFee;
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        usdkOrderDO.setAssetId(assetId);
        usdkOrderDO.setAssetName(assetName);
        usdkOrderDO.setUserId(userId);
        usdkOrderDO.setOrderDirection(orderDirection);
        usdkOrderDO.setOrderType(orderType);
        usdkOrderDO.setTotalAmount(totalAmount);
        usdkOrderDO.setPrice(price);
        usdkOrderDO.setUnfilledAmount(unfilledAmount);
        usdkOrderDO.setStatus(status);
        usdkOrderDO.setFee(fee);
        return null;
    }

    @Override
    public ResultCode cancelOrder(Long userId, Long orderId) {
        return null;
    }

    @Override
    public ResultCode cancelAllOrder(Long userId) {
        return null;
    }
}
