package com.fota.trade.service.impl;

import com.fota.trade.domain.AssetCategoryDO;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.mapper.AssetCategoryMapper;
import com.fota.trade.service.ContractCategoryService;
import com.fota.trade.service.ContractOrderService;
import com.fota.trade.service.UsdkOrderService;
import org.apache.commons.lang.StringUtils;
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

    @Autowired
    private UsdkOrderService usdkOrderService;

    @Autowired
    private AssetCategoryMapper assetCategoryMapper;

    /**
     * 查询所有未成交或部分成交的合约订单转换map
     * @param orderDirection 1 买单 2 卖单
     * @return
     */
    public Map<String, Map<BigDecimal, BigDecimal>> initContractEntrust(Integer orderDirection) {
        Map<String, Map<BigDecimal, BigDecimal>> contractEntrustMap = new LinkedHashMap<>();
        // TODO contractOrderIndex 需要从redis中获取 暂时写死
        BigInteger contractOrderIndex = new BigInteger("2");
        List<ContractCategoryDO> contractCategoryList = contractCategoryService.listActiveContract();
        List<ContractOrderDO> contractOrderList = contractOrderService.listNotMatchOrder(contractOrderIndex, orderDirection);

        if (contractCategoryList.isEmpty()) {
            log.info("no active contracts at the moment");
            return contractEntrustMap;
        }

        if (contractOrderList.isEmpty()) {
            log.info("no outstanding contract orders");
            return contractEntrustMap;
        }

        contractCategoryList.stream()
                .sorted(Comparator.comparing(ContractCategoryDO::getAssetId))
                .forEach(contractCategoryDO -> {
            if (!contractEntrustMap.containsKey(contractCategoryDO.getContractName())) {
                contractEntrustMap.put(contractCategoryDO.getContractName(), new LinkedHashMap<>());
            }
        });

        contractOrderList.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice))
                .forEach(contractOrderDO -> {
            Map<BigDecimal, BigDecimal> priceAndAmountMap = contractEntrustMap.get(contractOrderDO.getContractName());
            if (priceAndAmountMap != null) {
                BigDecimal price = contractOrderDO.getPrice();
                if (priceAndAmountMap.containsKey(price)) {
                    BigDecimal amount = priceAndAmountMap.get(price);
                    amount = amount.add(contractOrderDO.getUnfilledAmount());
                    priceAndAmountMap.put(price, amount);
                } else {
                    priceAndAmountMap.put(price, contractOrderDO.getUnfilledAmount());
                }
            }
        });
        return contractEntrustMap;
    }

    /**
     * 查询所有未成交或部分成交的usdk订单转换map
     * @param orderDirection 1 买单 2 卖单
     * @return
     */
    public Map<String, Map<BigDecimal, BigDecimal>> initUsdkEntrust(Integer orderDirection) {
        Map<String, Map<BigDecimal, BigDecimal>> usdkEntrustMap = new LinkedHashMap<>();
        // TODO contractOrderIndex 需要从redis中获取 暂时写死
        BigInteger contractOrderIndex = new BigInteger("2");
        List<AssetCategoryDO> assetCategoryList = assetCategoryMapper.getAll();
        List<UsdkOrderDO> usdkOrderList = usdkOrderService.listNotMatchOrder(contractOrderIndex, orderDirection);

        if (assetCategoryList == null || assetCategoryList.isEmpty()) {
            log.info("no active subject matter");
            return usdkEntrustMap;
        }

        if (usdkOrderList.isEmpty()) {
            log.info("no outstanding usdk orders");
            return usdkEntrustMap;
        }

        assetCategoryList.stream()
                .sorted(Comparator.comparing(AssetCategoryDO::getId))
                .forEach(assetCategoryDO -> {
                    if (!StringUtils.equals(assetCategoryDO.getName(), "USDK")
                            && usdkEntrustMap.containsKey(assetCategoryDO.getName())) {
                        usdkEntrustMap.put(assetCategoryDO.getName(), new LinkedHashMap<>());
                    }
                });

        usdkOrderList.stream()
                .sorted(Comparator.comparing(UsdkOrderDO::getPrice))
                .forEach(usdkOrderDO -> {
                    Map<BigDecimal, BigDecimal> priceAndAmountMap = usdkEntrustMap.get(usdkOrderDO.getAssetName());
                    if (priceAndAmountMap != null) {
                       BigDecimal price = usdkOrderDO.getPrice();
                       if (priceAndAmountMap.containsKey(price)) {
                           BigDecimal amount = priceAndAmountMap.get(price);
                           priceAndAmountMap.put(price, amount.add(usdkOrderDO.getUnfilledAmount()));
                       } else {
                           priceAndAmountMap.put(price, usdkOrderDO.getUnfilledAmount());
                       }
                    }
                });
        return usdkEntrustMap;
    }

}
