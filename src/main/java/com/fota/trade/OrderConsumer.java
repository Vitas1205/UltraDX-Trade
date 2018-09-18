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
import com.fota.trade.service.impl.UsdkOrderServiceImpl;
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
import java.util.Iterator;
import java.util.Map;

import static com.fota.trade.common.Constant.MQ_REPET_JUDGE_KEY_ORDER;
import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;

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
            String lockKey = "LOCK_MESSAGE_KEY_" + mqKey;
            boolean locked = redisManager.tryLock(lockKey, Duration.ofMinutes(1));
            if (!locked) {
                logFailMsg("get lock failed!", messageExt);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            String tag = messageExt.getTags();
            //去重,如果已经撤销，不再处理
            String existKey = MQ_REPET_JUDGE_KEY_ORDER + mqKey;
            boolean isExist = null != redisManager.get(existKey);
            if (isExist) {
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

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
                    //如果订单簿移除失败，此消息消费成功，但是做去重，否则没法重试撤单
                    logSuccessMsg(messageExt, "remove from book orderList failed");
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                Long orderId = res.getLong("id");
                BigDecimal unfilledAmount = res.getBigDecimal("unfilledAmount");
                ResultCode resultCode = null;
                if ("UsdkCancelResult".equals(tag)) {
                        resultCode = usdkOrderManager.cancelOrderByMessage(orderId, unfilledAmount);
                } else if ("ContractCancelResult".equals(tag)) {
                    resultCode = contractOrderManager.cancelOrderByMessage(orderId, unfilledAmount);
                }
                if (!resultCode.isSuccess()) {
                    logFailMsg("resultCode="+resultCode, messageExt);
                    if (resultCode.getCode() == ILLEGAL_PARAM.getCode()) {
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                //撤销成功，标记
                redisManager.set(existKey, "1", Duration.ofDays(1));
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception e) {
                logFailMsg(messageExt, e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            } finally {
                redisManager.releaseLock(lockKey);
            }

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

