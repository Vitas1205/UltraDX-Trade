package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.trade.domain.MQMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static com.fota.trade.client.constants.Constants.DEAL_TOPIC;
import static com.fota.trade.client.constants.Constants.DEFAULT_TAG;
import static org.apache.rocketmq.client.producer.SendStatus.SEND_OK;


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
    private DefaultMQProducer producer;

    @Resource
    private ConcurrentMap<String, String> failedMQMap;
    private long timeout = 1000;
    /**
     *
     * @param topic
     * @param tag
     * @param key
     * 唯一标识码，代表这条消息的业务关键词，服务器会根据keys创建哈希索引，设置后，可以在Console系统根据Topic、Keys来查询消息，
     * 由于是哈希索引，请尽可能保证key唯一，例如订单号，商品Id等

     * @param message
     * @return
     */
    public boolean sendMessage(String topic, String tag, String key, Object message){
        MQMessage mqMessage = new MQMessage(topic, tag, key, message);
        boolean suc =  doSendMessage(mqMessage);
        if (!suc) {
            failedMQMap.put(topic+"_"+key, JSON.toJSONString(mqMessage));
        }
        return suc;
    }


    public boolean sendMessage(String topic, String tag, String key, Object message,  MessageQueueSelector selector, Object args) {
        MQMessage mqMessage = new MQMessage(topic, tag, key, message, selector, args);
        boolean suc = doSendMessage(mqMessage);
        if (!suc) {
            failedMQMap.put(topic+"_"+key, JSON.toJSONString(mqMessage));
        }
        return suc;
    }

    public boolean doSendMessage(@NonNull MQMessage mqMessage){

        String msgStr = JSON.toJSONString(mqMessage.getMessage());
        Message message = new Message();
        try {
            message.setTopic(mqMessage.getTopic());
            message.setBody(msgStr.getBytes("UTF-8"));
            message.setTags(mqMessage.getTag());
            message.setKeys(mqMessage.getKey());
            SendResult ret = null; // 消息在1S内没有发送成功，就会重试
            if (null == mqMessage.getQueueSelector()) {
                ret = producer.send(message, timeout);

            }else {
                ret = producer.send(message, mqMessage.getQueueSelector(), mqMessage.getQueueSelectorArg(), timeout);
            }
            if (SEND_OK == ret.getSendStatus()) {
                log.info("send message success, mqMessage={}, ret={}", mqMessage, ret);
                return true;
            }else {
                log.error("send message failed, mqMessage={}, ret={}", message, JSON.toJSONString(ret));
                return false;
            }
        } catch (Exception e) {
            log.error("send message failed, mqMessage={}, exceptionMsg={}", mqMessage, e.getMessage());
            return false;
        }
    }


}
