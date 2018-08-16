package com.fota.trade;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.ContractMatchedOrderDTO;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.UsdkMatchedOrderDTO;
import com.fota.trade.domain.enums.TagsTypeEnum;
import com.fota.trade.manager.RedisManager;
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
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    UsdkOrderServiceImpl usdkOrderService;
    @Autowired
    RedisManager redisManager;
    @Value("${spring.rocketmq.namesrv_addr}")
    private String namesrvAddr;

    @Autowired
    ContractOrderServiceImpl contractOrderService;
    public void init() throws InterruptedException, MQClientException {
        //声明并初始化一个consumer
        //需要一个consumer group名字作为构造方法的参数，这里为consumer1
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("fota-trade-testenv");
        //同样也要设置NameServer地址
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setMaxReconsumeTimes(30);
        //这里设置的是一个consumer的消费策略
        //CONSUME_FROM_LAST_OFFSET 默认策略，从该队列最尾开始消费，即跳过历史消息
        //CONSUME_FROM_FIRST_OFFSET 从队列最开始开始消费，即历史消息（还储存在broker的）全部消费一遍
        //CONSUME_FROM_TIMESTAMP 从某个时间点开始消费，和setConsumeTimestamp()配合使用，默认是半个小时以前
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setVipChannelEnabled(false);
        //设置consumer所订阅的Topic和Tag，*代表全部的Tag
        consumer.subscribe("match_order", "usdk || contract");
        //设置一个Listener，主要进行消息的逻辑处理
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                if (CollectionUtils.isEmpty(msgs)) {
                    log.error("message error!");
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                for (MessageExt messageExt:msgs){
                    try {
                        String mqKey = messageExt.getKeys();
                        Map<String, Integer> keyMap = (Map<String, Integer>) redisManager.get(Constant.MQ_REPET_JUDGE_KEY);
                        if (keyMap != null && keyMap.containsKey(mqKey)) {
                            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                        }
                        String tag = messageExt.getTags();
                        byte[] bodyByte = messageExt.getBody();
                        String bodyStr = null;
                        try {
                            bodyStr = new String(bodyByte, "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            log.error("get mq message failed", e);
                        }
                        log.info("bodyStr()------------" + bodyStr);
                        if (TagsTypeEnum.USDK.getDesc().equals(tag)) {
                            UsdkMatchedOrderDTO usdkMatchedOrderDTO = JSON.parseObject(bodyStr, UsdkMatchedOrderDTO.class);
                            ResultCode resultCode = usdkOrderService.updateOrderByMatch(usdkMatchedOrderDTO);
                            log.info("resultCode---------------" + resultCode);
                            if (resultCode != null && resultCode.getCode() == 12002) {
                                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                            }
                        } else if (TagsTypeEnum.CONTRACT.getDesc().equals(tag)) {
                            ContractMatchedOrderDTO contractMatchedOrderDTO = JSON.parseObject(bodyStr, ContractMatchedOrderDTO.class);
                            ResultCode resultCode = contractOrderService.updateOrderByMatch(contractMatchedOrderDTO);
                            log.info("resultCode---------------" + resultCode);
                            if (resultCode != null && resultCode.getCode() == 12002) {
                                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                            }
                        }
                        if (keyMap == null) {
                            keyMap = new HashMap<>(1);
                        }
                        keyMap.put(mqKey, 0);
                        redisManager.set(Constant.MQ_REPET_JUDGE_KEY, keyMap);
                    } catch (Exception e) {
                        log.error("trade consume error ", e);
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        //调用start()方法启动consumer
        consumer.start();
        System.out.println("Consumer Started.");
    }
}

