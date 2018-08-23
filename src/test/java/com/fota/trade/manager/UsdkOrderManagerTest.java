package com.fota.trade.manager;

import com.fota.match.domain.TradeUsdkOrder;
import com.fota.match.service.UsdkMatchedOrderService;
import com.fota.trade.domain.UsdkOrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @Author huangtao 2018/8/23 下午6:41
 * @Description TODO
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class UsdkOrderManagerTest {

    @Autowired
    private UsdkMatchedOrderService usdkMatchedOrderService;
    @Autowired
    private RedisManager redisManager;

    @Test
    public void getJudegRetTest(){
        TradeUsdkOrder tradeUsdkOrder = new TradeUsdkOrder();
        tradeUsdkOrder.setId(444L);
        tradeUsdkOrder.setAssetId(2);
        tradeUsdkOrder.setOrderDirection(2);
        usdkMatchedOrderService.cancelOrderUsdk(tradeUsdkOrder);
    }
}
