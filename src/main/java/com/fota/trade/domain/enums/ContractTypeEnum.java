package com.fota.trade.domain.enums;

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
    SEASON(2, "SEASON"),
    ;
    private int code;
    private String desc;

    ContractTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
