package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public enum OrderDirectionEnum {

    /**
     * εε
     */
    ASK(1, "ASK"),
    /**
     * δΉ°ε
     */
    BID(2, "BID"),
    ;
    @Getter
    private int code;
    private String desc;

    OrderDirectionEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderDirectionEnum oppositeDirection(OrderDirectionEnum directionEnum){
        if (directionEnum == ASK) {
            return BID;
        }
        return ASK;
    }
}
