package com.fota.trade.mapper;

import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.query.UserPositionQuery;
import com.fota.trade.mapper.trade.UserPositionMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.fota.trade.domain.enums.PositionStatusEnum.UNDELIVERED;

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

    private Long userId = 0L;

    Long contractId = 0L;
    @Before
    public void Insert() throws Exception {
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setUserId(userId);
        userPositionDO.setContractId(contractId);
        userPositionDO.setContractName("BTC/0102");
        //userPositionDO.setLockedAmount(new BigDecimal(5L));
        userPositionDO.setUnfilledAmount(BigDecimal.valueOf(3));
        userPositionDO.setPositionType(1);
        userPositionDO.setAveragePrice(new BigDecimal("5000"));
        userPositionDO.setFeeRate(BigDecimal.ZERO);
        userPositionDO.setStatus(1);
        userPositionDO.setRealAveragePrice(BigDecimal.ZERO);
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
        userPositionQuery.setContractId(contractId);
        userPositionQuery.setPageNo(1);
        userPositionQuery.setPageSize(20);
        List<UserPositionDO> list = userPositionMapper.listByQuery(ParamUtil.objectToMap(userPositionQuery));
        assert !CollectionUtils.isEmpty(list);
//        Assert.assertTrue(list != null && list.size() >= 1);
//        Assert.assertTrue(list.get(0).getUserId().longValue() ==  userId.longValue());
    }

    @Test
    public void testDelete() throws Exception {
        int deleteRet = userPositionMapper.deleteByUserId(userId);
        Assert.assertTrue(deleteRet > 0);
    }
    @Test
    public void testSelectByUserIdAndId(){
        UserPositionDO userPositionDO1 = userPositionMapper.selectByUserIdAndContractId(userId, contractId);
        UserPositionDO userPositionDO2 = userPositionMapper.selectByUserIdAndId(userId, contractId);
        assert null != userPositionDO1;
        assert Objects.equals(userPositionDO1, userPositionDO2);
    }

    @Test
    public void testSelectByUserId(){
        List<UserPositionDO> userPositionDO = userPositionMapper.selectByUserId(userId, UNDELIVERED.getCode());
        assert !CollectionUtils.isEmpty(userPositionDO);
    }

    @Test
    public void testSelectByContractId(){
        List<UserPositionDO> userPositionDOS = userPositionMapper.selectByContractId(contractId, UNDELIVERED.getCode());
        assert !CollectionUtils.isEmpty(userPositionDOS);
    }

    @Test
    public void testUpdatePositionById(){
        UserPositionDO userPositionDO = userPositionMapper.selectByUserIdAndContractId(userId, contractId);
        userPositionDO.setRealAveragePrice(BigDecimal.ONE);
        int aff = userPositionMapper.updatePositionById(userPositionDO, userPositionDO.getPositionType(), userPositionDO.getUnfilledAmount(), userPositionDO.getAveragePrice());
        assert 1 == aff;
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
        List<UserPositionDO> userPositionDOS = userPositionMapper.selectByContractIdAndUserIds(Arrays.asList(userId),contractId );
        assert !CollectionUtils.isEmpty(userPositionDOS);
    }
}
