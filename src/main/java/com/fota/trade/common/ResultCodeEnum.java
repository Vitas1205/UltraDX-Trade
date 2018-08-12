package com.fota.trade.common;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public enum ResultCodeEnum {
    SUCCESS(0,"SUCCESS"),
    // 参数异常
    ILLEGAL_PARAM(11001, "ILLEGAL_PARAM"),
    DATABASE_EXCEPTION(11002, "DATABASE_EXCEPTION"),
    BEAN_COPY_EXCEPTION(11003, "BEAN_COPY_EXCEPTION"),
    SERVICE_EXCEPTION(11004, "SERVICE_EXCEPTION"),
    SERVICE_FAILED(11005, "SERVICE_FAILED"),
    ASSET_SERVICE_FAILED(11006, "SERVICE_FAILED"),

    //业务异常
    INSERT_USDK_ORDER_FAILED(12001, "INSERT_USDK_ORDER_FAILED"),
    UPDATE_USDK_ORDER_FAILED(12002, "UPDATE_USDK_ORDER_FAILED"),
    ORDER_IS_CANCLED(12003, "ORDER_HAS_BEEN_CANCLED"),
    ORDER_STATUS_ILLEGAL(12004, "ORDER_STATUS_ILLEGAL"),
    NO_CANCELLABLE_ORDERS(12005, "THERE_ARE_NO_CANCELLABLE_ORDERS"),
    ASK_AND_BID_ILLEGAL(12006, "ASK_AND_BID_ILLEGAL"),
    ASK_ILLEGAL(12007, "ASK_ILLEGAL"),
    BID_ILLEGAL(12008, "BID_ILLEGAL"),
    ORDER_UNFILLEDAMOUNT_NOT_ENOUGHT(12009, "ORDER_UNFILLEDAMOUNT_NOT_ENOUGHT"),
    UPDATE_USDK_CAPITAL_LOCKEDAMOUNT_FAILED(12010, "UPDATE_USDK_CAPITAL_LOCKEDAMOUNT_FAILED"),
    USDK_CAPITAL_AMOUNT_NOT_ENOUGH(12011, "USDK_CAPITAL_AMOUNT_NOT_ENOUGH"),
    UPDATE_COIN_CAPITAL_LOCKEDAMOUNT_FAILED(12012, "UPDATE_COIN_CAPITAL_LOCKEDAMOUNT_FAILED"),
    COIN_CAPITAL_AMOUNT_NOT_ENOUGH(12013, "COIN_CAPITAL_AMOUNT_NOT_ENOUGH"),
    ORDER_MATCH_UPDATE_BALANCE_FAILED(12014, "ORDER_MATCH_UPDATE_BALANCE_FAILED"),
    CONTRANCT_IS_NULL(12015, "CONTRANCT_IS_NULL"),
    INSERT_CONTRACT_ORDER_FAILED(12016, "INSERT_CONTRACT_ORDER_FAILED"),
    CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH(12017, "CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH"),
    UPDATE_CONTRACT_ACCOUNT_LOCKEDAMOUNT_FAILED(12018, "UPDATE_CONTRACT_ACCOUNT_LOCKEDAMOUNT_FAILED"),
    UPDATE_CONTRACT_ORDER_FAILED(12019, "UPDATE_CONTRACT_ORDER_FAILED"),
    GET_TOTAL_LOCKEDAMOUNT_FAILED(12020, "GET_TOTAL_LOCKEDAMOUNT_FAILED"),
    ORDER_CAN_NOT_CANCLE(12021, "ORDER_CAN_NOT_CANCLE"),
    UPDATE_MATCH_ORDER_FAILED(12022, "UPDATE_MATCH_ORDER_FAILED"),
    PARTLY_COMPLETED(12023, "PARTLY_COMPLETED"),



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
