package com.fota.fotatrade;

import com.fota.trade.manager.RedisManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/9 23:48
 * @Modified:
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RedisTest {

    @Autowired
    RedisManager redisManager;

    @Test
    public void RedisTest(){
        redisManager.set("mykey","1");
        Long ret = Long.valueOf(String.valueOf(redisManager.get("mykey")));
        ret = ret + 1;
        redisManager.set("mykey",ret);
        log.info("-----------------------------"+redisManager.get("mykey"));
    }
}
