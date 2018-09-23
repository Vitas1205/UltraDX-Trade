package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.trade.client.PostDealMessage;
import com.fota.trade.domain.ContractOrderDO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Created by Swifree on 2018/9/23.
 * Code is the law
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class DealManagerTest {
    @Resource
    private DealManager dealManager;
    @Test
    public void testPostDeal(){
        PostDealMessage postDealMessage = JSON.parseObject("{\"contractOrderDO\":{\"closeType\":1,\"contractId\":1007,\"contractName\":\"EOS0203\"," +
                        "\"fee\":0.0005000000000000,\"gmtCreate\":1537681445000,\"gmtModified\":1537681445000,\"id\":866844580224824,\"lever\":1," +
                        "\"orderContext\":\"{\\\"username\\\":\\\"15728044656\\\"}\",\"orderDirection\":1,\"orderType\":1,\"price\":6.0000000000000000," +
                        "\"status\":9,\"totalAmount\":1.0000000000000000,\"unfilledAmount\":0E-16,\"userId\":274},\"filledAmount\":1,\"filledPrice\":6," +
                        "\"group\":\"274_1007\",\"matchId\":57040}",
                PostDealMessage.class);
        postDealMessage.setMsgKey("57040_866844580224824");
        dealManager.postDeal(Arrays.asList(postDealMessage));
    }
}
