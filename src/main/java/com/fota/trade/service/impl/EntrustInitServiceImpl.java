package com.fota.trade.service.impl;

import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.service.ContractCategoryService;
import com.fota.trade.service.ContractOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午9:37 2018/7/5
 * @Modified:
 */
@Service
public class EntrustInitServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(EntrustInitServiceImpl.class);

    @Autowired
    private ContractCategoryService contractCategoryService;

    @Autowired
    private ContractOrderService contractOrderService;

    public Map<String, Map<BigDecimal, BigDecimal>> initContractEntrust() {
        Map<String, Map<BigDecimal, BigDecimal>> contractEntrustMap = new LinkedHashMap<>();
        // TODO contractOrderIndex 需要从redis中获取 暂时写死
        BigInteger contractOrderIndex = new BigInteger("2");
        List<ContractCategoryDO> contractCategoryList = contractCategoryService.listActiveContract();
        // TODO 买卖未作区分
        List<ContractOrderDO> contractOrderList = contractOrderService.listNotMatchOrder(contractOrderIndex);

        if (contractCategoryList.isEmpty()) {
            log.info("no active contracts at the moment");
            return contractEntrustMap;
        }

        if (contractOrderList.isEmpty()) {
            log.info("no outstanding orders");
            return contractEntrustMap;
        }

        contractCategoryList.stream()
                .sorted(Comparator.comparing(ContractCategoryDO::getContractName))
                .forEach(contractCategoryDO -> {
            if (!contractEntrustMap.containsKey(contractCategoryDO.getContractName())) {
                contractEntrustMap.put(contractCategoryDO.getContractName(), new LinkedHashMap<>());
            }
        });

        contractOrderList.forEach(contractOrderDO -> {
            Map<BigDecimal, BigDecimal> priceAndAmountMap = contractEntrustMap.get(contractOrderDO.getContractName());
            if (priceAndAmountMap != null) {
                BigDecimal price = contractOrderDO.getPrice();
                BigDecimal amount = priceAndAmountMap.get(price);
                if (amount == null) {
                    priceAndAmountMap.put(
                            price, contractOrderDO.getTotalAmount().subtract(contractOrderDO.getUnfilledAmount()));
                } else {
                    amount = amount.add(
                            contractOrderDO.getTotalAmount().subtract(contractOrderDO.getUnfilledAmount()));
                    priceAndAmountMap.put(price, amount);
                }
            }
        });

        return contractEntrustMap;
    }

}
