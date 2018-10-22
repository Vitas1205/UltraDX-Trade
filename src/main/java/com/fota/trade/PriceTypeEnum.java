package com.fota.trade;

import lombok.Getter;
import lombok.Setter;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/1 17:07
 * @Modified:
 */
public enum PriceTypeEnum {
    RIVAL_PRICE("RIVAL_PRICE", 0),
    SPECIFIED_PRICE("SPECIFIED_PRICE ", 1),
    MARKET_PRICE("MARKET_PRICE ", 2),
    ;

    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private int code;

    PriceTypeEnum(String name, int code) {
        this.code = code;
        this.name = name;
    }
}
