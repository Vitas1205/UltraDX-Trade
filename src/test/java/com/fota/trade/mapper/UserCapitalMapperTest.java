package com.fota.trade.mapper;

import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.UserCapitalDO;
import com.fota.trade.mapper.asset.UserCapitalMapper;
import com.fota.trade.mapper.sharding.UsdkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Resource
    private UsdkOrderMapper usdkOrderMapper;

    @Test
    public void testQuery() {
        UserCapitalDO userCapitalDO = userCapitalMapper.getCapitalByAssetId(200L, 2);
        Assert.assertTrue(userCapitalDO != null);
    }

    @Test
    public void testQueryOrder() {
        Map<String, Object> param = new HashMap<>();
        param.put("userId", 200L);
        param.put("startRow", 0);
        param.put("endRow", 100);
        List<UsdkOrderDO> list = usdkOrderMapper.listByQuery(param);
        Assert.assertTrue(list != null);
    }





}
