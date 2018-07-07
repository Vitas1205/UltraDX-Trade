package com.fota.client.service;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.client.domain.query.UsdkOrderQuery;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public interface UsdkOrderService {
    
    /**
     * 查询Usdk兑换订单列表
     * @param usdkOrderQuery
     * @return
     */
    Result<Page<UsdkOrderDTO>> listUsdkOrderByQuery(UsdkOrderQuery usdkOrderQuery);

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
