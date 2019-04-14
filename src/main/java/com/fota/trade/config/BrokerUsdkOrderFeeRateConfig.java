package com.fota.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: Harry Wang
 * @Date: 2019/4/9 14:44
 * @Version 1.0
 */
@Data
@ConfigurationProperties(prefix = "trade")
@Component
public class BrokerUsdkOrderFeeRateConfig {
    private List<String> brokerUsdkOrderFeeRateList;
}
