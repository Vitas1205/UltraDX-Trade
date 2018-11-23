package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.trade.common.ListSplitter;
import com.fota.trade.domain.MQMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    private long timeout = 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger("sendMQMessageFailed");
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
            LOGGER.error(JSON.toJSONString(mqMessage));
        }
        return suc;
    }


    public boolean sendMessage(String topic, String tag, String key, Object message,  MessageQueueSelector selector, Object args) {
        MQMessage mqMessage = new MQMessage(topic, tag, key, message, selector, args);
        boolean suc = doSendMessage(mqMessage);
        if (!suc) {
            LOGGER.error(JSON.toJSONString(mqMessage));
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

    public <T>  boolean  batchSendMessage(String topic, Function<T, String> tagSupplier, Function<T, String> keySupplier, List<T> msgs) {
        if (CollectionUtils.isEmpty(msgs)) {
            return true;
        }
        List<MQMessage> mqMessages = msgs.stream().map(msg -> {
            MQMessage message = new MQMessage();
            message.setTopic(topic);
            message.setMessage(msg);
            message.setTag(tagSupplier.apply(msg));
            message.setKey(keySupplier.apply(msg));
            return message;
        }).collect(Collectors.toList());
        boolean suc = doSendMessage(mqMessages);
        if (!suc) {
            LOGGER.error(JSON.toJSONString(mqMessages));
        }
        return suc;
    }

    public boolean doSendMessage(@NonNull List<MQMessage> mqMessages){

        if (CollectionUtils.isEmpty(mqMessages)) {
            return true;
        }
        List<Message> messageList = mqMessages.stream().map(msg -> {
            Message message = new Message();
            message.setTopic(msg.getTopic());
            message.setBody(JSON.toJSONBytes(msg.getMessage()));
            message.setTags(msg.getTag());
            message.setKeys(msg.getKey());
            return message;
        }).collect(Collectors.toList());
        ListSplitter splitter = new ListSplitter(messageList);
        try {
            while (splitter.hasNext()) {
                List<Message>  listItem = splitter.next();
                SendResult ret = null; // 消息在3S内没有发送成功，就会重试
                ret = producer.send(listItem);
                if (SEND_OK == ret.getSendStatus()) {
                    log.info("send message success, mqMessage={}, ret={}", listItem, ret);
                }else {
                    log.error("send message failed, mqMessage={}, ret={}", listItem, JSON.toJSONString(ret));
                }
            }
            return true;
        } catch (Exception e) {
            log.error("send message failed, mqMessage={}, exceptionMsg={}", mqMessages, e.getMessage());
            return false;
        }
    }


}
