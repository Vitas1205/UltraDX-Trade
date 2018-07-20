package com.fota.trade.mapper;

import com.fota.client.domain.query.ContractOrderQuery;
import com.fota.client.domain.query.UsdkOrderQuery;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderPriceTypeEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import org.junit.After;
import org.junit.Assert;
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
public class ContractOrderMapperTest {

    @Resource
    private ContractOrderMapper contractOrderMapper;

    @Test
    public void testInsert() throws Exception {
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setCloseType(0);
        contractOrderDO.setContractId(1L);
        contractOrderDO.setContractName("BTC0930");
        contractOrderDO.setFee(new BigDecimal("0.01"));
        contractOrderDO.setLever(10);
        contractOrderDO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        contractOrderDO.setPrice(new BigDecimal("6000.1"));
        contractOrderDO.setTotalAmount(100L);
        contractOrderDO.setUnfilledAmount(100L);
        contractOrderDO.setUserId(9528L);
        contractOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        int insertRet = contractOrderMapper.insert(contractOrderDO);
        Assert.assertTrue(insertRet > 0);
    }


    @Test
    public void testCountByQuery() throws Exception {
        ContractOrderQuery contractOrderQuery = new ContractOrderQuery();
        contractOrderQuery.setUserId(9527L);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        Integer count = contractOrderMapper.countByQuery(ParamUtil.objectToMap(contractOrderQuery));
        Assert.assertTrue(count > 0);
    }

    @Test
    public void testListByQuery() throws Exception {
        ContractOrderQuery contractOrderQuery = new ContractOrderQuery();
        contractOrderQuery.setUserId(9527L);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        List<ContractOrderDO> list = contractOrderMapper.listByQuery((ParamUtil.objectToMap(contractOrderQuery)));
        Assert.assertTrue(list != null && list.size() > 0);
    }


    @After
    public void testDelete() throws Exception {

    }

}
