package com.fota.trade.common;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;

@UtilityClass
public class RedisKey {

    public String getUsdkDailyMaxShortAmountKey(Long userId) {
        return String.format("trade:usdkDailyMaxShortAmount:%s-%s", userId, LocalDate.now().toString());
    }

    public String getUsdkDailyMaxLongAmountKey(Long userId) {
        return String.format("trade:usdkDailyMaxLongAmount:%s-%s", userId, LocalDate.now().toString());
    }
}
