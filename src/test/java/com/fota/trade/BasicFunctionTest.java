package com.fota.trade;

import com.fota.common.utils.LogUtil;
import com.fota.trade.client.FailedRecord;
import com.fota.trade.common.TradeBizTypeEnum;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.util.BasicUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
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
    public void testSort(){

        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setUserId(315L);
        contractOrderDO.setId(1L);

        ContractOrderDO  contractOrderDO1= new ContractOrderDO();
        contractOrderDO1.setUserId(314L);
        contractOrderDO1.setId(2L);
        List<ContractOrderDO> contractOrderDOS = new ArrayList<>();
        contractOrderDOS.add(contractOrderDO);
        contractOrderDOS.add(contractOrderDO1);

        Collections.sort(contractOrderDOS, (a, b) -> {
            int c = a.getUserId().compareTo(b.getUserId());
            return c;
//            if (c!=0) {
//                return c;
//            }
//            return a.getId().compareTo(b.getId());
        });
        Stream.of(2,1).sorted(Integer::compareTo)
                .forEach(x -> System.out.println(x));
        //获取锁
        List<String> locks = contractOrderDOS.stream()
        .map(x -> "POSITION_LOCK_"+ x.getUserId()+"_"+ x.getContractId())
                .distinct()
                .collect(Collectors.toList());
        System.out.println(locks);
    }
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
        assert 2== BasicUtils.count("a##", '#');
    }
}
