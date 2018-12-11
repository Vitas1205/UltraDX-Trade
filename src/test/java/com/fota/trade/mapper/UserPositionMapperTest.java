package com.fota.trade.mapper;

import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.query.UserPositionQuery;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    private Long userId = 9528L;
    @Test
    public void Insert() throws Exception {
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setUserId(userId);
        userPositionDO.setContractId(1000L);
        userPositionDO.setContractName("BTC/0102");
        //userPositionDO.setLockedAmount(new BigDecimal(5L));
        userPositionDO.setUnfilledAmount(BigDecimal.valueOf(3));
        userPositionDO.setPositionType(1);
        userPositionDO.setAveragePrice(new BigDecimal("5000"));
        userPositionDO.setFeeRate(BigDecimal.ZERO);
        userPositionDO.setStatus(1);
        int insertRet = userPositionMapper.insert(userPositionDO);
        Assert.assertTrue(insertRet > 0);
    }

    @Test
    public void testCountByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setUserId(userId);
        userPositionQuery.setContractId(1L);
        userPositionQuery.setPageNo(1);
        userPositionQuery.setPageSize(20);
        int count = userPositionMapper.countByQuery(ParamUtil.objectToMap(userPositionQuery));
//        Assert.assertTrue(count >= 1);
    }

    @Test
    public void testListByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
   //     userPositionQuery.setUserId(userId);
        userPositionQuery.setContractId(1000L);
        userPositionQuery.setPageNo(1);
        userPositionQuery.setPageSize(20);
        List<UserPositionDO> list = userPositionMapper.listByQuery(ParamUtil.objectToMap(userPositionQuery));
        System.out.println(list);
//        Assert.assertTrue(list != null && list.size() >= 1);
//        Assert.assertTrue(list.get(0).getUserId().longValue() ==  userId.longValue());
    }

    @Test
    public void testDelete() throws Exception {
        int deleteRet = userPositionMapper.deleteByUserId(userId);
        Assert.assertTrue(deleteRet > 0);
    }

//    @Test
    public void testLamda() throws Exception {
        List<UserPositionDO> positionlist = new ArrayList<>();
        List<UserPositionDO> userPositionDOlist = new ArrayList<>();
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setContractId(2L);
        positionlist.add(userPositionDO);
        userPositionDOlist = positionlist.stream().filter(userPosition-> userPosition.getContractId().equals(282L))
                .limit(1).collect(Collectors.toList());
        Assert.assertTrue(userPositionDOlist.size() > 0);
    }

    @Test
    public void test_countTotalPosition() {
        BigDecimal result = userPositionMapper.countTotalPosition(1000L);
        System.out.println("result : "+ result);
    }

    @Test
    public void testBatchSelect(){
        List<UserPositionDO> userPositionDOS = userPositionMapper.selectByContractIdAndUserIds(Arrays.asList(userId),1218L );
        System.out.println(userPositionDOS);
    }
}
