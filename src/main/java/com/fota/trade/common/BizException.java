package com.fota.trade.common;

/**
 * Created by Swifree on 2018/8/16.
 * Code is the law
 */
public class BizException extends RuntimeException {
    private Integer code;
    private String message;

    public BizException(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public BizException(ResultCodeEnum resultCodeEnum){
        this(resultCodeEnum.getCode(), resultCodeEnum.getMessage());
    }
}
