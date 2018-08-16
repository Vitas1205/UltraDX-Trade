package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/9 19:51
 * @Modified:
 */
public enum AssetTypeEnum {
    /**
     * USDT枚举值
     */
    USDT(1, "USDT"),
    /**
     * 以太坊枚举值
     */
    ETH(2, "ETH"),
    /**
     * 比特币枚举值
     */
    BTC(3, "BTC"),
    /**
     * FOTA枚举值
     */
    FOTA(4, "FOTA")
    ;
    @Getter
    private int code;
    private String desc;

    AssetTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

