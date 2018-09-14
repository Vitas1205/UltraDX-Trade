package com.fota.trade.util;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Created by Swifree on 2018/8/17.
 * Code is the law
 */
@Slf4j
public class BasicUtils {

    public static final Random random =new Random();
    public static final BigDecimal error = new BigDecimal(1e-10);

    public static long generateId(){
        UUID uuid = UUID.randomUUID();
        long m = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        m= Math.abs(m);
        m = m % 999999999999999L;
        return m;
    }
    public static <R>  R exeWhitoutError(Supplier<R> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            log.error("exe function error");
        }
        return null;
    }

    public static int randomInt(int bound){
        return random.nextInt(bound);
    }

    public static boolean equal(BigDecimal a, BigDecimal b) {
        if (null == a || null == b) {
            return a == b;
        }
        return a.subtract(b).abs().compareTo(error) < 0;
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        return a.subtract(b).compareTo(error) > 0;
    }

    public static boolean gtOrEq(BigDecimal a, BigDecimal b) {
        return a.subtract(b).compareTo(error) > 0;
    }

}
