package com.fota.client.common;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public enum  ResultCodeEnum {

    // 参数异常
    ILLEGAL_PARAM(11001, "ILLEGAL_PARAM"),
    DATABASE_EXCEPTION(11002, "DATABASE_EXCEPTION"),
    BEAN_COPY_EXCEPTION(11003, "BEAN_COPY_EXCEPTION"),
    ;
    @Getter
    private int code;
    @Getter
    private String message;

    ResultCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
