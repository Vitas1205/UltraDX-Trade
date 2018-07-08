package com.fota.trade.mapper;

import com.fota.client.domain.query.UserPositionQuery;
import com.fota.trade.domain.UserPositionDO;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class UserPositionMapperTest {

    @Resource
    private UserPositionMapper userPositionMapper;

    @Before
    public void testInsert() throws Exception {
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setUserId(9527L);
        userPositionDO.setContractId(1);
        userPositionDO.setContractName("Test");
        userPositionDO.setLockedAmount(new BigDecimal("0"));
        userPositionDO.setUnfilledAmount(new BigDecimal("10"));
        userPositionDO.setPositionType(1);
        userPositionDO.setAveragePrice(new BigDecimal("13.123"));
        userPositionDO.setStatus(1);
        int insertRet = userPositionMapper.insert(userPositionDO);
        Assert.assertTrue(insertRet > 0);
    }

    @Test
    public void testCountByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setUserId(9527L);
        userPositionQuery.setContractId(1L);
        int count = userPositionMapper.countByQuery(userPositionQuery);
        Assert.assertTrue(count == 1);
    }

    @Test
    public void testListByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setUserId(9527L);
        userPositionQuery.setContractId(1L);

        List<UserPositionDO> list = userPositionMapper.listByQuery(userPositionQuery);
        Assert.assertTrue(list != null && list.size() == 1);
        Assert.assertTrue(list.get(0).getUserId() == 9527);
    }

    @After
    public void testDelete() throws Exception {
        int deleteRet = userPositionMapper.deleteByUserId(9527L);
        Assert.assertTrue(deleteRet > 0);
    }

}
