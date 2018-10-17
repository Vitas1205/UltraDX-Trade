package com.fota.trade.service.internal;

import com.fota.trade.config.MarketAccountListConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MarketAccountListService {

    private MarketAccountListConfig marketAccountListConfig;

    public boolean contains(Long userId) {
        return marketAccountListConfig.getMarketAccountList() != null
                && marketAccountListConfig.getMarketAccountList().contains(userId);
    }

    @Autowired
    public void setMarketAccountListConfig(MarketAccountListConfig marketAccountListConfig) {
        this.marketAccountListConfig = marketAccountListConfig;
    }
}
