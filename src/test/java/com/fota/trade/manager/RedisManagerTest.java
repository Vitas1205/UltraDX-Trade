package com.fota.trade.manager;

import com.fota.trade.domain.UsdkOrderDO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.PublicKey;
import java.util.Set;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/26 16:02
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class RedisManagerTest {
    @Autowired
    RedisManager redisManager;

    @Test
    public void sSetTest(){
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        usdkOrderDO.setId(9997L);
        redisManager.sSet("sSetTest_KEY",usdkOrderDO);
    }

    @Test
    public void sGetTest(){
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        usdkOrderDO.setId(9999L);
        long ret = redisManager.sRemove("sSetTest_KEY",usdkOrderDO);
        Set set = redisManager.sMember("sSetTest_KEY");
        log.info(set.toString());
    }
}
