package com.fota.trade.config;

import com.fota.data.manager.IndexCacheManager;
import com.fota.data.service.SpotIndexService;
import com.fota.risk.client.manager.RelativeRiskLevelManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;

/**
 * Created by lds on 2018/11/2.
 * Code is the law
 */
@Configuration
public class BeanConfig {

    @Autowired
    private SpotIndexService spotIndexService;
    @Autowired
    private RedisTemplate indexRedisTemplate;
    @Bean
    public RelativeRiskLevelManager relativeRiskLevelManager(RedisTemplate<String, Object> redisTemplate){
        RelativeRiskLevelManager relativeRiskLevelManager = new RelativeRiskLevelManager();
        relativeRiskLevelManager.setRedisTemplate(redisTemplate);
        return relativeRiskLevelManager;
    }
    @Bean
    public IndexCacheManager indexCacheManager() {
        return new IndexCacheManager(indexRedisTemplate, spotIndexService);
    }
}
