package com.fota.trade.util;

/**
 * Created by lds on 2018/10/26.
 * Code is the law
 */
@FunctionalInterface
public interface RunnableWithException {
    void run() throws Throwable;
}
