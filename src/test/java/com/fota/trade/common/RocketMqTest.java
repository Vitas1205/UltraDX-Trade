package com.fota.trade.common;


import com.fota.trade.test.BaseTest;
import com.fota.trade.manager.RocketMqManager;
import org.apache.rocketmq.common.message.Message;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

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
        String topic = "trade_test";
        String tag = "UsdkOrder";
        List<Message> t = Arrays.asList(new Message());
        when(rocketMqManager.sendMessage(anyList())).thenReturn(true);
        Boolean ret = rocketMqManager.sendMessage(t);
        assert ret;
    }

}
