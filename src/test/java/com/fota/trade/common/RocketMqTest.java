package com.fota.trade.common;


import com.fota.trade.manager.RocketMqManager;
import com.fota.trade.test.BaseTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/13 16:02
 * @Modified:
 */

public class RocketMqTest extends BaseTest {

    @Autowired
    private RocketMqManager rocketMqManager;

    @Test
    public void RocketMqtest(){
//        String topic = "trade_test";
//        String tag = "UsdkOrder";
//        List<Message> t = Arrays.asList(new Message());
//        when(rocketMqManager.sendMessage(anyList())).thenReturn(true);
//        Boolean ret = rocketMqManager.sendMessage(t);
//        assert ret;
    }

}
