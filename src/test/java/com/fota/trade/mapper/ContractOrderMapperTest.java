package com.fota.trade.mapper;

import com.fota.common.Page;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.BaseQuery;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.ContractOrderDTO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.domain.query.ContractOrderQuery;
import com.fota.trade.service.ContractOrderService;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.PriceUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.fota.trade.domain.enums.OrderStatusEnum.CANCEL;
import static com.fota.trade.domain.enums.OrderStatusEnum.MATCH;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ContextConfiguration(classes = MapperTestConfig.class)
@Transactional
public class ContractOrderMapperTest {

    @Resource
    private ContractOrderMapper contractOrderMapper;

    private Long userId = 282L;

    private ContractOrderDO contractOrderDO;
    @Resource
    private ContractOrderService contractOrderService;

    @Before
    public void init() {
        // 准备数据
        contractOrderDO = new ContractOrderDO();
        contractOrderDO.setId(BasicUtils.generateId());
        contractOrderDO.setCloseType(1);
        contractOrderDO.setContractId(1L);
        contractOrderDO.setContractName("BTC0930");
        contractOrderDO.setFee(new BigDecimal("0.01"));
        contractOrderDO.setLever(10);
        contractOrderDO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        contractOrderDO.setPrice(new BigDecimal("6000.1"));
        contractOrderDO.setTotalAmount(BigDecimal.valueOf(100L));
        contractOrderDO.setUnfilledAmount(BigDecimal.valueOf(100L));
        contractOrderDO.setUserId(userId);
        contractOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        contractOrderDO.setGmtCreate(new Date());
        contractOrderDO.setOrderType(1);
        int insertRet = contractOrderMapper.insert(contractOrderDO);
        Assert.assertTrue(insertRet > 0);
    }


    @Test
    public void testCountByQuery() throws Exception {
        ContractOrderQuery contractOrderQuery = new ContractOrderQuery();
        contractOrderQuery.setUserId(userId);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        Integer count = contractOrderMapper.countByQuery(ParamUtil.objectToMap(contractOrderQuery));
        log.info("countByQuery result={}", count);
    }

    @Test
    public void testListByQuery() throws Exception {
        ContractOrderQuery contractOrderQuery = new ContractOrderQuery();
        contractOrderQuery.setUserId(userId);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        List<ContractOrderDO> list = contractOrderMapper.listByQuery((ParamUtil.objectToMap(contractOrderQuery)));
        log.info("listByQuery result={}", list);
    }


    @Test
    public void testSelectUnfinishedOrder() throws Exception {
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByUserId(userId);
        log.info("----------------------------" + list.size());
    }

    @Test
    public void testSelectNotEnforceOrderByUserId() throws Exception {
        List<ContractOrderDO> list = contractOrderMapper.selectNotEnforceOrderByUserId(userId);
        log.info("----------------------------" + list.size());
    }
    @Test
    public void testSelectNotEnforceOrderByUserIdAndContractId(){
        Object obj = contractOrderMapper.selectNotEnforceOrderByUserIdAndContractId(userId, contractOrderDO.getContractId());
        log.info("result={}", obj);
    }

    @Test
    public void testUpdateAmountAndStatus() throws Exception {

        BigDecimal filledAmount = BigDecimal.valueOf(100L);
        BigDecimal filledPrice = new BigDecimal(0.3);
        int aff = contractOrderMapper.updateAmountAndStatus(userId, contractOrderDO.getId(),filledAmount, filledPrice, new Date());
        ContractOrderDO contractOrderDO2 = contractOrderMapper.selectByIdAndUserId(userId, contractOrderDO.getId());

        BigDecimal expectAvgPrice = PriceUtil.getAveragePrice(contractOrderDO.getAveragePrice(),
                contractOrderDO.getTotalAmount().subtract(contractOrderDO.getUnfilledAmount()), filledAmount, filledPrice);

        BigDecimal wucha = new BigDecimal(1e-6);
        Assert.assertTrue(contractOrderDO2.getUnfilledAmount().compareTo(contractOrderDO.getUnfilledAmount().subtract(filledAmount)) == 0
                && contractOrderDO2.getAveragePrice().subtract(expectAvgPrice).compareTo(wucha)<0
                && contractOrderDO2.getStatus() == MATCH.getCode()
        );
    }


    @Test
    public void selectTest() throws Exception {
        ContractOrderDO temp = contractOrderMapper.selectByIdAndUserId(userId, contractOrderDO.getId());
        log.info("----------------------------"+temp);
    }

    @Test
    public void listByUserIdAndOrderTypeTest() throws Exception {
        List<Integer> orderTypes = new ArrayList<>();
        orderTypes.add(0);
        orderTypes.add(1);
        orderTypes.add(2);
        List<ContractOrderDO> list = contractOrderMapper.listByUserIdAndOrderType(userId,orderTypes);
        log.info("----------------------------"+list.size());
    }
    @Test
    public void testCancel(){
       int aff = contractOrderMapper.cancel(userId, contractOrderDO.getId(), CANCEL.getCode());
       assert 1 == aff;
    }

}
