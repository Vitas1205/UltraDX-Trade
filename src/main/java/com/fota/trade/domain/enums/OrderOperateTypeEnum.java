package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/13 15:02
 * @Modified:
 */

public enum OrderOperateTypeEnum {
    /**
     * 下单
     */
    PLACE_ORDER(1, "PLACE_ORDER"),
    /**
     * 撤单
     */
    CANCLE_ORDER(2, "CANCLE_ORDER"),

    ;
    @Getter
    private int code;
    private String desc;

    OrderOperateTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
