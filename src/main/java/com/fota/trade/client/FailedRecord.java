package com.fota.trade.client;

import com.alibaba.fastjson.JSON;
import com.fota.common.Result;
import lombok.Data;

/**
 * Created by lds on 2018/10/29.
 * Code is the law
 */
@Data
public class FailedRecord {

    public static final int RETRY = 1;
    public static final int NOT_RETRY = 2;
    public static final int NOT_SURE = 3;
    private Integer retryType;
    private String phase;
    private Object parameter;
    private Result result;
    String code;
    String message;

    public FailedRecord(Integer retryType, String phase, Object parameter) {
        this.retryType = retryType;
        this.phase = phase;
        this.parameter = parameter;
    }

    public FailedRecord(Integer retryType, String phase, Object parameter, String code, String message) {
        this.retryType = retryType;
        this.phase = phase;
        this.parameter = parameter;
        this.code = code;
        this.message = message;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
