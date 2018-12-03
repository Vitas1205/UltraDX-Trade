package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.fota.trade.client.FailedRecord;
import com.fota.trade.dto.DeleverageDTO;
import com.fota.trade.manager.*;
import com.fota.trade.msg.ContractDealedMessage;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.DistinctFilter;
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
import java.util.List;
import java.util.stream.Collectors;

import static com.fota.trade.client.ADLPhaseEnum.*;
import static com.fota.trade.client.FailedRecord.NOT_RETRY;
import static com.fota.trade.client.FailedRecord.RETRY;
import static com.fota.trade.msg.TopicConstants.MCH_CONTRACT_ADL;
import static com.fota.trade.msg.TopicConstants.TRD_CONTRACT_DELEVERAGE;


@Slf4j
@Component
public class DeleverageConsumer {

    private static final Logger ADL_FAILED_LOGGER = LoggerFactory.getLogger("adlFailed");

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

    @Autowired
    private DeleverageManager deleverageManager;


    DefaultMQPushConsumer consumer;

    @Autowired
    private RocketMqManager rocketMqManager;

    int maxRetries = 5;
    long seconds = 3600;


    String keyPrefix = "TRADE_DELEVERAGE_DUPLICATE_";

    @PostConstruct
    public void init() throws InterruptedException, MQClientException {
        //声明并初始化一个consumer
        //需要一个consumer group名字作为构造方法的参数，这里为consumer1
        consumer = new DefaultMQPushConsumer(group + "_" + TRD_CONTRACT_DELEVERAGE);
        consumer.setInstanceName(clientInstanceName);
        //同样也要设置NameServer地址
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setMaxReconsumeTimes(1);
        //这里设置的是一个consumer的消费策略
        //CONSUME_FROM_LAST_OFFSET 默认策略，从该队列最尾开始消费，即跳过历史消息
        //CONSUME_FROM_FIRST_OFFSET 从队列最开始开始消费，即历史消息（还储存在broker的）全部消费一遍
        //CONSUME_FROM_TIMESTAMP 从某个时间点开始消费，和setConsumeTimestamp()配合使用，默认是半个小时以前
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setVipChannelEnabled(false);
        //设置consumer所订阅的Topic和Tag，*代表全部的Tag
        consumer.subscribe(TRD_CONTRACT_DELEVERAGE, "*");
        consumer.setConsumeMessageBatchMaxSize(100);
        //设置一个Listener，主要进行消息的逻辑处理
        consumer.registerMessageListener(messageListenerOrderly);
        //调用start()方法启动consumer
        consumer.start();
    }

    @PreDestroy
    public void destory() {
        consumer.shutdown();
    }

    public MessageListenerOrderly messageListenerOrderly = new MessageListenerOrderly() {
        @Override
        public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
            if (CollectionUtils.isEmpty(msgs)) {
                log.error("message error when deleverage!");
                return ConsumeOrderlyStatus.SUCCESS;
            }

            try {
                msgs = msgs.stream().filter(DistinctFilter.distinctByKey(MessageExt::getKeys))
                        .collect(Collectors.toList());
                msgs = removeDuplicta(keyPrefix, msgs);
            }catch (Throwable t) {
                ADL_FAILED_LOGGER.error("{}\037", new FailedRecord(RETRY, DL_REMOVE_DUPLICATE.name(), msgs), t);
                return ConsumeOrderlyStatus.SUCCESS;
            }
            if (CollectionUtils.isEmpty(msgs)) {
                log.warn("empty msgs when deleverage");
            }
            msgs
                    .forEach(x -> {
                        DeleverageDTO message = BasicUtils.exeWhitoutError(() -> JSON.parseObject(x.getBody(), DeleverageDTO.class));
                        if (null == message) {
                            ADL_FAILED_LOGGER.error("{}\037", new FailedRecord(NOT_RETRY, DL_PARSE.name(), x));
                            return;
                        }
                        try {
                            deleverageManager.deleverage(message);
                        } catch (Throwable t) {
                            int retries = BasicUtils.count(x.getKeys(), '#');
                            if (retries < maxRetries) {
                                boolean suc = deleverageManager.sendDeleverageMessage(message, x.getKeys() + '#');
                                if (suc) {
                                    ADL_FAILED_LOGGER.error("{}\037", new FailedRecord(NOT_RETRY, DL_RESEND.name(), x, "'retry'", "retries=" + retries), t);
                                    return;
                                }
                            }
                            ADL_FAILED_LOGGER.error("{}\037", new FailedRecord(NOT_RETRY, DL_EXCEPTION.name(), x, "exception", "retries=" + retries), t);
                        }
                    });

            try {
                markExist(keyPrefix, msgs);
            }catch (Throwable t) {
                ADL_FAILED_LOGGER.error("{}\037", new FailedRecord(NOT_RETRY, DL_MARK_EXIST.name(), msgs), t);
            }
            return ConsumeOrderlyStatus.SUCCESS;
        }

    };

    public List<MessageExt> removeDuplicta(String keyPrefix, List<MessageExt> messageExts) {
        if (CollectionUtils.isEmpty(messageExts)) {
            return messageExts;
        }
        List<String> keys = messageExts.stream().map(x -> keyPrefix + x.getKeys()).collect(Collectors.toList());
        List<String> existList = redisTemplate.opsForValue().multiGet(keys);
        if (null == existList) {
            return messageExts;
        }
        List<MessageExt> ret = new ArrayList<>();
        for (int i = 0; i < messageExts.size(); i++) {
            MessageExt postDealMessage = messageExts.get(i);
            if (null == existList.get(i)) {
                ret.add(postDealMessage);
            } else {
                log.info("duplicate post deal message, message={}", postDealMessage);
            }
        }
        return ret;
    }

    public void markExist(String keyPrefix, List<MessageExt> postDealMessages) {
        List<String> keyList = postDealMessages.stream()
                .map(x -> keyPrefix + x.getKeys())
                .collect(Collectors.toList());
        for (String s : keyList) {
            redisManager.setWithExpire(s, "EXIST", Duration.ofSeconds(seconds));
        }
    }
}

