package com.fota.trade.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by lds on 2018/10/29.
 * Code is the law
 */
public class DistinctFilter {

    /**
     * If a key could not be put into ConcurrentHashMap, that means the key is duplicated
     * @param keyExtractor a mapping function to produce keys
     * @param <T> the type of the input elements
     * @return true if key is duplicated; else return false
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> map = new ConcurrentHashMap<>();
        return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}
