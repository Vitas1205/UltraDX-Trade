package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.fota.trade.client.PostDealMessage;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.DealManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fota.trade.client.constants.Constants.CONTRACT_POSITION_UPDATE_TOPIC;
import static com.fota.trade.client.constants.Constants.DEFAULT_TAG;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/25 21:04
 * @Modified:
 */
@Slf4j
@Component
public class PostDealConsumer {

    @Autowired
    private DealManager dealManager;

    @Autowired
    private ContractOrderManager contractOrderManager;

    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RedisManager redisManager;

    @Value("${spring.rocketmq.namesrv_addr}")
    private String namesrvAddr;
    @Value("${spring.rocketmq.group}")
    private String group;

    @Value("${spring.rocketmq.instanceName}")
    private String clientInstanceName;


    String EXIST_POST_DEAL = "EXIST_POST_DEAL_";
    long seconds = 24 * 3600;

    @Autowired
    private ContractOrderServiceImpl contractOrderService;

    @PostConstruct
    public void init() throws InterruptedException, MQClientException {
        //声明并初始化一个consumer
        //需要一个consumer group名字作为构造方法的参数，这里为consumer1
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group + "-postDeal");
        consumer.setInstanceName(clientInstanceName);
        //同样也要设置NameServer地址
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setMaxReconsumeTimes(10);
        //这里设置的是一个consumer的消费策略
        //CONSUME_FROM_LAST_OFFSET 默认策略，从该队列最尾开始消费，即跳过历史消息
        //CONSUME_FROM_FIRST_OFFSET 从队列最开始开始消费，即历史消息（还储存在broker的）全部消费一遍
        //CONSUME_FROM_TIMESTAMP 从某个时间点开始消费，和setConsumeTimestamp()配合使用，默认是半个小时以前
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setVipChannelEnabled(false);
        //设置consumer所订阅的Topic和Tag，*代表全部的Tag
        consumer.subscribe(CONTRACT_POSITION_UPDATE_TOPIC, DEFAULT_TAG);
        consumer.setConsumeMessageBatchMaxSize(100);
        consumer.setPullInterval(30);
        consumer.setPullBatchSize(100);
        //设置一个Listener，主要进行消息的逻辑处理
        consumer.registerMessageListener(new MessageListenerOrderly() {
            @Override
            public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
                if (CollectionUtils.isEmpty(msgs)) {
                    log.error("message error!");
                    return ConsumeOrderlyStatus.SUCCESS;
                }
                log.info("consume postDeal message,size={}, keys={}", msgs.size(), msgs.stream().map(MessageExt::getKeys).collect(Collectors.toList()));

                List<PostDealMessage> postDealMessages = msgs
                        .stream()
                        .map(x -> {
                            PostDealMessage message = JSON.parseObject(x.getBody(), PostDealMessage.class);
                            message.setMsgKey(x.getKeys());
                            return message;
                        })
                        .distinct()
                        .collect(Collectors.toList());

                postDealMessages = removeDuplicta(postDealMessages);
                if (CollectionUtils.isEmpty(postDealMessages)) {
                    log.error("empty postDealMessages");
                    return ConsumeOrderlyStatus.SUCCESS;
                }

                Map<String, List<PostDealMessage>> postDealMessageMap = postDealMessages
                        .stream()
                        .collect(Collectors.groupingBy(PostDealMessage::getGroup));

                postDealMessageMap.entrySet().parallelStream().forEach(entry -> {
                    try {
                        dealManager.postDeal(entry.getValue());
                        PostDealMessage postDealMessage = entry.getValue().get(0);
                        ContractOrderDO contractOrderDO = postDealMessage.getContractOrderDO();
                        contractOrderManager.updateExtraEntrustAmountByContract(contractOrderDO.getUserId(), contractOrderDO.getContractId());
                        markExist(entry.getValue());
                    } catch (Throwable t) {
                        log.error("post deal message exception", t);
                    }
                });
                return ConsumeOrderlyStatus.SUCCESS;
            }
        });
        //调用start()方法启动consumer
        consumer.start();
    }

    private List<PostDealMessage> removeDuplicta(List<PostDealMessage> postDealMessages) {
        List<String> keys = postDealMessages.stream().map(x -> EXIST_POST_DEAL + x.getMsgKey()).collect(Collectors.toList());
        List<String> existList = redisTemplate.opsForValue().multiGet(keys);
        List<PostDealMessage> ret = new ArrayList<>();
        for (int i = 0; i < postDealMessages.size(); i++) {
            PostDealMessage postDealMessage = postDealMessages.get(i);
            if (null == existList.get(i)) {
                ret.add(postDealMessage);
            } else {
                log.error("duplicate post deal message, message={}", postDealMessage);
            }
        }
        return ret;
    }

    public void markExist(List<PostDealMessage> postDealMessages) {
        List<String> keyList = postDealMessages.stream()
                .map(x -> EXIST_POST_DEAL + x.getMsgKey())
                .collect(Collectors.toList());
        for (String s : keyList) {
            redisManager.setWithExpire(s, "EXIST", Duration.ofSeconds(seconds));
        }
    }


    private void logSuccessMsg(MessageExt messageExt, String extInfo) {
        String body = null;
        try {
            body = new String(messageExt.getBody(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("get mq message failed", e);
        }
        log.info("consume message success, extInfo={}, msgId={}, msgKey={}, tag={},  body={}, reconsumeTimes={}", extInfo, messageExt.getMsgId(), messageExt.getKeys(), messageExt.getTags(),
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

