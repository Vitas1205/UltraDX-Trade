package com.fota;

import com.fota.trade.Consumer;
import com.fota.trade.OrderConsumer;
import com.fota.trade.config.MarketAccountListConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ImportResource;

import javax.annotation.PostConstruct;

@Slf4j
@RefreshScope
@SpringBootApplication
@ImportResource("classpath:application-context.xml")
@EnableConfigurationProperties(MarketAccountListConfig.class)
@EnableCaching
public class FotaTradeApplication {

    @Autowired
    private Consumer consumer;

    @Autowired
    private OrderConsumer orderConsumer;

    public static void main(String[] args) {
        SpringApplication.run(FotaTradeApplication.class, args);
    }

    @PostConstruct
    public void runConsumer() {
        try {
            consumer.init();
            orderConsumer.init();
            log.error("runConsumer success");

        } catch (Exception e) {
            log.error("runConsumer failed", e);
        }
    }
}
