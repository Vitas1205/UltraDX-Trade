package com.fota.trade.util;

import com.alibaba.fastjson.JSONObject;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderTypeEnum;
import com.fota.trade.service.internal.MarketAccountListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author Gavin Shen
 * @Date 2018/12/20
 */
@Component
public class MonitorLogManager {

    private static final Logger tradeLog = LoggerFactory.getLogger("trade");

    private static final int ORDER_TYPE_COIN = 1;
    private static final int ORDER_TYPE_CONTRACT = 2;

    // 下单
    private static final int BIZ_TYPE_PLACE_ORDER = 1;
    // 撤单
    private static final int BIZ_TYPE_CANCEL_ORDER = 2;
    // 强平
    private static final int BIZ_TYPE_FORCE_ORDER = 3;
    // 成交
    private static final int BIZ_TYPE_DEAL = 4;

    @Autowired
    private MarketAccountListService marketAccountListService;

    /**
     * 现货下单日志
     * order@type@@@name@@@username@@@amount@@@timestamp@@@operation@@@orderDirection@@@userId@@@fee
     */
    public void placeCoinOrderInfo(UsdkOrderDO usdkOrderDO) {
        if (marketAccountListService.contains(usdkOrderDO.getUserId())) {
            return;
        }
        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{",
                ORDER_TYPE_COIN,
                usdkOrderDO.getAssetName(),
                getUserNameByOrder(usdkOrderDO),
                usdkOrderDO.getUnfilledAmount().toPlainString(),
                usdkOrderDO.getGmtCreate().getTime(),
                BIZ_TYPE_PLACE_ORDER,
                usdkOrderDO.getOrderDirection(),
                usdkOrderDO.getUserId(),
                usdkOrderDO.getFee());
    }

    /**
     * order@type@@@name@@@username@@@amount@@@timestamp@@@operation@@@orderDirection@@@userId@@@fee
     */
    public void cancelCoinOrderInfo(UsdkOrderDO usdkOrderDO) {
        if (marketAccountListService.contains(usdkOrderDO.getUserId())) {
            return;
        }
        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{",
                ORDER_TYPE_COIN,
                usdkOrderDO.getAssetName(),
                getUserNameByOrder(usdkOrderDO),
                usdkOrderDO.getUnfilledAmount().toPlainString(),
                usdkOrderDO.getGmtCreate().getTime(),
                BIZ_TYPE_CANCEL_ORDER,
                usdkOrderDO.getOrderDirection(),
                usdkOrderDO.getUserId(),
                usdkOrderDO.getFee());
    }

    /**
     * 合约下单
     */
    public void placeContractOrderInfo(ContractOrderDO contractOrderDO) {
        if (marketAccountListService.contains(contractOrderDO.getUserId())) {
            return;
        }
        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{",
                ORDER_TYPE_CONTRACT,
                contractOrderDO.getContractName(),
                getUserNameByOrder(contractOrderDO),
                contractOrderDO.getUnfilledAmount().toPlainString(),
                contractOrderDO.getGmtCreate().getTime(),
                contractOrderDO.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode()) ? BIZ_TYPE_FORCE_ORDER : BIZ_TYPE_PLACE_ORDER,
                contractOrderDO.getOrderDirection(),
                contractOrderDO.getUserId(),
                contractOrderDO.getFee());
    }

    public void cancelContractOrderInfo(ContractOrderDO contractOrderDO) {
        if (marketAccountListService.contains(contractOrderDO.getUserId())) {
            return;
        }
        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{",
                ORDER_TYPE_COIN,
                contractOrderDO.getContractName(),
                getUserNameByOrder(contractOrderDO),
                contractOrderDO.getUnfilledAmount().toPlainString(),
                contractOrderDO.getGmtCreate().getTime(),
                BIZ_TYPE_CANCEL_ORDER,
                contractOrderDO.getOrderDirection(),
                contractOrderDO.getUserId(),
                contractOrderDO.getFee());
    }

    public void coinDealOrderInfo(UsdkOrderDO usdkOrderDO, BigDecimal filledAmount) {
        if (marketAccountListService.contains(usdkOrderDO.getUserId())) {
            return;
        }
        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                ORDER_TYPE_COIN,
                usdkOrderDO.getAssetName(),
                getUserNameByOrder(usdkOrderDO),
                filledAmount.toPlainString(),
                System.currentTimeMillis(),
                BIZ_TYPE_DEAL,
                usdkOrderDO.getOrderDirection(),
                usdkOrderDO.getUserId(),
                BigDecimal.ZERO);
    }

    public void contractDealOrderInfo(ContractOrderDO contractOrderDO, BigDecimal completeAmount, BigDecimal filledPrice) {
        if (marketAccountListService.contains(contractOrderDO.getUserId())) {
            return;
        }
        BigDecimal fee = contractOrderDO.getFee().multiply(filledPrice).multiply(completeAmount).setScale(16, RoundingMode.DOWN);
        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                ORDER_TYPE_CONTRACT,
                contractOrderDO.getContractName(),
                getUserNameByOrder(contractOrderDO),
                completeAmount.toPlainString(),
                System.currentTimeMillis(),
                BIZ_TYPE_DEAL,
                contractOrderDO.getOrderDirection(),
                contractOrderDO.getUserId(),
                fee);
    }

    /**
     * ald监控
     * adl@userId@@@orderDirection@@@contractName@@@amount@@@timestamp
     */
    public void adlInfo(UserPositionDO userPositionDO, BigDecimal adlAmount) {
        tradeLog.info("adl@userId@@@orderDirection@@@contractName@@@amount@@@timestamp",
                userPositionDO.getUserId(),
                userPositionDO.getPositionType(),
                userPositionDO.getContractName(),
                adlAmount.toPlainString(),
                System.currentTimeMillis());
    }

    private String getUserNameByOrder(UsdkOrderDO usdkOrderDO) {
        JSONObject jsonObject = JSONObject.parseObject(usdkOrderDO.getOrderContext());
        if (jsonObject != null && !jsonObject.isEmpty()) {
            return jsonObject.get("username") == null ? "" : jsonObject.get("username").toString();
        }
        return null;
    }

    private String getUserNameByOrder(ContractOrderDO contractOrderDO) {
        JSONObject jsonObject = JSONObject.parseObject(contractOrderDO.getOrderContext());
        if (jsonObject != null && !jsonObject.isEmpty()) {
            return jsonObject.get("username") == null ? "" : jsonObject.get("username").toString();
        }
        return null;
    }

}
