package com.fota.trade.service.impl;

import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.enums.ContractOrderStatusEnum;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.service.ContractOrderService;
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
    public List<ContractOrderDO> listNotMatchOrder(BigInteger contractOrderIndex) {
        List<ContractOrderDO> notMatchOrderList = null;
        try {
            notMatchOrderList = contractOrderMapper.notMatchOrderList(
                    ContractOrderStatusEnum.PLACE_ORDER.getCode(), ContractOrderStatusEnum.PARTIAL_SUCCESS.getCode(), contractOrderIndex);
        } catch (Exception e) {
            log.error("contractOrderMapper.notMatchOrderList error", e);
        }
        if (notMatchOrderList == null) {
            notMatchOrderList = new ArrayList<>();
        }
        return notMatchOrderList;
    }
}
