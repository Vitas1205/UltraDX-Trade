package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/8/30
 */
public enum PositionStatusEnum {

    /**
     * 未交割
     */
    UNDELIVERED(1, "UNDELIVERED"),
    /**
     * 已交割
     */
    DELIVERED(2, "DELIVERED"),
    ;
    @Getter
    private int code;
    private String desc;

    PositionStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
