package com.fota.trade;

import com.fota.common.utils.LogUtil;
import com.fota.trade.client.FailedRecord;
import com.fota.trade.common.TradeBizTypeEnum;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ConvertUtils;
import com.fota.trade.util.DistinctFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.Test;
import org.springframework.dao.DataAccessException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Swifree on 2018/9/10.
 * Code is the law
 */
@Slf4j
public class BasicFunctionTest {
    @Test
    public void testFastJson(){
        MessageExt messageExt = new MessageExt();
        messageExt.setBody("hello".getBytes());
        messageExt.setKeys("a");
        messageExt.setTags("b");
        System.out.println(new FailedRecord(1, "1", messageExt));
    }

    @Test
    public void testLogError(){
        LogUtil.error( TradeBizTypeEnum.COIN_CANCEL_ORDER.toString(), "1", 1, "usdkOrderMapper.cancel failed");

        LogUtil.error(1, "1", 1, new RuntimeException("a"));
    }

    @Test
    public void testRetry(){
        BasicUtils.retryWhenFail(() -> {log.info("a");return false;},Duration.ofMillis(10), 3);
    }
    @Test
    public void testCount(){
        assert new org.mybatis.spring.MyBatisSystemException(new Exception()) instanceof DataAccessException;
        assert 2== BasicUtils.count("a##", '#');
    }
    @Test
    public void testAbsHash(){
        int h = BasicUtils.absHash(Integer.MIN_VALUE);
        assert h>=0;
    }
    @Test
    public void testDistinctFilter(){
        MessageExt messageExt1 = new MessageExt(), messageExt2=new MessageExt(), messageExt3 = new MessageExt();
        messageExt1.setKeys("1");
        messageExt2.setKeys("2");
        messageExt3.setKeys("1");
        List<MessageExt> messageExts = Arrays.asList(messageExt1, messageExt2, messageExt3);
        List<MessageExt> newList = messageExts.stream().filter(DistinctFilter.distinctByKey(MessageExt::getKeys))
                .collect(Collectors.toList());
        assert newList.size() == 2 && newList.contains(messageExt1) && newList.contains(messageExt2);

    }

}
