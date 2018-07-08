package com.fota.client.service;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
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
     *
     * @param contractOrderQuery
     * @return
     */
    Result<Page<ContractOrderDTO>> listContractOrderByQuery(ContractOrderQuery contractOrderQuery);


    /**
     * 合约下单接口
     * @param contractOrderDTO
     * @return
     */
    ResultCode order(ContractOrderDTO contractOrderDTO);

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
