package com.fota.trade.manager;

import com.alibaba.fastjson.JSONObject;
import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.List;
import java.util.Set;

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

    @Test
    public void getContractCompetitorsPriceTest(){
        long contractId = 1000L;
        //获取买一卖一价
        BigDecimal askCurrentPrice = BigDecimal.ZERO;
        BigDecimal bidCurrentPrice = BigDecimal.ZERO;
        try{
            List<CompetitorsPriceDTO> competitorsPriceList = realTimeEntrust.getContractCompetitorsPrice();
            askCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.ASK.getCode() &&
                    competitorsPrice.getId() == contractId).limit(1).collect(toList()).get(0).getPrice();
            bidCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.BID.getCode() &&
                    competitorsPrice.getId() == contractId).limit(1).collect(toList()).get(0).getPrice();
            //log.info("askCurrentPrice:{}",askCurrentPrice);
            //log.info("bidCurrentPrice:{}",bidCurrentPrice);
        }catch (Exception e){
            //log.error("getContractBuyPriceSellPriceDTO failed{}",e);
        }
    }
}
