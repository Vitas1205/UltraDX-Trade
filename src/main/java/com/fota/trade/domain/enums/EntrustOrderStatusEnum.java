package com.fota.trade.domain.enums;


import lombok.Getter;
import lombok.Setter;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午11:29 2018/7/5
 * @Modified:
 */
public enum EntrustOrderStatusEnum {

    PLACE_ORDER(8, "PLACE_ORDER"),
    PARTIAL_SUCCESS(9, "PARTIAL_SUCCESS"),
    ;

    @Getter@Setter
    private Integer code;
    private String desc;

    EntrustOrderStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
