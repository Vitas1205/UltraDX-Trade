package com.fota.trade.mapper;

import com.fota.trade.domain.UserCapitalDO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * @author Gavin Shen
 * @Date 2019/1/21
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class UserCapitalMapperTest {

    @Resource
    private UserCapitalMapper userCapitalMapper;

    @Test
    public void testQuery() {
        UserCapitalDO userCapitalDO = userCapitalMapper.getCapitalByAssetId(200L, 2);
        Assert.assertTrue(userCapitalDO != null);
    }


}
