package com.fota.trade.client.constants;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Created by Swifree on 2018/9/22.
 * Code is the law
 */
@Data
@Accessors(chain = true)
public class DealedMessage {
    public static final String USDT_TYPE = "USDT";
    public static final String CONTRACT_TYPE = "CONTRACT";
    private long userId;
    /**
     *USDT
     *CONTRACT
     */
    private String subjectType;
    private long subjectId;
}
