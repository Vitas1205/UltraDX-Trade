package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.util.ConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;

@Slf4j
@Component
public class OrderConsumer {


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

    private static final int removeSucced=1;

    public void init() throws InterruptedException, MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group + "-cancel");
        consumer.setInstanceName(clientInstanceName);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setMaxReconsumeTimes(3);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setVipChannelEnabled(false);
        consumer.subscribe("order", "UsdkCancelResult || ContractCancelResult");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
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
                JSONObject res = ConvertUtils.resolveCancelResult(bodyStr);
                if (null == res) {
                    logFailMsg("resolve message failed, not retry", messageExt);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                Integer removeResult = res.getInteger("rst");
                if (null == removeResult || removeSucced != removeResult) {
                    logSuccessMsg(messageExt, "remove from book orderList failed");
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                Long orderId = res.getLong("id");
                BigDecimal unfilledAmount = res.getBigDecimal("unfilledAmount");
                Long userId = res.getLong("userId");
                if (null == orderId || null == unfilledAmount || null == userId) {
                    logFailMsg("illegal message, not retry", messageExt);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                ResultCode resultCode = null;
                if ("UsdkCancelResult".equals(tag)) {
                        resultCode = usdkOrderManager.cancelOrderByMessage(orderId, unfilledAmount);
                } else if ("ContractCancelResult".equals(tag)) {
                    resultCode = contractOrderManager.cancelOrderByMessage(userId, orderId, unfilledAmount);
                }
                if (!resultCode.isSuccess()) {

                    if (resultCode.getCode() == ILLEGAL_PARAM.getCode()) {
                        logFailMsg("resultCode="+resultCode, messageExt);
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                    logFailMsg("resultCode="+resultCode, messageExt);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                logSuccessMsg(messageExt, null);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception e) {
                logFailMsg(messageExt, e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }

        });
        //调用start()方法启动consumer
        consumer.start();
        System.out.println("order Consumer Started .");

    }
    private void logSuccessMsg(MessageExt messageExt, String extInfo) {
        String body = null;
        try {
            body = new String(messageExt.getBody(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("get mq message failed", e);
        }
        log.info("consume cancel message success, extInfo={}, msgId={}, msgKey={}, tag={},  body={}, reconsumeTimes={}",extInfo,  messageExt.getMsgId(), messageExt.getKeys(), messageExt.getTags(),
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
}

