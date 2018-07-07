package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public enum  OrderPriceTypeEnum {

    /**
     * 限价单
     */
    LIMIT(1, "LIMIT"),
    /**
     * 市价单
     */
    MARKET(2, "MARKET"),
    ;
    @Getter
    private int code;
    private String desc;

    OrderPriceTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
