package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fota.common.utils.LogUtil;
import com.fota.trade.common.TradeBizTypeEnum;
import com.fota.trade.domain.MQMessage;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.msg.BaseCanceledMessage;
import com.fota.trade.msg.TopicConstants;
import com.fota.trade.util.BasicUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.AccessChannel;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;
import static com.fota.trade.common.TradeBizTypeEnum.COIN_CANCEL_ORDER;

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

    @Value("${spring.ones.acl.secretKey}")
    private String aclSecretKey;

    @Value("${spring.ones.acl.accessKey}")
    private String aclAccessKey;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Autowired
    private ObjectMapper objectMapper;

    DefaultMQPushConsumer coinCanceledConsumer;

    private static final int removeSucced = 1;

    @PostConstruct
    public void init() throws MQClientException {
        coinCanceledConsumer = initCancelConsumer(TopicConstants.MCH_COIN_CANCEL_RST, (msgs, context) -> {
            return consumerCancelMessage(msgs, context, TradeBizTypeEnum.COIN_CANCEL_ORDER);
        });
    }

    public DefaultMQPushConsumer initCancelConsumer(String topic, MessageListenerConcurrently messageListenerConcurrently) throws MQClientException {
        DefaultMQPushConsumer defaultMQPushConsumer = new DefaultMQPushConsumer(group, new AclClientRPCHook(new SessionCredentials(aclAccessKey, aclSecretKey)), new AllocateMessageQueueAveragely());

        defaultMQPushConsumer.setInstanceName(clientInstanceName);
        defaultMQPushConsumer.setNamesrvAddr(namesrvAddr);
        defaultMQPushConsumer.setMaxReconsumeTimes(16);
        defaultMQPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        defaultMQPushConsumer.setConsumeMessageBatchMaxSize(1);
        defaultMQPushConsumer.setVipChannelEnabled(false);
        defaultMQPushConsumer.subscribe(topic, "*");
        defaultMQPushConsumer.registerMessageListener(messageListenerConcurrently);
        defaultMQPushConsumer.setAccessChannel(AccessChannel.CLOUD);
        //调用start()方法启动consumer
        defaultMQPushConsumer.start();

        return defaultMQPushConsumer;

    }

    public ConsumeConcurrentlyStatus consumerCancelMessage(final List<MessageExt> msgs,
                                                           final ConsumeConcurrentlyContext context, TradeBizTypeEnum bizType) {
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
                logErrorMsg(bizType, "resolve message failed, not retry", messageExt);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            if (!res.isSuccess()) {
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            if (null == res.getUnfilledAmount() || null == res.getTotalAmount()) {
                logErrorMsg(bizType, "illegal message, not retry", messageExt);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            ResultCode resultCode = null;
            if (COIN_CANCEL_ORDER.equals(bizType)) {
                resultCode = usdkOrderManager.cancelOrderByMessage(res);
            }
            if (!resultCode.isSuccess()) {
                if (resultCode.getCode() == ILLEGAL_PARAM.getCode()) {
                    logErrorMsg(bizType, "resultCode=" + resultCode, messageExt);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                logErrorMsg(bizType, "resultCode=" + resultCode, messageExt);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            logErrorMsg(bizType, messageExt, e);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }


    private void logErrorMsg(TradeBizTypeEnum bizType, MessageExt messageExt, Throwable t) {
        String errorMsg = String.format("consumeTimes:%s ",  messageExt.getReconsumeTimes());
        LogUtil.error( bizType, messageExt.getKeys(), MQMessage.of(messageExt),
                errorMsg, t);
    }

    private void logErrorMsg(TradeBizTypeEnum bizType, String cause, MessageExt messageExt) {
        String errorMsg = String.format("cause:%s, consumeTimes:%s ", cause, messageExt.getReconsumeTimes());
        LogUtil.error( bizType, messageExt.getKeys(), MQMessage.of(messageExt),
                errorMsg);
    }

    @PreDestroy
    public void destory() {
        coinCanceledConsumer.shutdown();
    }
}

