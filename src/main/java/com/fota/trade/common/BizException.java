package com.fota.trade.common;

import lombok.Data;

/**
 * Created by Swifree on 2018/8/31.
 * Code is the law
 */
@Data
public class BizException extends RuntimeException{
    private Integer code;
    private String message;

    public BizException(Integer code,String message){
        this.code = code;
        this.message = message;
    }

    public BizException(ResultCodeEnum resultCodeEnum){
        this(resultCodeEnum.getCode(), resultCodeEnum.getMessage());
    }


}

