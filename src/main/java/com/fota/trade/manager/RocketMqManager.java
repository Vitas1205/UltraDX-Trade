package com.fota.trade.manager;

import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.shade.com.alibaba.fastjson.JSONObject;
import com.fota.client.domain.OrderMessage;
import com.fota.trade.common.RocketMqProducer;
import com.fota.trade.domain.enums.OrderOperateTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/13 15:09
 * @Modified:
 */
@Component
@Slf4j
public class RocketMqManager {

    @Autowired
    private RocketMqProducer rocketMqProducer;

    public Boolean sendMessage(String topic, String tag, OrderMessage Message){
        Boolean ret = rocketMqProducer.producer("order", "UsdkOrder", Message.toString(), JSONObject.toJSONString(Message));
        return ret;
    }
}
