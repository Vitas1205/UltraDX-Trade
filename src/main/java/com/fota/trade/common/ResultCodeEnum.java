package com.fota.trade.common;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public enum ResultCodeEnum {
    SUCCESS(0,"SUCCESS"),
    BIZ_ERROR(-1, ""),
    SYSTEM_ERROR(100001, "SYSTEM_ERROR"),
    // 参数异常
    ILLEGAL_PARAM(11001, "ILLEGAL_PARAM"),
    DATABASE_EXCEPTION(11002, "DATABASE_EXCEPTION"),
    BEAN_COPY_EXCEPTION(11003, "BEAN_COPY_EXCEPTION"),
    SERVICE_EXCEPTION(11004, "SERVICE_EXCEPTION"),
    SERVICE_FAILED(11005, "SERVICE_FAILED"),
    ASSET_SERVICE_FAILED(11006, "SERVICE_FAILED"),

    //
    //=================================
    //业务异常
    //=================================

    //===================
    //下单code
    //==================

    CONTRACT_ACCOUNT_HAS_LIMITED(100003,"操作失败，合约账户接管中"),
    AMOUNT_ILLEGAL(101078, "金额不合法"),
    NO_CONTRACT_BALANCE(101079, "尚未开通合约账户"),
    PRICE_TYPE_ILLEGAL(101094, "价格类型不合法"),
    NO_COMPETITORS_PRICE(101095, "暂无对手价"),
    ILLEGAL_CONTRACT(101110,"无效合约"),
    PRICE_OUT_OF_BOUNDARY(101092, "价格超出指定范围"),
    ILLEGAL_ORDER_DIRECTION(101111, "非法订单方向"),
    CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH(120015, "合约账户余额不足"),


    NO_CANCELLABLE_ORDERS(120001, "NO_CANCELLABLE_ORDERS"),
    CLOSE_POSITION_FAILED(120002, "CLOSE_POSITION_FAILED"),
    CONTRACT_IS_DELIVERYING(120003, "合约正在交割中"),
    CONTRACT_HAS_DELIVERIED(120004, "合约已经交割"),
    CONTRACT_IS_ROLLING_BACK(120005, "合约正在回滚中"),
    MODIFY_LEVER_FAILED(120007, "MODIFY_LEVER_FAILED"),
    CANCEL_ORDER_FAILED(120008, "CANCEL_ORDER_FAILED"),
    UNFENISHED_ORDER(120009, "UNFENISHED_ORDER"),
    PARTLY_COMPLETED(120010, "PARTLY_COMPLETED"),
    //CONTRACT_ROLLBACK_FAILED(120011, "CONTRACT_ROLLBACK_FAILED"),
    ORDER_FAILED(120012, "ORDER_FAILED"),
    CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH(120013, "CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH"),
    COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH(120014, "COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH"),
    CANCEL_ALL_ORDER_FAILED(120016, "CANCEL_ALL_ORDER_FAILED"),
    ORDER_CAN_NOT_CANCLE(120018, "ORDER_CAN_NOT_CANCLE"),
    ENFORCE_ORDER_CANNOT_BE_CANCELED(120020, "ENFORCE_ORDER_CANNOT_BE_CANCELED"),
    BALANCE_NOT_ENOUGH(120021, "balance is not enough"),
    LOCK_FAILED(120022, "get lock failed"),
    CONCURRENT_PROBLEM(120023, "concurrent problem"),
    NO_LATEST_MATCHED_PRICE(120024, "NO_LATEST_MATCHED_PRICE"),
    TOO_MUCH_ORDERS(120024, "too much orders"),
    POSITION_EXCEEDS(120025, "position exceeds"),


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
