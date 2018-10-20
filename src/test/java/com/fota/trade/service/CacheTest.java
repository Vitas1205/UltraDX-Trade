package com.fota.trade.service;

/**
 * Created by lds on 2018/10/20.
 * Code is the law
 */

import com.fota.trade.manager.RealTimeEntrustManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class CacheTest {
    @Resource
    private RealTimeEntrustManager realTimeEntrustManager;
    @Test
    public void testSpringCache(){
        List res = realTimeEntrustManager.getContractCompetitorsPriceOrder();
        log.info("res={}", res);
    }
}
