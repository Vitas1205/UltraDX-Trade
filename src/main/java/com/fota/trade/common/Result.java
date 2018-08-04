package com.fota.trade.common;

import lombok.Data;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;


    public Result<T> success(T data) {
        this.code = 0;
        this.data = data;
        return this;
    }

    public Result<T> error(ResultCodeEnum resultCodeEnum) {
        this.code = resultCodeEnum.getCode();
        this.message = resultCodeEnum.getMessage();
        return this;
    }

    public static <T> Result<T> create() {
        return new Result<>();
    }


    public boolean isSuccess() {
        return this.code == 0;
    }

}
