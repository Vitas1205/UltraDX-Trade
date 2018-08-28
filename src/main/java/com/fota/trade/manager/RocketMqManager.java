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
    DefaultMQProducer producer;

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
                log.info("向Topic：{}，发送消息：{}，消息发送成功", topic, pushMsg);
            }else if (SendStatus.FLUSH_DISK_TIMEOUT == result.getSendStatus()){
                log.error("向Topic：{}，发送消息：{}，消息发送成功，但是服务器刷盘超时，消息已经进入服务器队列，只有此时服务器宕机，消息才会丢失", topic, pushMsg);
            }else if (SendStatus.FLUSH_SLAVE_TIMEOUT == result.getSendStatus()){
                log.error("向Topic：{}，发送消息：{}，消息发送成功，但是服务器同步到Slave时超时，消息已经进入服务器队列，只有此时服务器宕机，消息才会丢失", topic, pushMsg);
            }else if (SendStatus.SLAVE_NOT_AVAILABLE == result.getSendStatus()){
                log.error("向Topic：{}，发送消息：{}，消息发送成功，但是此时slave不可用，消息已经进入服务器队列，只有此时服务器宕机，消息才会丢失", topic, pushMsg);
            }
        } catch (Exception e) {
            log.error("向Topic：{}，发送消息：{}，消息发送失败", topic, pushMsg);
            e.printStackTrace();
        }
        return result;
    }


}
