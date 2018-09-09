package com.fota.trade;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.ContractMatchedOrderDTO;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.UsdkMatchedOrderDTO;
import com.fota.trade.domain.enums.TagsTypeEnum;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import com.fota.trade.service.impl.UsdkOrderServiceImpl;
import com.fota.trade.util.CommonUtils;
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
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fota.trade.common.ResultCodeEnum.BALANCE_NOT_ENOUGH;
import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/25 21:04
 * @Modified:
 */
@Slf4j
@Component
public class Consumer {

    @Autowired
    private UsdkOrderServiceImpl usdkOrderService;
    @Autowired
    private RedisManager redisManager;
    @Value("${spring.rocketmq.namesrv_addr}")
    private String namesrvAddr;
    @Value("${spring.rocketmq.group}")
    private String group;

    @Autowired
    private ContractOrderServiceImpl contractOrderService;
    public void init() throws InterruptedException, MQClientException {
        //声明并初始化一个consumer
        //需要一个consumer group名字作为构造方法的参数，这里为consumer1
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group + "-match");
        //同样也要设置NameServer地址
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setMaxReconsumeTimes(32);
        //这里设置的是一个consumer的消费策略
        //CONSUME_FROM_LAST_OFFSET 默认策略，从该队列最尾开始消费，即跳过历史消息
        //CONSUME_FROM_FIRST_OFFSET 从队列最开始开始消费，即历史消息（还储存在broker的）全部消费一遍
        //CONSUME_FROM_TIMESTAMP 从某个时间点开始消费，和setConsumeTimestamp()配合使用，默认是半个小时以前
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setVipChannelEnabled(false);
        //设置consumer所订阅的Topic和Tag，*代表全部的Tag
        consumer.subscribe("match_order", "usdk || contract");
        consumer.setConsumeMessageBatchMaxSize(1);
        //设置一个Listener，主要进行消息的逻辑处理
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {

                log.info("consumeMessage jjj {}", msgs.size());
                if (CollectionUtils.isEmpty(msgs)) {
                    log.error("message error!");
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                MessageExt messageExt = msgs.get(0);

                String mqKey = messageExt.getKeys();
                log.info("consumeMessage jjj {}", mqKey);


                ResultCode resultCode = null;

                try {

                    boolean isExist = redisManager.sHasKey(Constant.MQ_REPET_JUDGE_KEY_TRADE, mqKey);
                    if (isExist) {
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                    String tag = messageExt.getTags();
                    byte[] bodyByte = messageExt.getBody();
                    String bodyStr = null;
                    try {
                        bodyStr = new String(bodyByte, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        log.error("get mq message failed", e);
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                    log.info("bodyStr()------------" + bodyStr);
                    if (TagsTypeEnum.USDK.getDesc().equals(tag)) {
                        UsdkMatchedOrderDTO usdkMatchedOrderDTO = JSON.parseObject(bodyStr, UsdkMatchedOrderDTO.class);
                        resultCode = usdkOrderService.updateOrderByMatch(usdkMatchedOrderDTO);
                        log.info("resultCode u---------------" + resultCode);

                    } else if (TagsTypeEnum.CONTRACT.getDesc().equals(tag)) {
                        ContractMatchedOrderDTO contractMatchedOrderDTO = JSON.parseObject(bodyStr, ContractMatchedOrderDTO.class);
                        resultCode = contractOrderService.updateOrderByMatch(contractMatchedOrderDTO);
                        log.info("resultCode c---------------" + resultCode);

                    }

                    if (resultCode != null && resultCode.getCode() != null && !resultCode.isSuccess()
                            && !(resultCode.getCode() == ILLEGAL_PARAM.getCode()) ) {
                        logFailMsg(null, messageExt);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                    redisManager.sSet(Constant.MQ_REPET_JUDGE_KEY_TRADE, mqKey);
                } catch (Exception e) {
                    logFailMsg(messageExt, e);
                }
                if (resultCode.getCode() == ILLEGAL_PARAM.getCode()) {
                    logSuccessMsg(messageExt, "illegal param or balance is not enough");
                }else {
                    logSuccessMsg(messageExt, null);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
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
        log.error("consume message success, extInfo={}, msgId={}, msgKey={}, tag={},  body={}, reconsumeTimes={}",extInfo,  messageExt.getMsgId(), messageExt.getKeys(), messageExt.getTags(),
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
}

