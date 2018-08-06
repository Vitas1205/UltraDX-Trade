package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/6 22:18
 * @Modified:
 */
public enum ContractStatus {
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


    /**
     * 交割中
     */


    /**
     * 回滚中
     */

    ;
    @Getter
    private int code;
    @Getter
    private String desc;

    ContractStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
