package com.fota.fotatrade;

import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.BuyPriceSellPriceDTO;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.PositionTypeEnum;
import com.fota.trade.manager.RedisManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
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
    private RedisTemplate<String, String> redisTemplate;

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
        //从redis获取今天手续费
        Date date = new Date();
        SimpleDateFormat sdf1 =new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat sdf2 =new SimpleDateFormat("H");
        int hours = Integer.valueOf(sdf2.format(date));
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, 1);
        String dateStr = hours < 18 ? sdf1.format(date) : sdf1.format(calendar.getTime());
        BigDecimal totalFee =  BigDecimal.valueOf((Double)redisManager.get(Constant.REDIS_TODAY_FEE + dateStr));
        if (totalFee == null){
            log.error("totalFee not exeist, rediskey:{}", Constant.REDIS_TODAY_FEE + dateStr);
            totalFee = BigDecimal.ZERO;
        }
        log.info("totalFee"+totalFee);
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
    public void mgetTest(){
        List<String> result = redisTemplate.opsForValue().multiGet(Arrays.asList("57547_716254555967251", "57547_716254555967252"));
        log.info("result={}", result);
    }
    @Test
    public void mset(){
        long start = System.currentTimeMillis();
        redisManager.setExPipelined(Arrays.asList("test_a", "test_b"),  "exist", 300);
        long end = System.currentTimeMillis();
        System.out.println("millis:" + (end - start));
    }

}
