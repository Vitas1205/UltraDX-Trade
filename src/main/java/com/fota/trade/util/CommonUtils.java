package com.fota.trade.util;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Created by Swifree on 2018/8/17.
 * Code is the law
 */
public class CommonUtils {
    public static final BigDecimal wucha = new BigDecimal(1e-6);

    public static boolean equal(BigDecimal a, BigDecimal b) {
        if (null == a || null == b) {
            return a == b;
        }
        return a.subtract(b).abs().compareTo(wucha) < 0;
    }
    public static long generateId(){
        UUID uuid = UUID.randomUUID();
        return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
    }
}
