package com.fota.trade.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/13 10:40
 * @Modified:
 */
@Slf4j
@Configuration
public class RocketMqConfig {

    @Value("${spring.rocketmq.namesrv_addr}")
    private String namesrvAddr;

    @Value("${spring.rocketmq.group}")
    private String group;

    @Value("${spring.ones.acl.secretKey}")
    private String aclSecretKey;

    @Value("${spring.ones.acl.accessKey}")
    private String aclAccessKey;
    @Bean
    public DefaultMQProducer getRocketMQProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(group, new AclClientRPCHook(new SessionCredentials(aclAccessKey, aclSecretKey)));
        producer.setNamesrvAddr(namesrvAddr);
        producer.setVipChannelEnabled(false);
        producer.setRetryTimesWhenSendFailed(3);  // 失败的情况重发3次
        try {
            producer.start();
            log.info(String.format("RocketMQ: producer is start ! groupName:[%s],namesrvAddr:[%s]"
                    , group, namesrvAddr));
        } catch (Exception e) {
            log.error("RocketMQ: producer is error", e);
        }
        return producer;
    }

}
