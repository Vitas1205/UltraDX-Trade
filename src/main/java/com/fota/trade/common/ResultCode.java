package com.fota.trade.common;

import lombok.Data;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Data
public class ResultCode {
    private int code;
    private String message;

    public static ResultCode success() {
        ResultCode resultCode = new ResultCode();
        resultCode.code = 0;
        return resultCode;
    }

    public static ResultCode error(int code, String message) {
        ResultCode resultCode = new ResultCode();
        resultCode.code = code;
        resultCode.message = message;
        return resultCode;
    }

    public static ResultCode error(ResultCodeEnum resultCodeEnum) {
        ResultCode resultCode = new ResultCode();
        resultCode.code = resultCodeEnum.getCode();
        resultCode.message = resultCodeEnum.getMessage();
        return resultCode;
    }

    public boolean isSuccess() {
        return this.code == 0;
    }

}
