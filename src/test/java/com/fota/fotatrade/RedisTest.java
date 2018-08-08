package com.fota.fotatrade;

import com.fota.trade.manager.RedisManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/9 23:48
 * @Modified:
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class RedisTest {

    @Autowired
    RedisManager redisManager;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Test
    public void RedisTest(){
        String redisKey = "mykey"+123;
        for (int i = 0;i <= 10;i++){
            long count = redisTemplate.opsForValue().increment(redisKey, 1);
            log.info("------------"+String.valueOf(count));
            log.info("fota_usdk_entrust_"+count);
        }
    }

    @Test
    public void RedisGetTest(){
        String redisKey = "fota_competitor_price";
        Object obj = redisManager.get(redisKey);
        log.info("---------------"+obj);

    }
}
