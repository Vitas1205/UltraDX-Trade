package com.fota.trade.service;

import com.fota.trade.manager.CurrentPriceManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/10/18 14:07
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class SpotIndexServiceTest {


    @Autowired
    private CurrentPriceManager currentPriceManager;

    @Test
    public void getIndexByCurrentPriceManagerTest(){
//        List<TickerDTO> list = currentPriceManager.getSpotIndexes();
//        TickerDTO tickerDTO = list.stream().filter(x->x.getSymbol().equals("ETH")).findFirst().get();
//        log.info("list={}", JSON.toJSONString(list));
    }
}
