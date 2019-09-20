package com.fota.fotatrade;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fota.risk.client.domain.UserRRLDTO;
import com.fota.risk.client.manager.RelativeRiskLevelManager;
import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.manager.RedisManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private RedisManager redisManager;

    @Autowired
    private RealTimeEntrust realTimeEntrust;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
        private RelativeRiskLevelManager riskLevelManager;

    String testKey = "testKey";
    @Before
    public void init(){
        //初始化连接池
//        redisTemplate.opsForValue().get(testKey);
    }
    @Test
    public void RedisTest(){
        String redisKey = "mykey"+123;
        for (int i = 0;i <= 10;i++){
            long count = redisTemplate.opsForValue().increment(redisKey, 1);
        }
    }
    @Test
    public void testSerializable(){
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        String val = "testVal";
        String haskKey = "test_haskKey";
        String key = "test_key";
        redisTemplate.opsForHash().put(key, haskKey, val);

        Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        jackson2JsonRedisSerializer.setObjectMapper(om);

        assert val.equals(redisTemplate.opsForHash().get(key, haskKey));
    }
    @Test
    public void testGetRRL(){

        for (int i=0;i<10;i++) {
            List<UserRRLDTO> ret = riskLevelManager.range(1204L,
                    2, 0, -1);
            System.out.println(ret);
        }
    }
    @Test
    public void lockTest(){
        String lock = "TEST_LOCK";
        Duration expire = Duration.ofSeconds(3);
        boolean suc = redisManager.tryLock(lock, expire);
        assert suc;
        suc = redisManager.tryLock(lock, expire);
        assert !suc;

        long t = redisTemplate.getExpire(lock, TimeUnit.MILLISECONDS);
        System.out.println(t);
        assert t < expire.toMillis();
    }

    @Test
    public void mgetTest(){
//        List<String> result = redisTemplate.opsForValue().multiGet(Arrays.asList("57547_716254555967251", "57547_716254555967252"));
//        log.info("result={}", result);
    }
    @Test
    public void mset(){
        long start = System.currentTimeMillis();
        redisManager.setExPipelined(Arrays.asList("test_a", "test_b"),  "exist", 300);
        long end = System.currentTimeMillis();
        System.out.println("millis:" + (end - start));
    }

}
