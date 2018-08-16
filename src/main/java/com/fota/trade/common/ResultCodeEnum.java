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
    /**
     * 业务异常
     */
    NO_CANCELLABLE_ORDERS(120001, "没有可以撤销的订单"),
    CLOSE_POSITION_FAILED(120002, "平仓失败,没有委托的买一卖一价"),
    CONTRACT_IS_DELIVERYING(120003, "操作失败，合约交割中"),
    CONTRACT_HAS_DELIVERIED(120004, "操作失败，合约已交割"),
    CONTRACT_IS_ROLLING_BACK(120005, "操作失败，合约回滚中"),
    PRICE_OUT_OF_RANGE(120006, "价格超出指定范围"),
    MODIFY_LEVER_FAILED(120007, "杠杆调整不符合保证金限制"),
    CANCEL_ORDER_FAILED(120008, "撤单失败"),
    UNFENISHED_ORDER(120009, "调整杠杆失败，有未完成的订单"),
    PARTLY_COMPLETED(120010, "全撤失败，部分订单撤销失败"),
    CONTRACT_ROLLBACK_FAILED(120011, "合约回滚失败"),
    ORDER_FAILED(120012, "下单失败"),
    USDT_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH(120013, "USDT钱包账户余额不足"),
    COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH(120014, "交易币种余额不足"),
    CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH(120015, "合约账户余额不足"),

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
