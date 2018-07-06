package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
public enum ContractStatusEnum {

    /**
     * 已删除
     */
    DELETED(0, "DELETED"),
    /**
     * 未开启
     */
    UNOPENED(1, "UNOPENED"),
    /**
     * 进行中
     */
    PROCESSING(2, "PROCESSING"),
    /**
     * 已交割
     */
    DELIVERED(3, "DELIVERED"),
    ;
    @Getter
    private int code;
    private String desc;

    ContractStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
