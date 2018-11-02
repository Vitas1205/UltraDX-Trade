package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.fota.trade.client.BizTypeEnum;
import com.fota.trade.domain.ContractMatchedOrderDTO;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.UsdkMatchedOrderDTO;
import com.fota.trade.domain.enums.TagsTypeEnum;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.msg.TopicConstants;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import com.fota.trade.service.impl.UsdkOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.*;
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
import java.time.Duration;
import java.util.List;

import static com.fota.trade.client.BizTypeEnum.COIN;
import static com.fota.trade.client.BizTypeEnum.CONTRACT;
import static com.fota.trade.common.Constant.MQ_REPET_JUDGE_KEY_MATCH;
import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/25 21:04
 * @Modified:
 */
@Slf4j
@Component
public class MatchedConsumer {

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

    DefaultMQPushConsumer coinMatchedConsumer;
    DefaultMQPushConsumer contractMatchedConsumer;
    @Autowired
    private ContractOrderServiceImpl contractOrderService;
    @PostConstruct
    public void init() throws InterruptedException, MQClientException {

        coinMatchedConsumer = initMatchedConsumer(TopicConstants.MCH_COIN_MATCH,
                (List<MessageExt> msgs, ConsumeConcurrentlyContext context) ->  consumeMatchedMessage(msgs, context, COIN)
        );

        coinMatchedConsumer = initMatchedConsumer(TopicConstants.MCH_CONTRACT_MATCH,
                (List<MessageExt> msgs, ConsumeConcurrentlyContext context) ->  consumeMatchedMessage(msgs, context, CONTRACT)
        );



    }

    public DefaultMQPushConsumer initMatchedConsumer(String topic, MessageListenerConcurrently listenerConcurrently) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group + "_"+ topic);
        consumer.setInstanceName(clientInstanceName);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setMaxReconsumeTimes(10);
        //这里设置的是一个consumer的消费策略
        //CONSUME_FROM_LAST_OFFSET 默认策略，从该队列最尾开始消费，即跳过历史消息
        //CONSUME_FROM_FIRST_OFFSET 从队列最开始开始消费，即历史消息（还储存在broker的）全部消费一遍
        //CONSUME_FROM_TIMESTAMP 从某个时间点开始消费，和setConsumeTimestamp()配合使用，默认是半个小时以前
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setVipChannelEnabled(false);
        consumer.subscribe(topic, "*");
        consumer.setConsumeMessageBatchMaxSize(1);
        return consumer;
    }

    public ConsumeConcurrentlyStatus consumeMatchedMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context, BizTypeEnum bizType){
        if (CollectionUtils.isEmpty(msgs)) {
            log.error("message error!");
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        MessageExt messageExt = msgs.get(0);

        String mqKey = messageExt.getKeys();


        ResultCode resultCode = null;

        String existKey = MQ_REPET_JUDGE_KEY_MATCH  + messageExt.getTags() + "_" + mqKey;
        //判断是否已经成交
        boolean locked = redisManager.tryLock(existKey, Duration.ofHours(1));
        if (!locked) {
            logSuccessMsg(messageExt, "already consumed, not retry");
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        try {
            byte[] bodyByte = messageExt.getBody();
            String bodyStr = null;
            try {
                bodyStr = new String(bodyByte, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                log.error("get mq message failed", e);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            log.info("receive match message, ------------" + bodyStr);
            if (TagsTypeEnum.USDK.getDesc().equals(bizType)) {
                UsdkMatchedOrderDTO usdkMatchedOrderDTO = JSON.parseObject(bodyStr, UsdkMatchedOrderDTO.class);
                resultCode = usdkOrderService.updateOrderByMatch(usdkMatchedOrderDTO);

            } else if (BizTypeEnum.CONTRACT.equals(bizType)) {
                ContractMatchedOrderDTO contractMatchedOrderDTO = JSON.parseObject(bodyStr, ContractMatchedOrderDTO.class);
                resultCode = contractOrderService.updateOrderByMatch(contractMatchedOrderDTO);
            }

            if (!resultCode.isSuccess()) {
                logFailMsg("resultCode="+resultCode, messageExt);
                if (resultCode.getCode() == ILLEGAL_PARAM.getCode()) {
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                redisManager.del(existKey);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            //一定要成交成功才能标记
            redisManager.set(existKey, "1", Duration.ofDays(1));
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            logFailMsg(messageExt, e);
            redisManager.del(existKey);
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
        log.info("consume message success, extInfo={}, msgId={}, msgKey={}, tag={},  body={}, reconsumeTimes={}",extInfo,  messageExt.getMsgId(), messageExt.getKeys(), messageExt.getTags(),
                body, messageExt.getReconsumeTimes());
    }
    private void logFailMsg(MessageExt messageExt, Throwable t) {
        String body = null;
        try {
            body = new String(messageExt.getBody(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("get mq message failed", e);
        }
        log.error("consume message exception, msgId={}, msgKey={}, tag={},  body={}, reconsumeTimes={}", messageExt.getMsgId(), messageExt.getKeys(), messageExt.getTags(),
                body, messageExt.getReconsumeTimes(), t);
    }
    private void logFailMsg(String cause, MessageExt messageExt) {
        String body = null;
        try {
            body = new String(messageExt.getBody(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("get mq message failed", e);
        }
        log.error("consume message failed, cause={}, msgId={}, msgKey={}, tag={},  body={}, reconsumeTimes={}",
                cause, messageExt.getMsgId(), messageExt.getKeys(), messageExt.getTags(),
                body, messageExt.getReconsumeTimes());
    }

    @PreDestroy
    public void destory(){
        coinMatchedConsumer.shutdown();
        contractMatchedConsumer.shutdown();
    }
}

