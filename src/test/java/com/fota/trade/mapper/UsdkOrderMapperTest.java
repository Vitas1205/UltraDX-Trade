package com.fota.trade.mapper;

import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderPriceTypeEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.domain.query.UsdkOrderQuery;
import com.fota.trade.util.BasicUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static com.fota.trade.domain.enums.OrderStatusEnum.CANCEL;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class UsdkOrderMapperTest {

    @Resource
    private UsdkOrderMapper usdkOrderMapper;

    UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
    long userId = 274L;

    @Before
    public void testInsert() throws Exception {
        usdkOrderDO.setId(BasicUtils.generateId());
        usdkOrderDO.setAssetId(2);
        usdkOrderDO.setAssetName("BTC");
        usdkOrderDO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        usdkOrderDO.setUserId(userId);
        usdkOrderDO.setOrderType(OrderPriceTypeEnum.LIMIT.getCode());
        usdkOrderDO.setTotalAmount(new BigDecimal("0.01"));
        usdkOrderDO.setUnfilledAmount(new BigDecimal("0.01"));
        usdkOrderDO.setPrice(new BigDecimal("6000.1"));
        usdkOrderDO.setFee(new BigDecimal("1.1"));
        usdkOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        usdkOrderDO.setGmtModified(new Date(System.currentTimeMillis()));
        usdkOrderDO.setGmtCreate(new Date(System.currentTimeMillis()));
        int insertRet = usdkOrderMapper.insert(usdkOrderDO);
        Assert.assertTrue(insertRet > 0);
    }

    @Test
    public void testSelectByUserIdAndId(){
        UsdkOrderDO res = usdkOrderMapper.selectByUserIdAndId(userId, usdkOrderDO.getId());
        assert null != res;
    }


    @Test
    public void testCountByQuery() {
        UsdkOrderQuery usdkOrderQuery = new UsdkOrderQuery();
        usdkOrderQuery.setUserId(userId);
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        Integer count = null;
        try {
            count = usdkOrderMapper.countByQuery(ParamUtil.objectToMap(usdkOrderQuery));
        } catch (Exception e) {
            e.printStackTrace();
        }
        assert  count > 0;
    }

    @Test
    public void testListByQuery() throws Exception {
        UsdkOrderQuery usdkOrderQuery = new UsdkOrderQuery();
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        usdkOrderQuery.setUserId(userId);
        List<UsdkOrderDO> usdkOrderDOS = usdkOrderMapper.listByQuery(ParamUtil.objectToMap(usdkOrderQuery));
        Assert.assertTrue(usdkOrderDOS != null && usdkOrderDOS.size() > 0);
    }
    @Test
    public void testSelectUnfinishedOrderByUserId(){
        List<UsdkOrderDO> usdkOrderDOS = usdkOrderMapper.selectUnfinishedOrderByUserId(userId);
        assert usdkOrderDOS.size()>0;
    }

    @Test
    public void testUpdateByFilledAmount(){
        int aff = usdkOrderMapper.updateByFilledAmount(userId, usdkOrderDO.getId(), usdkOrderDO.getUnfilledAmount(), usdkOrderDO.getPrice(), new Date());
        assert aff == 1;
    }

    @Test
    public void testCancel(){
        UsdkOrderDO temp = usdkOrderMapper.selectByUserIdAndId(userId, usdkOrderDO.getId());
        int aff = usdkOrderMapper.cancelByOpLock(userId, temp.getId(), CANCEL.getCode(), temp.getGmtModified());
        assert  1 == aff;
    }

}
