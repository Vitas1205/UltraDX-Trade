package com.fota.trade.service;

import com.fota.client.domain.query.UserPositionQuery;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class UserPositionServiceTest {

    @Resource
    private UserPositionService userPositionService;

    @Test
    public void testListPositionByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setUserId(282L);
        userPositionQuery.setContractId(1001L);
        com.fota.common.Page<com.fota.trade.domain.UserPositionDTO> page = userPositionService.listPositionByQuery(482,100, 1, 10 );
        Assert.assertTrue(null != page && null != page.getData());
    }



}
