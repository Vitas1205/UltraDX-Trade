package com.fota.trade.util;

import java.math.BigDecimal;

/**
 * @author Gavin Shen
 * @Date 2018/7/27
 */
public class PriceUtil {

    public static BigDecimal getAveragePrice(BigDecimal oldAveragePrice, BigDecimal oldAmount, BigDecimal addAmount, BigDecimal filledPrice) {
        if (oldAveragePrice == null) {
            oldAveragePrice = BigDecimal.ZERO;
        }
        return oldAveragePrice.multiply(oldAmount)
                .add(addAmount.multiply(filledPrice))
                .divide(oldAmount.add(addAmount), 8, BigDecimal.ROUND_DOWN);
    }

}
