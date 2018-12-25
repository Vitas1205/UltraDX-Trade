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

    private static final Logger log = LoggerFactory.getLogger(MonitorLogManager.class);
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
    public void placeCoinOrderInfo(UsdkOrderDO usdkOrderDO, String username) {
        try {
            if (marketAccountListService.contains(usdkOrderDO.getUserId())) {
                return;
            }
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    ORDER_TYPE_COIN,
                    usdkOrderDO.getAssetName(),
                    username,
                    usdkOrderDO.getUnfilledAmount().toPlainString(),
                    System.currentTimeMillis(),
                    BIZ_TYPE_PLACE_ORDER,
                    usdkOrderDO.getOrderDirection(),
                    usdkOrderDO.getUserId(),
                    usdkOrderDO.getFee());
        } catch (Exception e) {
            log.error("placeCoinOrderInfo({}) exception", usdkOrderDO, e);
        }
    }

    /**
     * order@type@@@name@@@username@@@amount@@@timestamp@@@operation@@@orderDirection@@@userId@@@fee
     */
    public void cancelCoinOrderInfo(UsdkOrderDO usdkOrderDO) {
        try {
            if (marketAccountListService.contains(usdkOrderDO.getUserId())) {
                return;
            }
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    ORDER_TYPE_COIN,
                    usdkOrderDO.getAssetName(),
                    getUserNameByOrder(usdkOrderDO),
                    usdkOrderDO.getUnfilledAmount().toPlainString(),
                    usdkOrderDO.getGmtCreate().getTime(),
                    BIZ_TYPE_CANCEL_ORDER,
                    usdkOrderDO.getOrderDirection(),
                    usdkOrderDO.getUserId(),
                    usdkOrderDO.getFee());
        } catch (Exception e) {
            log.error("cancelCoinOrderInfo({}) exception", usdkOrderDO, e);
        }
    }

    /**
     * 合约下单
     */
    public void placeContractOrderInfo(ContractOrderDO contractOrderDO, String userName) {
        try {
            if (marketAccountListService.contains(contractOrderDO.getUserId())) {
                return;
            }
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    ORDER_TYPE_CONTRACT,
                    contractOrderDO.getContractName(),
                    userName,
                    contractOrderDO.getUnfilledAmount().toPlainString(),
                    System.currentTimeMillis(),
                    contractOrderDO.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode()) ? BIZ_TYPE_FORCE_ORDER : BIZ_TYPE_PLACE_ORDER,
                    contractOrderDO.getOrderDirection(),
                    contractOrderDO.getUserId(),
                    contractOrderDO.getFee());
        } catch (Exception e) {
            log.error("placeContractOrderInfo({}) exception", contractOrderDO, e);
        }
    }

    public void cancelContractOrderInfo(ContractOrderDO contractOrderDO) {
        try {
            if (marketAccountListService.contains(contractOrderDO.getUserId())) {
                return;
            }
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    ORDER_TYPE_COIN,
                    contractOrderDO.getContractName(),
                    getUserNameByOrder(contractOrderDO),
                    contractOrderDO.getUnfilledAmount().toPlainString(),
                    contractOrderDO.getGmtCreate().getTime(),
                    BIZ_TYPE_CANCEL_ORDER,
                    contractOrderDO.getOrderDirection(),
                    contractOrderDO.getUserId(),
                    contractOrderDO.getFee());
        } catch (Exception e) {
            log.error("cancelContractOrderInfo({}) exception", contractOrderDO, e);
        }
    }

    public void coinDealOrderInfo(UsdkOrderDO usdkOrderDO, BigDecimal filledAmount) {
        try {
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
        } catch (Exception e) {
            log.error("coinDealOrderInfo({}, {}) exception", usdkOrderDO, filledAmount, e);
        }
    }

    public void contractDealOrderInfo(ContractOrderDO contractOrderDO, BigDecimal completeAmount, BigDecimal filledPrice) {
        try {
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
        } catch (Exception e) {
            log.error("contractDealOrderInfo({}, {}, {}) exception", contractOrderDO, completeAmount, filledPrice, e);
        }
    }

    /**
     * ald监控
     * adl@userId@@@orderDirection@@@contractName@@@amount@@@timestamp
     */
    public void adlInfo(UserPositionDO userPositionDO, BigDecimal adlAmount) {
        try {
            tradeLog.info("adl@{}@@@{}@@@{}@@@{}@@@{}",
                    userPositionDO.getUserId(),
                    userPositionDO.getPositionType(),
                    userPositionDO.getContractName(),
                    adlAmount.toPlainString(),
                    System.currentTimeMillis());
        } catch (Exception e) {
            log.error("adlInfo({}, {}) exception", userPositionDO, adlAmount);
        }
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
