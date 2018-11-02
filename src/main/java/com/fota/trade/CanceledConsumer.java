package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fota.trade.client.BizTypeEnum;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.msg.BaseCanceledMessage;
import com.fota.trade.msg.TopicConstants;
import com.fota.trade.util.BasicUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.fota.trade.client.BizTypeEnum.COIN;
import static com.fota.trade.client.BizTypeEnum.CONTRACT;
import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;
import static com.fota.trade.msg.TopicConstants.MCH_CONTRACT_CANCEL_RST;

@Slf4j
@Component
public class CanceledConsumer {


    @Autowired
    private RedisManager redisManager;

    @Value("${spring.rocketmq.namesrv_addr}")
    private String namesrvAddr;
    @Value("${spring.rocketmq.group}")
    private String group;
    @Value("${spring.rocketmq.instanceName}")
    private String clientInstanceName;

    @Autowired
    private ContractOrderManager contractOrderManager;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Autowired
    private ObjectMapper objectMapper;

    DefaultMQPushConsumer coinCanceledConsumer;
    DefaultMQPushConsumer contractCanceledConsumer;

    private static final int removeSucced = 1;

    @PostConstruct
    public void init() throws MQClientException {
        coinCanceledConsumer = initCancelConsumer(TopicConstants.MCH_COIN_CANCEL_RST, (msgs, context) -> {
            return consumerCancelMessage(msgs, context, BizTypeEnum.COIN);
        });

        contractCanceledConsumer = initCancelConsumer(MCH_CONTRACT_CANCEL_RST,  (msgs, context) -> {
            return consumerCancelMessage(msgs, context, BizTypeEnum.CONTRACT);
        });
    }

    public DefaultMQPushConsumer initCancelConsumer(String topic, MessageListenerConcurrently messageListenerConcurrently) throws MQClientException {
        DefaultMQPushConsumer defaultMQPushConsumer = new DefaultMQPushConsumer(group + "_" +topic);
        defaultMQPushConsumer.setInstanceName(clientInstanceName);
        defaultMQPushConsumer.setNamesrvAddr(namesrvAddr);
        defaultMQPushConsumer.setMaxReconsumeTimes(3);
        defaultMQPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        defaultMQPushConsumer.setConsumeMessageBatchMaxSize(1);
        defaultMQPushConsumer.setVipChannelEnabled(false);
        defaultMQPushConsumer.subscribe(topic, "*");
        defaultMQPushConsumer.registerMessageListener(messageListenerConcurrently);
        //调用start()方法启动consumer
        defaultMQPushConsumer.start();

        return defaultMQPushConsumer;

    }

    public ConsumeConcurrentlyStatus consumerCancelMessage(final List<MessageExt> msgs,
                                                           final ConsumeConcurrentlyContext context, BizTypeEnum bizType) {
        if (CollectionUtils.isEmpty(msgs)) {
            log.error("message error!");
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        MessageExt messageExt = msgs.get(0);
        String mqKey = messageExt.getKeys();
        String tag = messageExt.getTags();

        try {

            byte[] bodyByte = messageExt.getBody();
            String bodyStr = new String(bodyByte, StandardCharsets.UTF_8);
            BaseCanceledMessage res = BasicUtils.exeWhitoutError(() -> JSON.parseObject(bodyStr, BaseCanceledMessage.class));
            if (null == res) {
                logFailMsg("resolve message failed, not retry", messageExt);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            if (!res.isSuccess()) {
                logSuccessMsg(messageExt, "remove from book orderList failed");
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            if (null == res.getUnfilledAmount() || null == res.getTotalAmount()) {
                logFailMsg("illegal message, not retry", messageExt);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            ResultCode resultCode = null;
            if (COIN.equals(bizType)) {
                resultCode = usdkOrderManager.cancelOrderByMessage(res);
            } else if (CONTRACT.equals(bizType)) {
                resultCode = contractOrderManager.cancelOrderByMessage(res);
            }
            if (!resultCode.isSuccess()) {

                if (resultCode.getCode() == ILLEGAL_PARAM.getCode()) {
                    logFailMsg("resultCode=" + resultCode, messageExt);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                logFailMsg("resultCode=" + resultCode, messageExt);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            logSuccessMsg(messageExt, null);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            logFailMsg(messageExt, e);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    private void logSuccessMsg(MessageExt messageExt, String extInfo) {
        String body = null;
        try {
            body = new String(messageExt.getBody(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("get mq message failed", e);
        }
        log.info("consume cancel message success, extInfo={}, msgId={}, msgKey={}, tag={},  body={}, reconsumeTimes={}", extInfo, messageExt.getMsgId(), messageExt.getKeys(), messageExt.getTags(),
                body, messageExt.getReconsumeTimes());
    }

    private void logFailMsg(MessageExt messageExt, Throwable t) {
        String body = null;
        try {
            body = new String(messageExt.getBody(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("get mq message failed", e);
        }
        log.error("consume cancel message exception, msgId={}, msgKey={}, tag={},  body={}, reconsumeTimes={}", messageExt.getMsgId(), messageExt.getKeys(), messageExt.getTags(),
                body, messageExt.getReconsumeTimes(), t);
    }

    private void logFailMsg(String cause, MessageExt messageExt) {
        String body = null;
        try {
            body = new String(messageExt.getBody(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("get mq message failed", e);
        }
        log.error("consume cancel message failed, cause={}, msgId={}, msgKey={}, tag={},  body={}, reconsumeTimes={}",
                cause, messageExt.getMsgId(), messageExt.getKeys(), messageExt.getTags(),
                body, messageExt.getReconsumeTimes());
    }

    @PreDestroy
    public void destory() {
        coinCanceledConsumer.shutdown();
        contractCanceledConsumer.shutdown();
    }
}

