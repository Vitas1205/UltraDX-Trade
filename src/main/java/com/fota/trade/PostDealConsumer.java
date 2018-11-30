package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.fota.common.utils.LogUtil;
import com.fota.trade.client.FailedRecord;
import com.fota.trade.common.TradeBizTypeEnum;
import com.fota.trade.domain.MQMessage;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.DealManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.msg.ContractDealedMessage;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import com.fota.trade.util.BasicUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fota.trade.client.FailedRecord.NOT_SURE;
import static com.fota.trade.client.FailedRecord.RETRY;
import static com.fota.trade.client.PostDealPhaseEnum.*;
import static com.fota.trade.msg.TopicConstants.TRD_CONTRACT_DEAL;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/25 21:04
 * @Modified:
 */
@Slf4j
@Component
public class PostDealConsumer {

    private static final Logger UPDATE_POSITION_FAILED_LOGGER = LoggerFactory.getLogger("updatePositionFailed");

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
    long seconds = 3600;

    @Autowired
    private ContractOrderServiceImpl contractOrderService;

    DefaultMQPushConsumer consumer;
    @PostConstruct
    public void init() throws InterruptedException, MQClientException {
        //声明并初始化一个consumer
        //需要一个consumer group名字作为构造方法的参数，这里为consumer1
        consumer = new DefaultMQPushConsumer(group + "_"+ TRD_CONTRACT_DEAL);
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
        consumer.subscribe(TRD_CONTRACT_DEAL, "*");
        consumer.setConsumeMessageBatchMaxSize(100);
        consumer.setPullInterval(30);
        consumer.setPullBatchSize(100);
        //设置一个Listener，主要进行消息的逻辑处理
        consumer.registerMessageListener(new MessageListenerOrderly() {
            @Override
            public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
                if (CollectionUtils.isEmpty(msgs)) {
                    LogUtil.error(TradeBizTypeEnum.CONTRACT_DEAL, null, msgs, "empty postDeal messages");
                    return ConsumeOrderlyStatus.SUCCESS;
                }
                try {
                    List<ContractDealedMessage> postDealMessages = msgs
                            .stream()
                            .map(x -> {
                                ContractDealedMessage message = BasicUtils.exeWhitoutError(()->JSON.parseObject(x.getBody(), ContractDealedMessage.class));
                                if (null == message) {
                                    UPDATE_POSITION_FAILED_LOGGER.error("{}\037", new FailedRecord(RETRY, PARSE.name(), Arrays.asList(x)));
                                    return null;
                                }
                                message.setMsgKey(x.getKeys());
                                return message;
                            })
                            .filter(x -> null != x)
                            .distinct()
                            .collect(Collectors.toList());

                    try {
                        postDealMessages = removeDuplicta(postDealMessages);
                    }catch (Throwable t) {
                        UPDATE_POSITION_FAILED_LOGGER.error("{}\037", new FailedRecord(RETRY, REMOVE_DUPLICATE.name(), postDealMessages));
                    }

                    if (CollectionUtils.isEmpty(postDealMessages)) {
                        return ConsumeOrderlyStatus.SUCCESS;
                    }

                    Map<String, List<ContractDealedMessage>> postDealMessageMap = postDealMessages
                            .stream()
                            .collect(Collectors.groupingBy(ContractDealedMessage::getGroup));

                    postDealMessageMap.entrySet().parallelStream().forEach(entry -> {

                        try {
                            dealManager.postDealOneUserOneContract(entry.getValue());
                            ContractDealedMessage postDealMessage = entry.getValue().get(0);
                            contractOrderManager.updateExtraEntrustAmountByContract(postDealMessage.getUserId(), postDealMessage.getSubjectId());
                            BasicUtils.exeWhitoutError(() ->  markExist(entry.getValue()));
                        }catch (Throwable t) {
                            UPDATE_POSITION_FAILED_LOGGER.error("{}\037", new FailedRecord(NOT_SURE, UNKNOWN.name(), entry.getValue()), t);
                        }
                    });
                } catch (Throwable t) {

                    List<MQMessage> mqMessages = msgs.stream().map(x-> {
                        MQMessage mqMessage = new MQMessage();
                        mqMessage.setKey(x.getKeys());
                        mqMessage.setMessage(x.getBody());
                        return mqMessage;
                    }).collect(Collectors.toList());
                    UPDATE_POSITION_FAILED_LOGGER.error("{}\037", new FailedRecord(NOT_SURE, UNKNOWN.name(), mqMessages), t);
                }
                return ConsumeOrderlyStatus.SUCCESS;
            }
        });
        //调用start()方法启动consumer
        consumer.start();
    }

    private List<ContractDealedMessage> removeDuplicta(List<ContractDealedMessage> postDealMessages) {
        List<String> keys = postDealMessages.stream().map(x -> EXIST_POST_DEAL + x.getMsgKey()).collect(Collectors.toList());
        List<String> existList = redisTemplate.opsForValue().multiGet(keys);
        if (null == existList) {
            return postDealMessages;
        }
        List<ContractDealedMessage> ret = new ArrayList<>();
        for (int i = 0; i < postDealMessages.size(); i++) {
            ContractDealedMessage postDealMessage = postDealMessages.get(i);
            if (null == existList.get(i)) {
                ret.add(postDealMessage);
            } else {
                log.info("duplicate post deal message, message={}", postDealMessage);
            }
        }
        return ret;
    }

    public void markExist(List<ContractDealedMessage> postDealMessages) {
        List<String> keyList = postDealMessages.stream()
                .map(x -> EXIST_POST_DEAL + x.getMsgKey())
                .collect(Collectors.toList());
        for (String s : keyList) {
            redisManager.setWithExpire(s, "EXIST", Duration.ofSeconds(seconds));
        }
    }
    @PreDestroy
    public void destory(){
        consumer.shutdown();
    }
}

