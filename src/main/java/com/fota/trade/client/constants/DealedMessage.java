package com.fota.trade.client.constants;

import lombok.Data;

/**
 * Created by Swifree on 2018/9/22.
 * Code is the law
 */
@Data
public class DealedMessage {
    private long userId;
    /**
     *USDT
     *CONTRACT
     */
    private String subjectType;
    private long subjectId;
}
