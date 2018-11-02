package com.fota.trade.config;

import com.fota.risk.client.manager.RelativeRiskLevelManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Created by lds on 2018/11/2.
 * Code is the law
 */
@Configuration
public class BeanConfig {
    @Bean
    public RelativeRiskLevelManager relativeRiskLevelManager(@Autowired RedisTemplate<String, Object> redisTemplate){
        RelativeRiskLevelManager relativeRiskLevelManager = new RelativeRiskLevelManager();
        relativeRiskLevelManager.setRedisTemplate(redisTemplate);
        return relativeRiskLevelManager;
    }
}
