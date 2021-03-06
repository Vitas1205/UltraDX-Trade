package com.fota.trade.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Created by lds on 2018/10/20.
 * Code is the law
 */
@Configuration
public class SpringCacheConfig {
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(
                Arrays.asList(new ConcurrentMapCache("competitorsPriceOrder"),
                new ConcurrentMapCache("usdtPriceOrder"),
                new ConcurrentMapCache("spotIndexes"),
                new ConcurrentMapCache("usdtLatestPrice")
                        )
        );
        return cacheManager;
    }
}
