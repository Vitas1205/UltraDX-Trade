package com.fota.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "trade")
public class BlackListConfig {

    private List<Long> blackList;
}
