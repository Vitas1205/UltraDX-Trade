package com.fota.trade;

import com.alibaba.fastjson.JSON;
import com.aliyun.openservices.ons.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fota.common.utils.LogUtil;
import com.fota.trade.common.TradeBizTypeEnum;
import com.fota.trade.domain.MQMessage;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.msg.BaseCanceledMessage;
import com.fota.trade.msg.TopicConstants;
import com.fota.trade.util.BasicUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;
import static com.fota.trade.common.TradeBizTypeEnum.COIN_CANCEL_ORDER;

@Slf4j
@Component
public class CanceledConsumer {

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

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Autowired
    private ObjectMapper objectMapper;


    private static final int removeSucced = 1;

    @PostConstruct
    public void init() throws MQClientException {
        Properties properties = new Properties();
        // 您在控制台创建的 Group ID
        properties.put(PropertyKeyConst.GROUP_ID, "GID_" + group + "_" +TopicConstants.MCH_COIN_CANCEL_RST);
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
        consumer.subscribe(TopicConstants.MCH_COIN_CANCEL_RST, "*", new MessageListener() { //订阅多个 Tag
            @Override
            public Action consume(Message messageExt, ConsumeContext context) {
                if (messageExt==null) {
                    log.error("message error!");
                    return Action.CommitMessage;
                }
                String mqKey = messageExt.getKey();
                String tag = messageExt.getTag();
                try {
                    byte[] bodyByte = messageExt.getBody();
                    String bodyStr = new String(bodyByte, StandardCharsets.UTF_8);
                    BaseCanceledMessage res = BasicUtils.exeWhitoutError(() -> JSON.parseObject(bodyStr, BaseCanceledMessage.class));
                    if (null == res) {
                        logErrorMsg(COIN_CANCEL_ORDER, "resolve message failed, not retry", messageExt);
                        return Action.CommitMessage;
                    }
                    if (!res.isSuccess()) {
                        return Action.CommitMessage;
                    }
                    if (null == res.getUnfilledAmount() || null == res.getTotalAmount()) {
                        logErrorMsg(COIN_CANCEL_ORDER, "illegal message, not retry",messageExt);
                        return Action.CommitMessage;
                    }
                    ResultCode resultCode = null;
                    resultCode = usdkOrderManager.cancelOrderByMessage(res);
                    if (!resultCode.isSuccess()) {
                        if (resultCode.getCode() == ILLEGAL_PARAM.getCode()) {
                            logErrorMsg(COIN_CANCEL_ORDER, "resultCode=" + resultCode, messageExt);
                            return Action.CommitMessage;
                        }
                        logErrorMsg(COIN_CANCEL_ORDER, "resultCode=" + resultCode, messageExt);
                        return Action.ReconsumeLater;
                    }
                    return Action.CommitMessage;
                } catch (Exception e) {
                    logErrorMsg(COIN_CANCEL_ORDER, messageExt, e);
                    return Action.ReconsumeLater;
                }
            }
        });
        consumer.start();
    }

    private void logErrorMsg(TradeBizTypeEnum bizType, Message messageExt, Throwable t) {
        String errorMsg = String.format("consumeTimes:%s ", messageExt.getReconsumeTimes());
        LogUtil.error(bizType, messageExt.getKey(), MQMessage.of(messageExt),
                errorMsg, t);
    }

    private void logErrorMsg(TradeBizTypeEnum bizType, String cause, Message messageExt) {
        String errorMsg = String.format("cause:%s, consumeTimes:%s ", cause, messageExt.getReconsumeTimes());
        LogUtil.error(bizType, messageExt.getKey(), MQMessage.of(messageExt),
                errorMsg);
    }

}

