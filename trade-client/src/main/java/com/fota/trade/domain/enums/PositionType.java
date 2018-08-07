package com.fota.trade.domain.enums;

import lombok.Getter;

/**
 * @author Gavin Shen
 * @Date 2018/8/7
 */
public enum PositionType {

    /**
     * 多仓
     */
    OVER(2, "OVER"),
    /**
     * 空仓
     */
    EMPTY(1, "EMPTY"),
    ;

    @Getter
    private int code;
    private String desc;

    PositionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }


}
