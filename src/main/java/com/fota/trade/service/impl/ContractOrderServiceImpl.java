package com.fota.trade.service.impl;

import com.fota.client.common.Page;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.client.domain.query.ContractOrderQuery;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.enums.EntrustOrderStatusEnum;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.client.service.ContractOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午11:16 2018/7/5
 * @Modified:
 */
@Service("ContractOrderService")
public class ContractOrderServiceImpl implements ContractOrderService {

    private static final Logger log = LoggerFactory.getLogger(ContractOrderServiceImpl.class);

    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Override
    public List<ContractOrderDO> listNotMatchOrder(BigInteger contractOrderIndex, Integer orderDirection) {
        List<ContractOrderDO> notMatchOrderList = null;
        try {
            notMatchOrderList = contractOrderMapper.notMatchOrderList(
                    EntrustOrderStatusEnum.PLACE_ORDER.getCode(), EntrustOrderStatusEnum.PARTIAL_SUCCESS.getCode(), contractOrderIndex, orderDirection);
        } catch (Exception e) {
            log.error("contractOrderMapper.notMatchOrderList error", e);
        }
        if (notMatchOrderList == null) {
            notMatchOrderList = new ArrayList<>();
        }
        return notMatchOrderList;
    }

    @Override
    public Page<ContractOrderDTO> listContractOrderByQuery(ContractOrderQuery contractOrderQuery) {
        return null;
    }

    @Override
    public ResultCode order(ContractOrderDO contractOrderDO) {
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
