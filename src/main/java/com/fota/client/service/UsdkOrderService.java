package com.fota.client.service;

import com.fota.client.common.Page;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.client.domain.query.UsdkOrderQuery;

import java.math.BigInteger;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public interface UsdkOrderService {

    /**
     * 查询未被撮合或部分撮合的订单
     * @return
     */
    List<UsdkOrderDTO> listNotMatchOrder(BigInteger contractOrderIndex, Integer orderDirection);

    /**
     * 查询Usdk兑换订单列表
     * @param usdkOrderQuery
     * @return
     */
    Page<UsdkOrderDTO> listUsdkOrderByQuery(UsdkOrderQuery usdkOrderQuery);

    /**
     * 下单接口
     * @param usdkOrderDTO
     * @return
     */
    ResultCode order(UsdkOrderDTO usdkOrderDTO);

    /**
     * 撤销订单
     * @param userId
     * @param orderId
     * @return
     */
    ResultCode cancelOrder(Long userId, Long orderId);

    /**
     * 撤销用户所有订单
     * @param userId
     * @return
     */
    ResultCode cancelAllOrder(Long userId);

}
