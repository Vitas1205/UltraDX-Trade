package com.fota.trade.util;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
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
            log.error("exe function error",t);
        }
        return null;
    }

    public static void exeWhitoutError(RunnableWithException runnable) {
        try {
            runnable.run();
        }catch (Throwable t){
            log.error("exe function exception", t);
        }
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

    public static int absHash(Object obj) {
        int h = obj.hashCode();
        if (h < 0) {
            h = Math.abs(h);
        }
        if (h < 0) {
            h = 0;
        }
        return h;
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        return a.subtract(b).compareTo(error) > 0;
    }

    public static boolean gtOrEq(BigDecimal a, BigDecimal b) {
        return a.subtract(b).compareTo(error) > 0;
    }

    public static Boolean retryWhenFail(Callable<Boolean> callable, Duration retryDuration, int maxRetries){
        //??????false???????????????
        return retryWhenFail(callable, Predicates.equalTo(false), retryDuration, maxRetries);
    }
    public static <T> T retryWhenFail(Callable<T> callable, Predicate<T> retryPredicate, Duration retryDuration, int maxRetries){
        Retryer<T> retryer = RetryerBuilder
                .<T>newBuilder()
                //??????runtime?????????checked????????????????????????????????????error???????????????
                .retryIfExceptionOfType(Throwable.class)
                .retryIfResult(retryPredicate)
                //????????????
                .withWaitStrategy(WaitStrategies.fixedWait(retryDuration.toMillis(), TimeUnit.MILLISECONDS))
                //????????????
                .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetries))
                .build();
        try {
            return retryer.call(callable);
        } catch (Throwable t) {
            log.error("retry call exception after {} times", maxRetries, t);
            return null;
        }
    }

//    public static Pair<String, Integer> getAndInc(String key){
//        if (!key.matches(".*#[0-9]*")) {
//            return Pair.of(key+"#1", 0);
//        }
//        String[] strs = key.split("#");
//        int cnt = Integer.parseInt(strs[1]);
//        return Pair.of(strs[0]+"#"+ (cnt+1), cnt);
//    }
    public static int count(String str, char ch) {
        if (null == str) {
            return 0;
        }
        int count = 0;
        for (char tm : str.toCharArray()) {
            if (tm == ch) {
                count++;
            }
        }
        return count;
    }

}
