package com.fota.trade.client;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Created by lds on 2018/10/20.
 * Code is the law
 */
@Data
public class OrderResult implements Serializable {
    Map<String, Object> entrustInternalValues;
    Integer lever;
}
