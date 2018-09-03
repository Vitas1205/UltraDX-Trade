package com.fota.trade.domain.enums;

import lombok.Getter;
/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public enum OrderCloseTypeEnum {

    /**
     * 手动平仓
     */
    MANUAL(1, "MANUAL"),
    /**
     * 系统平仓
     */
    SYSTEM(2, "SYSTEM"),
    /**
     * 交割
     */
    EXPIRED(3, "EXPIRED"),
    ;
    @Getter
    private int code;

    @Getter
    private String desc;

    OrderCloseTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }


}
