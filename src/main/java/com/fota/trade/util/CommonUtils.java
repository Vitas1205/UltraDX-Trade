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
public class CommonUtils {

    public static final Random random =new Random();

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
    public static void exeWhitoutError(Runnable runnable) {
        try {
            runnable.run();
        }catch (Throwable t){
            log.error("exe function exception", t);
        }
    }

    public static int randomInt(int bound){
        return random.nextInt(bound);
    }

}
