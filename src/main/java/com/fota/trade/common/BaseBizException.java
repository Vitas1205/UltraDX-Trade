package com.fota.trade.common;

import lombok.Data;

@Data
public class BaseBizException extends RuntimeException{
    private BizExceptionEnum bizException;

    public BaseBizException(BizExceptionEnum bizExceptionEnum) {
        this.bizException = bizExceptionEnum;
    }

    public BaseBizException(Throwable cause, BizExceptionEnum bizExceptionEnum) {
        super(cause);
        this.bizException = bizExceptionEnum;
    }

    public BaseBizException(String message, BizExceptionEnum bizExceptionEnum) {
        super(bizExceptionEnum + "," + message);
        this.bizException = bizExceptionEnum;
    }
}
