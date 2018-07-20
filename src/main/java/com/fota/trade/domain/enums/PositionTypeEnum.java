package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/20 14:07
 * @Modified:
 */
public enum  PositionTypeEnum {
    /**
     * 多仓
     */
    OVER(1, "OVER"),
    /**
     * 空仓
     */
    EMPTY(2, "EMPTY"),
    ;

    @Getter
    private int code;
    private String desc;

    PositionTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
