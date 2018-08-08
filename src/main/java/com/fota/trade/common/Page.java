package com.fota.trade.common;

import lombok.Data;

import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Data
public class Page<T> {
    private Integer pageNo;
    private Integer pageSize;
    private Integer total;
    private List<T> data;
}
