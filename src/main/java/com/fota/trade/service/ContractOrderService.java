package com.fota.trade.service;

import com.fota.trade.domain.ContractOrderDO;

import java.math.BigInteger;
import java.util.List;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午11:16 2018/7/5
 * @Modified:
 */
public interface
ContractOrderService {

    /**
     * 查询未被撮合或部分撮合的订单
     * @return
     */
    List<ContractOrderDO> listNotMatchOrder(BigInteger contractOrderIndex, Integer orderDirection);
}
