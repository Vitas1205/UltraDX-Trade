package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/8/7
 */
public enum OrderDirection {

    /**
     * 卖单
     */
    ASK(1, "ASK"),
    /**
     * 买单
     */
    BID(2, "BID"),
    ;
    @Getter
    private int code;
    private String desc;

    OrderDirection(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
