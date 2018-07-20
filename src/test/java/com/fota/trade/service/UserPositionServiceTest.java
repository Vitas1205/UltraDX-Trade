package com.fota.trade.service;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.domain.UserPositionDTO;
import com.fota.client.domain.query.UserPositionQuery;
import com.fota.client.service.UserPositionService;
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
        userPositionQuery.setUserId(9527L);
        userPositionQuery.setContractId(1L);
        Result<Page<UserPositionDTO>> result = userPositionService.listPositionByQuery(userPositionQuery);
        Assert.assertTrue(result != null && result.getData() != null && result.getData().getData() != null);
    }



}
