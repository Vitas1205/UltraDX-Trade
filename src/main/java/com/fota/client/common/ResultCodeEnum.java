package com.fota.client.common;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public enum  ResultCodeEnum {
    SUCCESS(0,"SUCCESS"),
    // 参数异常
    ILLEGAL_PARAM(11001, "ILLEGAL_PARAM"),
    DATABASE_EXCEPTION(11002, "DATABASE_EXCEPTION"),
    BEAN_COPY_EXCEPTION(11003, "BEAN_COPY_EXCEPTION"),
    SERVICE_EXCEPTION(11004, "SERVICE_EXCEPTION"),
    SERVICE_FAILED(11005, "SERVICE_FAILED"),

    //
    CREATE_USDKORDER_FAILED(12001,"CREATE_USDKORDER_FAILED"),
    UPDATE_USDKORDER_FAILED(12002,"UPDATE_USDKORDER_FAILED"),
    ORDER_IS_CANCLED(12003,"ORDER_HAS_BEEN_CANCLED"),
    ORDER_STATUS_ILLEGAL(12004,"ORDER_STATUS_ILLEGAL"),
    NO_CANCELLABLE_ORDERS(12005,"THERE_ARE_NO_CANCELLABLE_ORDERS"),
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
