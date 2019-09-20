package com.fota.trade.util;

import com.alibaba.fastjson.JSONObject;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderTypeEnum;
import com.fota.trade.service.internal.MarketAccountListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * @author Gavin Shen
 * @Date 2018/12/20
 */
@Component
public class MonitorLogManager {

    private static final Logger log = LoggerFactory.getLogger(MonitorLogManager.class);
    private static final Logger tradeLog = LoggerFactory.getLogger("trade");

    private static final int ORDER_TYPE_COIN = 1;

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
            if (Objects.isNull(username)) {
                username = "";
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
                    BigDecimal.ZERO);
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
                    BigDecimal.ZERO);
        } catch (Exception e) {
            log.error("cancelCoinOrderInfo({}) exception", usdkOrderDO, e);
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

    private String getUserNameByOrder(UsdkOrderDO usdkOrderDO) {
        JSONObject jsonObject = JSONObject.parseObject(usdkOrderDO.getOrderContext());
        if (jsonObject != null && !jsonObject.isEmpty()) {
            return jsonObject.get("username") == null ? "" : jsonObject.get("username").toString();
        }
        return null;
    }

}
