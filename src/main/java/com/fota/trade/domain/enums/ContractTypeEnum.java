package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
public enum  ContractTypeEnum {

    /**
     * 周
     */
    WEEK(1, "WEEK"),
    /**
     * MONTH
     */
    MONTH(2, "MONTH"),
    /**
     * SEASON
     */
    SEASON(3, "SEASON"),
    ;
    @Getter
    private int code;
    @Getter
    private String desc;

    ContractTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
