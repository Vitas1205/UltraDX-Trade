package com.fota.trade.manager;

import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/26 16:02
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class RedisManagerTest {
    @Autowired
    private RedisManager redisManager;
    @Autowired
    RealTimeEntrust realTimeEntrust;

    @Test
    public void sSetTest(){
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        usdkOrderDO.setId(9996L);
        //redisManager.sSet("sSetTest_KEY",JSONObject.toJSONString(usdkOrderDO));
    }

    @Test
    public void sGetTest(){
//        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
//        usdkOrderDO.setId(9999L);
//        long ret = redisManager.sRemove("sSetTest_KEY",JSONObject.toJSONString(usdkOrderDO));
        //Set set = redisManager.sMember("sSetTest_KEY");
        //log.info(set.toString());
    }

    @Test
    public void hSetTest(){
        /*Set set = redisManager.hSet("hSetTest_KEY", "01", "value1");
        log.info(set.toString());*/
    }
}
