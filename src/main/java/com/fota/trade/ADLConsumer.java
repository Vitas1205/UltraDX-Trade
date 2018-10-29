package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.fota.common.Result;
import com.fota.trade.client.ADLPhaseEnum;
import com.fota.trade.client.FailedRecord;
import com.fota.trade.domain.ContractADLMatchDTO;
import com.fota.trade.manager.ADLManager;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.DealManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.fota.trade.client.ADLPhaseEnum.START;
import static com.fota.trade.client.ADLPhaseEnum.UNKNOW;
import static com.fota.trade.client.FailedRecord.NOT_RETRY;
import static com.fota.trade.client.FailedRecord.NOT_SURE;
import static com.fota.trade.client.FailedRecord.RETRY;
import static com.fota.trade.client.PostDealPhaseEnum.PARSE;
import static com.fota.trade.client.PostDealPhaseEnum.UNKNOWN;
import static com.fota.trade.client.constants.Constants.*;
import static com.fota.trade.domain.MQTopicConstants.ADL;


@Slf4j
@Component
public class ADLConsumer {

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
    private ADLManager adlManager;

    @Autowired
    private ContractOrderServiceImpl contractOrderService;

    DefaultMQPushConsumer consumer;

    @PostConstruct
    public void init() throws InterruptedException, MQClientException {
        //声明并初始化一个consumer
        //需要一个consumer group名字作为构造方法的参数，这里为consumer1
        consumer = new DefaultMQPushConsumer(group + "-adl");
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
        consumer.subscribe(ADL, ADL_TAG);
        consumer.setConsumeMessageBatchMaxSize(100);
        consumer.setPullInterval(30);
        consumer.setPullBatchSize(100);
        //设置一个Listener，主要进行消息的逻辑处理
        consumer.registerMessageListener(messageListenerOrderly);
        //调用start()方法启动consumer
        consumer.start();
    }

    @PreDestroy
    public void destory(){
        consumer.shutdown();
    }

    public MessageListenerOrderly messageListenerOrderly = new MessageListenerOrderly() {
        @Override
        public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
            if (CollectionUtils.isEmpty(msgs)) {
                log.error("message error!");
                return ConsumeOrderlyStatus.SUCCESS;
            }
            try {
                List<ContractADLMatchDTO> adlMessages = msgs
                        .stream()
                        .filter(DistinctFilter.distinctByKey(MessageExt::getKeys))
                        .map(x -> {
                            ContractADLMatchDTO message = BasicUtils.exeWhitoutError(()->JSON.parseObject(x.getBody(), ContractADLMatchDTO.class));
                            if (null == message) {
                                ADL_FAILED_LOGGER.error("{}", new FailedRecord(NOT_RETRY, PARSE.name(), x));
                                return null;
                            }
                            return message;
                        })
                        .filter(x -> null != x)
                        .distinct()
                        .collect(Collectors.toList());

                if (CollectionUtils.isEmpty(adlMessages)) {
                    log.error("empty postDealMessages");
                    return ConsumeOrderlyStatus.SUCCESS;
                }

                adlMessages.stream().forEach(adlMessage -> {
                    try {
                        Result result = adlManager.adl(adlMessage);
                        if (!result.isSuccess()) {
                            ADL_FAILED_LOGGER.error("{}", new FailedRecord(NOT_RETRY, UNKNOW.name(), adlMessage));
                        }
                    }catch (Throwable t) {
                        ADL_FAILED_LOGGER.error("{}", new FailedRecord(RETRY, START.name(), adlMessage), t);
                    }
                });

            } catch (Throwable t) {
                ADL_FAILED_LOGGER.error("{}", new FailedRecord(NOT_SURE, UNKNOW.name(), msgs), t);
            }
            return ConsumeOrderlyStatus.SUCCESS;
        }
    };

}

