package com.fota.trade.mapper.common;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
public interface BaseMapper<T> {

    int insert(T t);

    int update(T t);

    T getById(Long id);

    int delete(Long id);

}
