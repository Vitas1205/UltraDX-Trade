package com.fota.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.service.impl.UsdkOrderServiceImpl;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

import static com.fota.trade.common.Constant.MQ_REPET_JUDGE_KEY_ORDER;

@Slf4j
@Component
public class OrderConsumer {

    @Autowired
    private UsdkOrderServiceImpl usdkOrderService;

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

    public void init() throws InterruptedException, MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group + "-cancel");
        consumer.setInstanceName(clientInstanceName);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setMaxReconsumeTimes(3);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setVipChannelEnabled(false);
        consumer.subscribe("order", "UsdkCancelResult || ContractCancelResult");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            if (CollectionUtils.isEmpty(msgs)) {
                log.error("message error!");
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            for (MessageExt messageExt : msgs) {
                String mqKey = messageExt.getKeys();
                String lockKey = "LOCK_MESSAGE_KEY_" + mqKey;
                boolean locked = redisManager.tryLock(lockKey, Duration.ofMinutes(1));
                if (!locked) {
                    logFailMsg("get lock failed!", messageExt);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                try {
                    String existKey = MQ_REPET_JUDGE_KEY_ORDER + mqKey;
                    boolean isExist = null != redisManager.get(existKey);
                    if (isExist) {
                        logSuccessMsg(messageExt, "already consumed, not retry");
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                    String tag = messageExt.getTags();
                    byte[] bodyByte = messageExt.getBody();
                    String bodyStr = new String(bodyByte, StandardCharsets.UTF_8);
                    log.info("order bodyStr()------------" + bodyStr);
                    ObjectNode result = objectMapper.readValue(bodyStr, ObjectNode.class);
                    String orderId = null;
                    int status = 0;
                    Iterator<Map.Entry<String, JsonNode>> fields = result.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        orderId = entry.getKey();
                        status = entry.getValue().asInt();
                        break;
                    }
                    if ("UsdkCancelResult".equals(tag)) {
                        usdkOrderManager.cancelOrderByMessage(orderId, status);
                    } else if ("ContractCancelResult".equals(tag)) {
                        ResultCode resultCode = contractOrderManager.cancelOrderByMessage(Long.parseLong(orderId), status);
                        if (!resultCode.isSuccess()) {
                            log.error("cancel message failed, messageKey={}, resultCode={}", mqKey, resultCode);
                        }
                    }
                    redisManager.set(existKey, "1", Duration.ofDays(1));
                } catch (Exception e) {
                    logFailMsg(messageExt, e);
                } finally {
                    redisManager.releaseLock(lockKey);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        //调用start()方法启动consumer
        consumer.start();
        System.out.println("Consumer Started.");
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

