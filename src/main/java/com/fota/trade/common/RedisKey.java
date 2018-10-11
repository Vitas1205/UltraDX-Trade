package com.fota.trade.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RedisKey {

    public String getUserContractPositionExtraKey(Long userId) {
        return String.format("trade:userContractPE:%d", userId);
    }
}
