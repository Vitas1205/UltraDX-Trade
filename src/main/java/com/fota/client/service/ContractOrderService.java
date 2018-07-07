package com.fota.client.service;

import com.fota.client.common.Page;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.client.domain.query.ContractOrderQuery;
import com.fota.trade.domain.ContractOrderDO;

import java.math.BigInteger;
import java.util.List;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午11:16 2018/7/5
 * @Modified:
 */
public interface ContractOrderService {

    /**
     * 查询未被撮合或部分撮合的订单
     * @return
     */
    List<ContractOrderDO> listNotMatchOrder(BigInteger contractOrderIndex);

    /**
     *
     * @param contractOrderQuery
     * @return
     */
    Page<ContractOrderDTO> listContractOrderByQuery(ContractOrderQuery contractOrderQuery);


    /**
     * 合约下单接口
     * @param contractOrderDO
     * @return
     */
    ResultCode order(ContractOrderDO contractOrderDO);

    /**
     * 撤销合约订单
     * @param userId
     * @param orderId
     * @return
     */
    ResultCode cancelOrder(Long userId, Long orderId);

    /**
     * 撤销用户所有合约订单
     * @param userId
     * @return
     */
    ResultCode cancelAllOrder(Long userId);

}
