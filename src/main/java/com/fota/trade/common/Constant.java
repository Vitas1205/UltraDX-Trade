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
    public static final String CONTRACT_COMPETITOR_PRICE_KEY = "fota_contract_competitor_price";
    public static final String USDK_COMPETITOR_PRICE_KEY = "fota_usdk_competitor_price";
    public static final BigDecimal CONTRACT_SIZE = new BigDecimal("0.01");
    public static final BigDecimal FEE_RATE = new BigDecimal("0.001");
    public static final String CACHE_KEY_MATCH_CONTRACT = "CACHE_KEY_MATCH_CONTRACT_";
    public static final String CACHE_KEY_MATCH_USDK = "CACHE_KEY_MATCH_USDK_";
    public static final String MQ_REPET_JUDGE_KEY_TRADE = "MQ_REPET_JUDGE_KEY_TRADE";
    public static final String ENTRUST_MARGIN = "entrustMargin";
    public static final String POSITION_MARGIN = "positionMargin";
    public static final String FLOATING_PL = "floatingPL";
    public static final String LAST_USDT_MATCH_PRICE = "FOTA_LATEST_USDT_MATCHED_ORDER_";
    public static final String LAST_CONTRACT_MATCH_PRICE = "FOTA_LATEST_CONTRACT_MATCHED_ORDER_";
    public static final String REDIS_USDT_CANCEL_ORDER_RESULT = "cancel_usdt_order_result";
    public static final String REDIS_CONTRACT_CANCEL_ORDER_RESULT = "cancel_contract_order_result";

}
