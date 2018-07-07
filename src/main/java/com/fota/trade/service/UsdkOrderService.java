package com.fota.trade.service;

import com.fota.trade.domain.UsdkOrderDO;

import java.math.BigInteger;
import java.util.List;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午3:22 2018/7/6
 * @Modified:
 */
public interface UsdkOrderService {
    /**
     * 查询未撮合或部分撮合的订单
     * @param contractOrderIndex
     * @param orderDirection
     * @return
     */
    List<UsdkOrderDO> listNotMatchOrder(BigInteger contractOrderIndex, Integer orderDirection);
}
