package com.fota.trade.test;

import com.fota.trade.manager.CurrentPriceService;
import com.fota.trade.manager.RocketMqManager;
import com.fota.trade.mapper.ContractOrderMapper;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.*;

@Profile("test")
@Configuration
public class MockConfiguration {

    @Primary
    @Bean
    public DefaultMQProducer defaultMQProducer() throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        DefaultMQProducer defaultMQProducer =  Mockito.mock(DefaultMQProducer.class);
        when(defaultMQProducer.send(anyList())).thenReturn(sendResult);
        when((defaultMQProducer.send(any(Message.class)))).thenReturn(sendResult);
        return defaultMQProducer;
    }

    @Primary
    @Bean
    public CurrentPriceService currentPriceService(){
        return Mockito.mock(CurrentPriceService.class);
    }

    @Primary
    @Bean
    public ContractOrderMapper contractOrderMapper(){
        return Mockito.mock(ContractOrderMapper.class);
    }


}