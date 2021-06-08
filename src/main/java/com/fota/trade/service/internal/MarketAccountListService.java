package com.fota.trade.service.internal;

import com.alibaba.fastjson.JSON;
import com.fota.trade.config.MarketAccountListConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class MarketAccountListService {

    private MarketAccountListConfig marketAccountListConfig;

    public boolean contains(Long userId) {
        List<Long> marketAccountList = marketAccountListConfig.getMarketAccountList();
        log.info("contains marketAccountList:{}", JSON.toJSONString(marketAccountList));
        return marketAccountListConfig.getMarketAccountList() != null
                && marketAccountListConfig.getMarketAccountList().contains(userId);
    }

    @Autowired
    public void setMarketAccountListConfig(MarketAccountListConfig marketAccountListConfig) {
        this.marketAccountListConfig = marketAccountListConfig;
    }
}
