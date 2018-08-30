package com.fota.trade.manager;

import com.fota.match.domain.TradeUsdkOrder;
import com.fota.match.service.UsdkMatchedOrderService;
import com.fota.trade.domain.UsdkOrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

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

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Test
    @Ignore
    public void getJudegRetTest(){
        TradeUsdkOrder tradeUsdkOrder = new TradeUsdkOrder();
        tradeUsdkOrder.setId(444L);
        tradeUsdkOrder.setAssetId(2);
        tradeUsdkOrder.setOrderDirection(2);
        usdkMatchedOrderService.cancelOrderUsdk(tradeUsdkOrder);
    }

    @Test
    public void test_send_cancel_msg() {
        usdkOrderManager.sendCancelMessage(Arrays.asList(715669044238909L, 751658069641829L), 282L);
    }
}
