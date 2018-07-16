package com.fota.trade.common;


import com.fota.trade.domain.OrderMessage;
import com.fota.trade.domain.UsdkOrderDTO;
import com.fota.trade.domain.enums.OrderOperateTypeEnum;
import com.fota.trade.manager.RocketMqManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/13 16:02
 * @Modified:
 */
@SpringBootTest
@RunWith(SpringRunner.class)
public class RocketMqTest {

    @Resource
    RocketMqManager rocketMqManager;

    @Test
    public void RocketMqtest(){
        String topic = "order";
        String tag = "UsdkOrder";
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        usdkOrderDTO.setUserId(1);
        usdkOrderDTO.setAssetId(2);
        usdkOrderDTO.setAssetName("BTC");
        OrderMessage orderMessage = new OrderMessage();
        //orderMessage.setType(OrderOperateTypeEnum.PLACE_ORDER.getCode());
        //orderMessage.setMessage(usdkOrderDTO);
        Boolean ret = rocketMqManager.sendMessage(topic,tag,orderMessage);
    }
}
