package com.fota.fotatrade;

import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.BuyPriceSellPriceDTO;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.manager.RedisManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
    RedisManager redisManager;

    @Autowired
    private RealTimeEntrust realTimeEntrust;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    String testKey = "testKey";
    @Before
    public void init(){
        //初始化连接池
        redisTemplate.opsForValue().get(testKey);
    }
    @Test
    public void RedisTest(){
        String redisKey = "mykey"+123;
        for (int i = 0;i <= 10;i++){
            long count = redisTemplate.opsForValue().increment(redisKey, 1);
            log.info("fota_usdk_entrust_"+count);
        }
    }

    @Test
    public void RedisGetTest(){
        String redisKey = "fota_competitor_price";
        long st;
        st = System.currentTimeMillis();
        Object obj = redisManager.get(redisKey);
        log.info("costOfQuery={}, result={}", System.currentTimeMillis() - st,obj);
    }

    @Test
    public void testRealTimeEntrust() {
        List<CompetitorsPriceDTO> list =  realTimeEntrust.getContractCompetitorsPrice();
        list.forEach(System.out::println);
    }
    @Test
    public void lockTest(){
        String lock = "TEST_LOCK";
        long timeout = 3;
        long st;

        st = System.currentTimeMillis();
        boolean suc = redisManager.tryLock(lock, timeout);
        log.info("costOfLock={}", System.currentTimeMillis() - st);
        assert suc;

        long sum = 0, count = 10;
        for (int i=0;i<count;i++){
            st = System.currentTimeMillis();
            Object obj = redisManager.get(lock);
            long cost = System.currentTimeMillis() - st;
            sum += cost;
            log.info("costOfQuery={}, result={}", cost,obj);
        }
        log.info("averageCost={}" ,sum*1.0/count);

        long t = redisTemplate.getExpire(lock, TimeUnit.MILLISECONDS);
        assert t < timeout;
    }
}
