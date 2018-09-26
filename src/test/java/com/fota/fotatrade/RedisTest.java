package com.fota.fotatrade;

import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.BuyPriceSellPriceDTO;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.PositionTypeEnum;
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
import java.math.BigDecimal;
import java.time.Duration;
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
        //获取买一卖一价
        BigDecimal askCurrentPrice = BigDecimal.ZERO;
        BigDecimal bidCurrentPrice = BigDecimal.ZERO;
        long contractId = 1002L;
        List<CompetitorsPriceDTO> competitorsPriceList = realTimeEntrust.getContractCompetitorsPrice();
            bidCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.BID.getCode() &&
                    competitorsPrice.getId() == contractId).findFirst().get().getPrice();
            BigDecimal bidPositionEntrustAmount;
            askCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.ASK.getCode() &&
                    competitorsPrice.getId() == contractId).findFirst().get().getPrice();
            BigDecimal askPositionEntrustAmount;
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
    public void hSetTest(){
        String redisKey = "test20180917";
        //redisManager.hSetWithOutTime(redisKey,"index2", new BigDecimal("50.23"),1L);
        boolean ret = redisManager.exists(redisKey, "index3");
        log.info(""+ret);
    }

    @Test
    public void hGetTest(){
        String redisKey = "test20180917";
        BigDecimal ret = (BigDecimal) redisManager.hGet(redisKey,"index3");
        log.info(""+ret);
    }
}
