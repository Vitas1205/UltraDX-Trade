package com.fota.trade.mapper;

import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderPriceTypeEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.domain.query.UsdkOrderQuery;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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
 * @Date 2018/7/7
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class UsdkOrderMapperTest {

    @Resource
    private UsdkOrderMapper usdkOrderMapper;

    @Test
    public void testInsert() throws Exception {
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        usdkOrderDO.setAssetId(2);
        usdkOrderDO.setAssetName("BTC");
        usdkOrderDO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        usdkOrderDO.setUserId(9527L);
        usdkOrderDO.setOrderType(OrderPriceTypeEnum.LIMIT.getCode());
        usdkOrderDO.setTotalAmount(new BigDecimal("0.01"));
        usdkOrderDO.setUnfilledAmount(new BigDecimal("0.01"));
        usdkOrderDO.setPrice(new BigDecimal("6000.1"));
        usdkOrderDO.setFee(new BigDecimal("1.1"));
        usdkOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        int insertRet = usdkOrderMapper.insert(usdkOrderDO);
        Assert.assertTrue(insertRet > 0);
    }


    @Test
    public void testCountByQuery() throws Exception {
        UsdkOrderQuery usdkOrderQuery = new UsdkOrderQuery();
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        Integer count = usdkOrderMapper.countByQuery(ParamUtil.objectToMap(usdkOrderQuery));
        Assert.assertTrue(count > 0);
    }

    @Test
    public void testListByQuery() throws Exception {
        UsdkOrderQuery usdkOrderQuery = new UsdkOrderQuery();
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        List<UsdkOrderDO> usdkOrderDOS = usdkOrderMapper.listByQuery(ParamUtil.objectToMap(usdkOrderQuery));
        Assert.assertTrue(usdkOrderDOS != null && usdkOrderDOS.size() > 0);
    }


    @Test
    public void testupdateStatus() throws Exception {
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        usdkOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
        usdkOrderDO.setId(929L);
        usdkOrderDO.setUserId(205L);
        usdkOrderMapper.updateStatus(usdkOrderDO);
    }

}
