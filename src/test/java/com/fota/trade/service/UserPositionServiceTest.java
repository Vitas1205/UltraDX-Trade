package com.fota.trade.service;

import com.fota.common.Result;
import com.fota.trade.domain.UserPositionDTO;
import com.fota.trade.domain.query.UserPositionQuery;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class UserPositionServiceTest {

    @Resource
    private UserPositionService userPositionService;

    @Test
    public void testListPositionByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setUserId(9528L);
        userPositionQuery.setContractId(1001L);
        com.fota.common.Page<com.fota.trade.domain.UserPositionDTO> page = userPositionService.listPositionByQuery(482,100, 1, 10 );
//        Assert.assertTrue(null != page && null != page.getData());
    }

    @Test
    public void listPositionByUserIdTest(){
        List<UserPositionDTO> list = userPositionService.listPositionByUserId(285L);
        log.info("----"+list.size());
    }

    @Test
    public void getPositionMarginByContractIdTest(){
        Result<BigDecimal> result = userPositionService.getPositionMarginByContractId(1000L);
        log.info("----"+result);
    }



}
