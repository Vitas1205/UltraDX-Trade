package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.aliyun.openservices.ons.api.*;
import com.fota.common.utils.LogUtil;
import com.fota.trade.common.TradeBizTypeEnum;
import com.fota.trade.domain.MQMessage;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.UsdkMatchedOrderDTO;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.msg.TopicConstants;
import com.fota.trade.service.impl.UsdkOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Properties;

import static com.fota.trade.common.Constant.MQ_REPET_JUDGE_KEY_MATCH;
import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;
import static com.fota.trade.common.TradeBizTypeEnum.COIN_DEAL;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/25 21:04
 * @Modified:
 */
@Slf4j
@Component
public class MatchedConsumer {

    @Autowired
    private UsdkOrderServiceImpl usdkOrderService;
    @Autowired
    private RedisManager redisManager;
    @Value("${spring.rocketmq.namesrv_addr}")
    private String namesrvAddr;
    @Value("${spring.rocketmq.group}")
    private String group;

    @Value("${spring.rocketmq.instanceName}")
    private String clientInstanceName;

    @Value("${spring.ones.acl.secretKey}")
    private String aclSecretKey;

    @Value("${spring.ones.acl.accessKey}")
    private String aclAccessKey;


    @PostConstruct
    public void init() throws MQClientException {
        Properties properties = new Properties();
        // 您在控制台创建的 Group ID
        properties.put(PropertyKeyConst.GROUP_ID, "GID_" + group + "_" +TopicConstants.MCH_COIN_MATCH);
        // AccessKey 阿里云身份验证，在阿里云服务器管理控制台创建
        properties.put(PropertyKeyConst.AccessKey, aclAccessKey);
        // SecretKey 阿里云身份验证，在阿里云服务器管理控制台创建
        properties.put(PropertyKeyConst.SecretKey, aclSecretKey);
        // 设置 TCP 接入域名，到控制台的实例基本信息中查看
        properties.put(PropertyKeyConst.NAMESRV_ADDR, namesrvAddr);
        properties.put(PropertyKeyConst.MaxReconsumeTimes, 16);
        // 集群订阅方式 (默认)
        // properties.put(PropertyKeyConst.MessageModel, PropertyValueConst.CLUSTERING);
        // 广播订阅方式
        // properties.put(PropertyKeyConst.MessageModel, PropertyValueConst.BROADCASTING);
        Consumer consumer = ONSFactory.createConsumer(properties);
        consumer.subscribe(TopicConstants.MCH_COIN_MATCH, "*", new MessageListener() { //订阅多个 Tag
           @Override
            public Action consume(Message messageExt, ConsumeContext context) {
               log.info("matchedConsumer mq,messageExt:{}", messageExt);
               if (messageExt==null) {
                   log.error("message error!");
                   return Action.CommitMessage;
               }
               String mqKey = messageExt.getKey();
               ResultCode resultCode = null;
               String existKey = MQ_REPET_JUDGE_KEY_MATCH + mqKey;
               //判断是否已经成交
               boolean locked = redisManager.tryLock(existKey, Duration.ofHours(1));
               if (!locked) {
                   logErrorMsg(COIN_DEAL, "already consumed, not retry", messageExt);
                   return Action.CommitMessage;
               }

               try {
                   byte[] bodyByte = messageExt.getBody();
                   String bodyStr = null;
                   try {
                       bodyStr = new String(bodyByte, "UTF-8");
                   } catch (UnsupportedEncodingException e) {
                       log.error("get mq message failed", e);
                       return Action.CommitMessage;
                   }
                   UsdkMatchedOrderDTO usdkMatchedOrderDTO = JSON.parseObject(bodyStr, UsdkMatchedOrderDTO.class);
                   resultCode = usdkOrderService.updateOrderByMatch(usdkMatchedOrderDTO);
                   if (!resultCode.isSuccess()) {
                       logErrorMsg(COIN_DEAL, "resultCode=" + resultCode, messageExt);
                       if (resultCode.getCode() == ILLEGAL_PARAM.getCode()) {
                           return Action.CommitMessage;
                       }
                       redisManager.del(existKey);
                       return Action.ReconsumeLater;
                   }
                   //一定要成交成功才能标记
                   redisManager.set(existKey, "1", Duration.ofDays(1));
                   return Action.CommitMessage;
               } catch (Exception e) {
                   logErrorMsg(COIN_DEAL, messageExt, e);
                   redisManager.del(existKey);
                   return Action.CommitMessage;
               }
            }
        });
        consumer.start();
    }


    public static void logErrorMsg(TradeBizTypeEnum bizType, Message messageExt, Throwable t) {
        String errorMsg = String.format("consumeTimes:%s ", messageExt.getReconsumeTimes());
        LogUtil.error(bizType, messageExt.getKey(), MQMessage.of(messageExt),
                errorMsg, t);
    }

    public static void logErrorMsg(TradeBizTypeEnum bizType, String cause, Message messageExt) {
        String errorMsg = String.format("cause:%s, consumeTimes:%s ", cause, messageExt.getReconsumeTimes());
        LogUtil.error(bizType, messageExt.getKey(), MQMessage.of(messageExt),
                errorMsg);
    }
}

