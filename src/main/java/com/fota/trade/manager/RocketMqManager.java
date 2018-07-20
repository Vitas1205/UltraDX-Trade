package com.fota.trade.manager;

import com.alibaba.fastjson.JSONObject;
import com.fota.trade.common.RocketMqProducer;
import com.fota.trade.domain.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


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

    public Boolean sendMessage(String topic, String tag, OrderMessage message){
        Boolean ret = rocketMqProducer.producer("order", tag, message.toString(), JSONObject.toJSONString(message));
        return ret;
    }
}
