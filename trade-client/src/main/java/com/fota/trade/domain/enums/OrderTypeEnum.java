package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/8/7
 */
public enum OrderTypeEnum {

    /**
     * 限价单
     */
    LIMIT(1, "LIMIT"),
    /**
     * 市价单
     */
    MARKET(2, "MARKET"),
    /**
     * 市价单
     */
    ENFORCE(3, "ENFORCE"),
    ;
    @Getter
    private int code;
    private String desc;

    OrderTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
