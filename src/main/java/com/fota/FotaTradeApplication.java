package com.fota;

import com.fota.trade.Consumer;
import com.fota.trade.OrderConsumer;
import com.fota.trade.service.impl.ContractCategoryServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ImportResource;

import javax.annotation.PostConstruct;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RefreshScope
@SpringBootApplication
@ImportResource("classpath:application-context.xml")
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
