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
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/13 16:02
 * @Modified:
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@Transactional
public class RocketMqTest {

    @Resource
    RocketMqManager rocketMqManager;

    @Test
    public void RocketMqtest(){
        String topic = "order";
        String tag = "UsdkOrder";
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        usdkOrderDTO.setUserId(1l);
        usdkOrderDTO.setAssetId(2);
        usdkOrderDTO.setAssetName("BTC");
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
        /*orderMessage.setUserId(001);
        orderMessage.setSubjectId(2);*/
        Boolean ret = rocketMqManager.sendMessage(topic,tag,orderMessage);
    }
}
