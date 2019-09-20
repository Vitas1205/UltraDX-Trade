package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/25 21:26
 * @Modified:
 */
public enum TagsTypeEnum {
    /**
     * USDK成交
     */
    USDK(1, "usdk")
    ;

    @Getter
    private int code;
    @Getter
    private String desc;

    TagsTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
