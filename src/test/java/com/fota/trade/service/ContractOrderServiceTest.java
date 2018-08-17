package com.fota.trade.service;

import com.fota.common.Page;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDate;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

import static com.fota.trade.domain.enums.OrderStatusEnum.COMMIT;
import static com.fota.trade.domain.enums.OrderStatusEnum.PART_MATCH;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class ContractOrderServiceTest {

    @Resource
    private ContractOrderServiceImpl contractOrderService;
    @Resource
    private ContractOrderManager contractOrderManager;
    @Resource
    private ContractOrderMapper contractOrderMapper;

    @Test
    public void testListContractOrderByQuery() throws Exception {
        BaseQuery contractOrderQuery = new BaseQuery();
        contractOrderQuery.setUserId(282L);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        contractOrderQuery.setEndTime(LocalDate.now().plusDays(1).toDate());
        contractOrderQuery.setSourceId(1000);
        contractOrderQuery.setOrderStatus(Arrays.asList(PART_MATCH.getCode(), COMMIT.getCode()));
        Page<ContractOrderDTO> result = contractOrderService.listContractOrderByQuery(contractOrderQuery);
//        Assert.assertTrue(result != null && result.getData() != null);
    }

    @Test
    public void testUpdateOrderByMatch() throws Exception {


//        public long id;
//        public long askOrderId;
//        public String askOrderPrice;
//        public long bidOrderId;
//        public String bidOrderPrice;
//        public String filledPrice;
//        public long filledAmount;
//        public long contractId;
//        public String contractName;
//        public long gmtCreate;
//        public int matchType;
//        public String assetName;
//        public int contractType;

        ContractMatchedOrderDTO  contractMatchedOrderDTO = new ContractMatchedOrderDTO();
        contractMatchedOrderDTO.setAskOrderId(428l);
        contractMatchedOrderDTO.setBidOrderId(430l);
        contractMatchedOrderDTO.setFilledPrice("6500");
        contractMatchedOrderDTO.setFilledAmount(5l);
        contractMatchedOrderDTO.setBidOrderPrice("6510");
        contractMatchedOrderDTO.setAskOrderPrice("6500");
//        ResultCode resultCode = contractOrderService.updateOrderByMatch(contractMatchedOrderDTO);


    }

    @Test
    public void getTodayFeeTest(){
        BigDecimal ret = contractOrderService.getTodayFee();
        log.info("--------------------------"+ret);
    }

    @Test
    public void insertOrderRecordTest(){
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setUserId(282L);
        contractOrderDO.setTotalAmount(12L);
        contractOrderDO.setContractId(1001L);
        contractOrderDO.setOrderDirection(1);
        contractOrderDO.setContractName("BTC0203");
        Long ret = contractOrderManager.insertOrderRecord(contractOrderDO);
        log.info("--------------------------"+ret);
    }

    @Test
    public void insertTest(){
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
        contractOrderDO.setUserId(31L);
        contractOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        Integer ret = contractOrderMapper.insert(contractOrderDO);
        log.info("--------------------------"+ret);
    }

}
