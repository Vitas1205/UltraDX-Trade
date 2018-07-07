package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public enum OrderStatusEnum {

    /**
     * 部撤
     */
    PART_CANCEL(3, "PART_CANCEL"),
    /**
     * 已撤
     */
    CANCEL(4, "CANCEL"),
    /**
     * 已报
     */
    COMMIT(8, "COMMIT"),
    /**
     * 部成
     */
    PART_MATCH(9, "PART_MATCH"),
    /**
     * 全成
     */
    MATCH(10, "MATCH")

    ;
    @Getter
    private int code;
    private String desc;

    OrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
