package com.fota.trade.service;

import com.fota.common.Page;
import com.fota.trade.domain.*;

public interface UsdkOrderService {

    /**
     * 查询Usdk兑换订单列表
     *
     * @param usdkOrderQuery
     * @param usdkOrderQuery
     * @return
     */
    Page<UsdkOrderDTO> listUsdkOrderByQuery(BaseQuery usdkOrderQuery);

    /**
     * 下单接口
     *
     * @param usdkOrderDTO
     * @param usdkOrderDTO
     * @return
     */
    ResultCode order(UsdkOrderDTO usdkOrderDTO);

    /**
     * 撤销订单
     *
     * @param userId
     * @param orderId
     * @param userId
     * @param orderId
     * @return
     */
    ResultCode cancelOrder(long userId, long orderId);

    /**
     * 撤销用户所有订单
     *
     * @param userId
     * @param userId
     * @return
     */
    ResultCode cancelAllOrder(long userId);

    /**
     * 根据成交更新订单的信息
     *
     * @param usdkMatchedOrderDTO
     * @return
     */
    ResultCode updateOrderByMatch(UsdkMatchedOrderDTO usdkMatchedOrderDTO);

}
