package com.fota.trade.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.rocketmq.client.producer.MessageQueueSelector;

/**
 * Created by Swifree on 2018/9/20.
 * Code is the law
 */
@Data
@NoArgsConstructor
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
}
