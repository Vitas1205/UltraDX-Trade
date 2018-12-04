package com.fota.trade.common;

public class ADLBizException extends BaseBizException{
    public ADLBizException(BizExceptionEnum bizExceptionEnum) {
        this(bizExceptionEnum.name(), bizExceptionEnum);
    }

    public ADLBizException(Throwable cause, BizExceptionEnum bizExceptionEnum) {
        super(cause, bizExceptionEnum);
    }

    public ADLBizException(String message, BizExceptionEnum bizExceptionEnum) {
        super(message, bizExceptionEnum);
    }
}
