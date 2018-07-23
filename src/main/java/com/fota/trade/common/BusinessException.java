package com.fota.trade.common;

import lombok.Getter;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/20 16:14
 * @Modified:
 */
public class BusinessException extends Exception{
    @Getter
    private Integer code;
    private String mesage;

    public BusinessException(Integer code,String mesage){
        this.code = code;
        this.mesage = mesage;
    }


}
