package com.fota.trade.util;

import com.fota.asset.domain.ContractDealer;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UserPositionDO;

import java.math.BigDecimal;

/**
 * Created by Swifree on 2018/8/22.
 * Code is the law
 */
public class ContractUtils {
    public static UserPositionDO buildPosition(ContractOrderDO contractOrderDO, BigDecimal contractSize, int lever, long filledAmount, BigDecimal filledPrice) {
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
        userPositionDO.setAveragePrice(filledPrice);
        userPositionDO.setUnfilledAmount(filledAmount);
        userPositionDO.setStatus(1);
        userPositionDO.setUserId(contractOrderDO.getUserId());
        userPositionDO.setContractName(contractOrderDO.getContractName());
        userPositionDO.setContractId(contractOrderDO.getContractId());
        userPositionDO.setContractSize(contractSize);
        userPositionDO.setLever(lever);
        return userPositionDO;
    }

    public static BigDecimal calAveragePrice(long oldAmount, BigDecimal oldAveragePrice, long filledAmount,  BigDecimal filledPrice){
        long newTotalAmount = oldAmount + filledAmount;
        BigDecimal oldTotalPrice = oldAveragePrice.multiply(new BigDecimal(oldAmount));
        BigDecimal addedTotalPrice = filledPrice.multiply(new BigDecimal(filledAmount));
        BigDecimal newTotalPrice = oldTotalPrice.add(addedTotalPrice);
        BigDecimal newAveragePrice = newTotalPrice.divide(new BigDecimal(newTotalAmount), 8, BigDecimal.ROUND_DOWN);
        return newAveragePrice;
    }

}
