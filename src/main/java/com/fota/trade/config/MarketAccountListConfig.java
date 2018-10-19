package com.fota.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "trade")
public class MarketAccountListConfig {

    private List<Long> marketAccountList;
}
