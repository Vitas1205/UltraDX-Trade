package com.fota.trade.mapper;

import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.domain.query.ContractOrderQuery;
import com.fota.trade.util.PriceUtil;
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
import java.util.List;
import java.util.stream.Collectors;

import static com.fota.trade.domain.enums.OrderStatusEnum.PART_MATCH;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@Transactional
public class ContractOrderMapperTest {

    @Resource
    private ContractOrderMapper contractOrderMapper;

    private Long userId = 9528L;

    private ContractOrderDO contractOrderDO;

    @Before
    public void init() {
        // 准备数据
        contractOrderDO = new ContractOrderDO();
        contractOrderDO.setCloseType(0);
        contractOrderDO.setContractId(1L);
        contractOrderDO.setContractName("BTC0930");
        contractOrderDO.setFee(new BigDecimal("0.01"));
        contractOrderDO.setLever(10);
        contractOrderDO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        contractOrderDO.setPrice(new BigDecimal("6000.1"));
        contractOrderDO.setTotalAmount(100L);
        contractOrderDO.setUnfilledAmount(100L);
        contractOrderDO.setUserId(userId);
        contractOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        int insertRet = contractOrderMapper.insertSelective(contractOrderDO);
        Assert.assertTrue(insertRet > 0);
    }


    @Test
    public void testCountByQuery() throws Exception {
        ContractOrderQuery contractOrderQuery = new ContractOrderQuery();
        contractOrderQuery.setUserId(userId);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        Integer count = contractOrderMapper.countByQuery(ParamUtil.objectToMap(contractOrderQuery));
        Assert.assertTrue(count > 0);
    }

    @Test
    public void testListByQuery() throws Exception {
        ContractOrderQuery contractOrderQuery = new ContractOrderQuery();
        contractOrderQuery.setUserId(userId);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        List<ContractOrderDO> list = contractOrderMapper.listByQuery((ParamUtil.objectToMap(contractOrderQuery)));
        Assert.assertTrue(list != null && list.size() > 0);
    }


    @Test
    public void testSelectUnfinishedOrder() throws Exception {
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByUserId(userId);
        log.info("----------------------------" + list.size());
    }

    @Test
    public void testUpdateAmountAndStatus() throws Exception {

        Long filledAmount = 1L;
        BigDecimal filledPrice = new BigDecimal(0.3);
        int aff = contractOrderMapper.updateAmountAndStatus(contractOrderDO.getId(),new BigDecimal(filledAmount), filledPrice);
        ContractOrderDO contractOrderDO2 = contractOrderMapper.selectByPrimaryKey(contractOrderDO.getId());

        BigDecimal expectAvgPrice = PriceUtil.getAveragePrice(contractOrderDO.getAveragePrice(),
                new BigDecimal(contractOrderDO.getTotalAmount() - contractOrderDO.getUnfilledAmount()), new BigDecimal(filledAmount), filledPrice);

        BigDecimal wucha = new BigDecimal(1e-6);
        Assert.assertTrue(contractOrderDO2.getUnfilledAmount() == contractOrderDO.getUnfilledAmount() - filledAmount
                && contractOrderDO2.getAveragePrice().subtract(expectAvgPrice).compareTo(wucha)<0
                && contractOrderDO2.getStatus() == PART_MATCH.getCode()
        );
    }

    @Test
    public void testLamda() throws Exception {
        long userId = 282L;
        List<ContractOrderDO> orderList = null;
        /*List<ContractOrderDO> bidList = orderList.stream().filter(order-> order.getUsdkLockedAmount().compareTo(BigDecimal.ZERO)>0)
                .collect(Collectors.toList());*/
        if (orderList != null && orderList.size() != 0) {
            log.info("----------------------------");
        } else {
            log.info("++++++++++++++++++++++++++++");
        }
    }

}
