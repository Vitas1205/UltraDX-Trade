package com.fota.trade.service.impl;

import com.fota.client.service.EntrustDepthService;
import com.fota.client.service.UsdkOrderService;
import com.fota.trade.domain.AssetCategoryDO;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.client.service.ContractCategoryService;
import com.fota.client.service.ContractOrderService;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.AssetCategoryMapper;
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
public class EntrustDepthServiceImpl implements EntrustDepthService {

    private static final Logger log = LoggerFactory.getLogger(EntrustDepthServiceImpl.class);

    @Autowired
    private com.fota.trade.service.ContractCategoryService contractCategoryService;

    @Autowired
    private ContractOrderManager contractOrderManager;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Autowired
    private AssetCategoryMapper assetCategoryMapper;

    /**
     * 查询所有未成交或部分成交的合约订单转换map
     * @param orderDirection 1 买单 2 卖单
     * @return
     */
    @Override
    public Map<String, Map<BigDecimal, BigDecimal>> initContractEntrust(Integer orderDirection) {
        Map<String, Map<BigDecimal, BigDecimal>> contractEntrustMap = new LinkedHashMap<>();
//        List<ContractCategoryDO> contractCategoryList = contractCategoryService.listActiveContract();
//        // TODO contractOrderIndex 需要从redis中获取 暂时写死
//        List<ContractOrderDO> contractOrderList = contractOrderManager.listNotMatchOrder(2L, orderDirection);
//
//        if (contractCategoryList.isEmpty()) {
//            log.info("no active contracts at the moment");
//            return contractEntrustMap;
//        }
//
//        if (contractOrderList.isEmpty()) {
//            log.info("no outstanding contract orders");
//            return contractEntrustMap;
//        }
//
//        contractCategoryList.stream()
//                .sorted(Comparator.comparing(ContractCategoryDO::getAssetId))
//                .forEach(contractCategoryDO -> {
//            if (!contractEntrustMap.containsKey(contractCategoryDO.getContractName())) {
//                contractEntrustMap.put(contractCategoryDO.getContractName(), new LinkedHashMap<>());
//            }
//        });
//
//        contractOrderList.stream()
//                .sorted(Comparator.comparing(ContractOrderDO::getPrice))
//                .forEach(contractOrderDO -> {
//            Map<BigDecimal, BigDecimal> priceAndAmountMap = contractEntrustMap.get(contractOrderDO.getContractName());
//            if (priceAndAmountMap != null) {
//                BigDecimal price = contractOrderDO.getPrice();
//                if (priceAndAmountMap.containsKey(price)) {
//                    BigDecimal amount = priceAndAmountMap.get(price);
////                    amount = amount.add(contractOrderDO.getUnfilledAmount());
//                    priceAndAmountMap.put(price, amount);
//                } else {
////                    priceAndAmountMap.put(price, contractOrderDO.getUnfilledAmount());
//                }
//            }
//        });
        return contractEntrustMap;
    }

    /**
     * 查询所有未成交或部分成交的usdk订单转换map
     * @param orderDirection 1 买单 2 卖单
     * @return
     */
    @Override
    public Map<String, Map<BigDecimal, BigDecimal>> initUsdkEntrust(Integer orderDirection) {
        Map<String, Map<BigDecimal, BigDecimal>> usdkEntrustMap = new LinkedHashMap<>();
        List<AssetCategoryDO> assetCategoryList = assetCategoryMapper.getAll();
        // TODO contractOrderIndex 需要从redis中获取 暂时写死
        List<UsdkOrderDO> usdkOrderList = usdkOrderManager.listNotMatchOrder(2L, orderDirection);

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
