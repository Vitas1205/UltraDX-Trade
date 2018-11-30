package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.fota.trade.client.FailedRecord;
import com.fota.trade.common.ADLBizException;
import com.fota.trade.common.BizExceptionEnum;
import com.fota.trade.domain.ContractADLMatchDTO;
import com.fota.trade.manager.*;
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
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fota.trade.client.ADLPhaseEnum.*;
import static com.fota.trade.client.FailedRecord.*;
import static com.fota.trade.client.PostDealPhaseEnum.PARSE;
import static com.fota.trade.common.BizExceptionEnum.NO_CURRENT_PRICE;
import static com.fota.trade.common.BizExceptionEnum.UPDATE_POSITION;
import static com.fota.trade.msg.TopicConstants.MCH_CONTRACT_ADL;


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

    @Autowired
    private RocketMqManager rocketMqManager;

    @PostConstruct
    public void init() throws InterruptedException, MQClientException {
        //声明并初始化一个consumer
        //需要一个consumer group名字作为构造方法的参数，这里为consumer1
        consumer = new DefaultMQPushConsumer(group + "_" + MCH_CONTRACT_ADL);
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
        consumer.subscribe(MCH_CONTRACT_ADL, "*");
        consumer.setConsumeMessageBatchMaxSize(100);
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
                Map<ContractADLMatchDTO, MessageExt> messageExtMap = new HashMap<>();
                List<ContractADLMatchDTO> adlMessages = msgs
                        .stream()
                        .filter(DistinctFilter.distinctByKey(MessageExt::getKeys))
                        .map(x -> {
                            ContractADLMatchDTO message = BasicUtils.exeWhitoutError(()->JSON.parseObject(x.getBody(), ContractADLMatchDTO.class));
                            if (null == message) {
                                ADL_FAILED_LOGGER.error("{}\037", new FailedRecord(NOT_RETRY, PARSE.name(), x));
                                return null;
                            }
                            messageExtMap.put(message, x);
                            return message;
                        })
                        .filter(x -> null != x)
                        .collect(Collectors.toList());

                if (CollectionUtils.isEmpty(adlMessages)) {
                    log.error("empty postDealMessages");
                    return ConsumeOrderlyStatus.SUCCESS;
                }

                for (ContractADLMatchDTO adlMessage : adlMessages) {
                    adl(messageExtMap, adlMessage);
                }

            } catch (Throwable t) {
                ADL_FAILED_LOGGER.error("{}\037", new FailedRecord(NOT_SURE, UNKNOW.name(), msgs), t);
            }
            return ConsumeOrderlyStatus.SUCCESS;
        }
    };
    public void adl(Map<ContractADLMatchDTO, MessageExt> messageExtMap, ContractADLMatchDTO adlMessage){
        try {
            adlManager.adl(adlMessage);
        }catch (Throwable t) {
            boolean shouldRetry = false;
            MessageExt messageExt = messageExtMap.get(adlMessage);
            if (t instanceof ADLBizException) {
                ADLBizException adlBizException = (ADLBizException)t;
                BizExceptionEnum bizException = adlBizException.getBizException();
                if ( UPDATE_POSITION == bizException
                        || NO_CURRENT_PRICE == bizException) {
                    shouldRetry = true;
                }
            }
            if (t instanceof DataAccessException) {
                shouldRetry = true;
            }
            if (shouldRetry) {
                messageExt.setKeys(messageExt.getKeys() + "#");
                int cnt = BasicUtils.count(messageExt.getKeys(), '#');
                //最多重试3次
                if (cnt <=3) {
                    boolean suc = rocketMqManager.sendMessage(Arrays.asList(messageExt));
                    if (suc) {
                        ADL_FAILED_LOGGER.error("{}\037", new FailedRecord(RETRY, RESEND.name(), adlMessage, "retry", "retries="+cnt), t);
                        return;
                    }
                }
            }else {
                ADL_FAILED_LOGGER.error("{}\037", new FailedRecord(RETRY, EXCEPTION.name(), adlMessage), t);
            }
        }
    }

}

