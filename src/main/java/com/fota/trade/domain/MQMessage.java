package com.fota.trade.domain;

import com.aliyun.openservices.ons.api.Message;
import com.fota.common.utils.LogUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.MessageExt;

import java.io.UnsupportedEncodingException;

import static com.fota.trade.client.constants.Constants.UTF8;
import static com.fota.trade.common.TradeBizTypeEnum.COMMON;

/**
 * Created by Swifree on 2018/9/20.
 * Code is the law
 */
@Data
@NoArgsConstructor
@Slf4j
public class MQMessage {

    private String topic;

    private String tag;

    private String key;

    private Object message;

    private MessageQueueSelector queueSelector;

    private Object queueSelectorArg;

    public MQMessage(String topic, String tag, String key, Object message) {
        this.topic = topic;
        this.tag = tag;
        this.key = key;
        this.message = message;
    }

    public MQMessage(String topic, String tag, String key, Object message, MessageQueueSelector queueSelector, Object queueSelectorArg) {
        this.topic = topic;
        this.tag = tag;
        this.key = key;
        this.message = message;
        this.queueSelector = queueSelector;
        this.queueSelectorArg = queueSelectorArg;
    }
    public static MQMessage of(Message messageExt) {
        String body;
        try {
            body = new String(messageExt.getBody(), UTF8);
        } catch (UnsupportedEncodingException e) {
            LogUtil.error( COMMON, null, messageExt.getBody(), "UnsupportedEncodingException "+UTF8);
            return null;
        }
        return new MQMessage(messageExt.getTopic(), messageExt.getTag(), messageExt.getKey(), body);
    }
}
