package com.fota.trade.manager;

import com.alibaba.fastjson.JSONObject;
import com.fota.trade.common.RocketMqProducer;
import com.fota.trade.domain.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
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

    @Autowired
    private DefaultMQProducer producer;

    public Boolean sendMessage(String topic, String tag, String key, Object message){
        Boolean ret = rocketMqProducer.producer(topic, tag, key, JSONObject.toJSONBytes(message));
        return ret;
    }

    public SendResult producer(String topic, String tag, String key, String pushMsg){

        SendResult result = null;
        try {
            Message msg = new Message(topic,tag,key, pushMsg.getBytes("UTF-8"));
            result = producer.send(msg, 1000); // 消息在1S内没有发送成功，就会重试

            if (SendStatus.SEND_OK == result.getSendStatus()){
                log.info("send message success, topic={}, tag={}, msgId={}, key={}, msg={}", topic, tag, result.getMsgId(), key, pushMsg);
            }else {
                log.error("send message failed, topic={}, tag={},  key={}, msg={}, result={}", topic, tag, key, pushMsg, result);
            }
        } catch (Exception e) {
            log.error("send message exception, topic={}, tag={},  key={}, msg={}", topic, tag, key, pushMsg, e);
        }
        return result;
    }


}
