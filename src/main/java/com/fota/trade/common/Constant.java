package com.fota.trade.common;

import java.math.BigDecimal;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
public class Constant {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_MAX_PAGE_SIZE = 50;
    public static final String USDK_ORDER_HEAD = "fota_usdk_entrust_";
    public static final String CONTRACT_ORDER_HEAD = "fota_contract_entrust_";
    public static final String USDK_REDIS_KEY = "usdk_key";
    public static final String CONTRACT_REDIS_KEY = "contract_key";

    public static final BigDecimal FEE_RATE = new BigDecimal("0.001");

}
