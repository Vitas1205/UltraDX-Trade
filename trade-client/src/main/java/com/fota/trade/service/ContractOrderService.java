package com.fota.trade.service;

import com.fota.common.Page;
import com.fota.trade.domain.BaseQuery;
import com.fota.trade.domain.ContractMatchedOrderDTO;
import com.fota.trade.domain.ContractOrderDTO;
import com.fota.trade.domain.ResultCode;

import java.math.BigDecimal;
import java.util.List;

public interface ContractOrderService {

    /**
     * @param contractOrderQuery
     * @return
     */
    Page<ContractOrderDTO> listContractOrderByQuery(BaseQuery contractOrderQuery);

    /**
     *
     * @param contractOrderQuery
     * @return
     */
    List<ContractOrderDTO> getAllContractOrder(BaseQuery contractOrderQuery);

    /**
     * 合约下单接口
     *
     * @param contractOrderDTO
     * @param contractOrderDTO
     * @return
     */
    ResultCode order(ContractOrderDTO contractOrderDTO);

    /**
     * 撤销合约订单
     *
     * @param userId
     * @param orderId
     * @param userId
     * @param orderId
     * @return
     */
    ResultCode cancelOrder(long userId, long orderId);

    /**
     * 撤销用户所有合约订单
     *
     * @param userId
     * @param userId
     * @return
     */
    ResultCode cancelAllOrder(long userId);

    /**
     * 撤销用户非强平单
     * @param userId
     * @return
     */
    ResultCode cancelOrderByOrderType(long userId, int orderType);

    /**
     * 撤销该合约的所有委托订单
     * @param contractId
     * @return
     */
    ResultCode cancelOrderByContractId(long contractId);

    /**
     * 根据成交更新订单的信息
     *
     * @param contractMatchedOrderDTO
     * @return
     */
    ResultCode updateOrderByMatch(ContractMatchedOrderDTO contractMatchedOrderDTO);

    /**
     * 获取昨天六点到今天六点的平台手续费
     */
    BigDecimal getTodayFee();
}