package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/19 15:53
 * @Modified:
 */
public enum CloseTypeEnum {
    /**
     *用户委托枚举
     */
    USER_ENTRUST(1, "USER_ENTRUST"),
    /**
     *系统强命枚举
     */
    SYSTEM_CLOSE(2, "SYSTEM_CLOSE"),
    /**
     *交割枚举
     */
    DELIVERY(3, "DELIVERY"),

    ;
    @Getter
    private int code;
    private String desc;

    CloseTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
