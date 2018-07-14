package com.fota.trade.common;


import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.client.producer.SendStatus;
import com.alibaba.rocketmq.common.message.Message;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/13 13:40
 * @Modified:
 */
@Slf4j
@Data
@Component
public class RocketMqProducer {

    @Autowired
    private DefaultMQProducer producer;

    /**
     * 生成消息的公共方法,topic、tag、pushMsg不能为空
     * @param topic
     * @param tag
     * @param key 唯一标识码，代表这条消息的业务关键词，服务器会根据keys创建哈希索引，设置后，可以在Console系统根据Topic、Keys来查询消息，
     *            由于是哈希索引，请尽可能保证key唯一，例如订单号，商品Id等
     * @param pushMsg
     * @return
     */
    public Boolean producer(String topic, String tag, String key, String pushMsg){

        SendResult result = null;
        try {
//            producer.start();
            Message msg = new Message(topic,tag,key, pushMsg.getBytes("UTF-8"));
            result = producer.send(msg, 1000); // 消息在1S内没有发送成功，就会重试

            if (SendStatus.SEND_OK == result.getSendStatus()){
                log.info("向Topic：{}，发送消息：{}，消息发送成功", topic, pushMsg);
            }else if (SendStatus.FLUSH_DISK_TIMEOUT == result.getSendStatus()){
                log.error("向Topic：{}，发送消息：{}，消息发送成功，但是服务器刷盘超时，消息已经进入服务器队列，只有此时服务器宕机，消息才会丢失", topic, pushMsg);
            }else if (SendStatus.FLUSH_SLAVE_TIMEOUT == result.getSendStatus()){
                log.error("向Topic：{}，发送消息：{}，消息发送成功，但是服务器同步到Slave时超时，消息已经进入服务器队列，只有此时服务器宕机，消息才会丢失", topic, pushMsg);
            }else if (SendStatus.SLAVE_NOT_AVAILABLE == result.getSendStatus()){
                log.error("向Topic：{}，发送消息：{}，消息发送成功，但是此时slave不可用，消息已经进入服务器队列，只有此时服务器宕机，消息才会丢失", topic, pushMsg);
            }else {
                log.error("向Topic：{}，发送消息：{}，消息发送失败", topic, pushMsg);
                return false;
            }
        } catch (Exception e) {
            log.error("向Topic：{}，发送消息：{}，消息发送失败", topic, pushMsg);
            e.printStackTrace();
        }
        return true;
    }
}
