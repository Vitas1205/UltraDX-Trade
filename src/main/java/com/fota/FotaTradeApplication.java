package com.fota;

import com.fota.trade.config.MarketAccountListConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@RefreshScope
@SpringBootApplication
@ImportResource("classpath:application-context.xml")
@EnableConfigurationProperties(MarketAccountListConfig.class)
//@EnableCaching
@EnableScheduling
public class FotaTradeApplication {


    public static void main(String[] args) {
        SpringApplication.run(FotaTradeApplication.class, args);
    }

}
