package com.fota.trade.client;

/**
 * Created by lds on 2018/10/29.
 * Code is the law
 */
public enum  ADLPhaseEnum {
    PARSE,
    /**
     * 重新发送mq消息
     */
    RESEND,

    EXCEPTION,

    //======减仓阶段
    DL_PARSE,
    DL,
    DL_EXCEPTION,
    UNKNOW
    ;
}
