package com.fota.trade.common;

import lombok.Data;
import lombok.Getter;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/20 16:14
 * @Modified:
 */
@Data
public class BusinessException extends Exception{
    private Integer code;
    private String message;

    public BusinessException(Integer code,String message){
        this.code = code;
        this.message = message;
    }

    public BusinessException(ResultCodeEnum resultCodeEnum){
        this(resultCodeEnum.getCode(), resultCodeEnum.getMessage());
    }


}
