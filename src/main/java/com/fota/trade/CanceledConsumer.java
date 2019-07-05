package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fota.common.utils.LogUtil;
import com.fota.trade.common.TradeBizTypeEnum;
import com.fota.trade.domain.MQMessage;
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
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;
import static com.fota.trade.common.TradeBizTypeEnum.COIN_CANCEL_ORDER;
import static com.fota.trade.common.TradeBizTypeEnum.CONTRACT_CANCEL_ORDER;
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
            return consumerCancelMessage(msgs, context, TradeBizTypeEnum.COIN_CANCEL_ORDER);
        });

        contractCanceledConsumer = initCancelConsumer(MCH_CONTRACT_CANCEL_RST,  (msgs, context) -> {
            return consumerCancelMessage(msgs, context, TradeBizTypeEnum.CONTRACT_CANCEL_ORDER);
        });
    }

    public DefaultMQPushConsumer initCancelConsumer(String topic, MessageListenerConcurrently messageListenerConcurrently) throws MQClientException {
        DefaultMQPushConsumer defaultMQPushConsumer = new DefaultMQPushConsumer(group + "_" +topic);
        defaultMQPushConsumer.setInstanceName(clientInstanceName);
        defaultMQPushConsumer.setNamesrvAddr(namesrvAddr);
        defaultMQPushConsumer.setMaxReconsumeTimes(16);
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
            } else if (CONTRACT_CANCEL_ORDER.equals(bizType)) {
                resultCode = contractOrderManager.cancelOrderByMessage(res);
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
            // todo 非正常情况下出现的撤单失败，把失败的订单保持起来，定时任务去重试
            // 现货还是期货、orderId（用于查询这个订单的状态）、topic、tag、key、message
            // bizType:COIN_CANCEL_ORDER,^_traceId:601287347634942,^_param:MQMessage(topic=match_coin_cancel_rst, tag=coin, key=601287347634942,
            // message={"code":"SUCCESS","subjectId":2200001,"userId":521562,"orderId":601287347634942,"unfilledAmount":44531,"totalAmount":44531,"averagePrice":null,"success":true,"status":4}

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
        contractCanceledConsumer.shutdown();
    }
}

