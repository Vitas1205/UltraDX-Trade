package com.fota.trade.mapper;

import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.query.UserPositionQuery;
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
public class UserPositionMapperTest {

    @Resource
    private UserPositionMapper userPositionMapper;

    @Test
    public void testInsert() throws Exception {
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setUserId(282L);
        userPositionDO.setContractId(1000L);
        userPositionDO.setContractName("BTC/0102");
        //userPositionDO.setLockedAmount(new BigDecimal(5L));
        userPositionDO.setUnfilledAmount(3L);
        userPositionDO.setPositionType(1);
        userPositionDO.setAveragePrice(new BigDecimal("5000"));
        userPositionDO.setStatus(1);
        int insertRet = userPositionMapper.insert(userPositionDO);
        Assert.assertTrue(insertRet > 0);
    }

    @Test
    public void testCountByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setUserId(9527L);
        userPositionQuery.setContractId(1L);
        userPositionQuery.setPageNo(1);
        userPositionQuery.setPageSize(20);
        int count = userPositionMapper.countByQuery(ParamUtil.objectToMap(userPositionQuery));
        Assert.assertTrue(count == 1);
    }

    @Test
    public void testListByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setUserId(9527L);
        userPositionQuery.setContractId(1L);
        userPositionQuery.setPageNo(1);
        userPositionQuery.setPageSize(20);
        List<UserPositionDO> list = userPositionMapper.listByQuery(ParamUtil.objectToMap(userPositionQuery));
        Assert.assertTrue(list != null && list.size() == 1);
        Assert.assertTrue(list.get(0).getUserId() == 9527);
    }

    @Test
    public void testDelete() throws Exception {
        int deleteRet = userPositionMapper.deleteByUserId(9527L);
        Assert.assertTrue(deleteRet > 0);
    }

}
