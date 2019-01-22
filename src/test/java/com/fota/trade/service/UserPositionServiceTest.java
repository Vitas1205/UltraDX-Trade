package com.fota.trade.service;

import com.fota.common.Page;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.DeliveryCompletedDTO;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.UserPositionDTO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.query.UserPositionQuery;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.mapper.trade.ContractMatchedOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class UserPositionServiceTest {

    @Autowired
    private UserPositionService userPositionService;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Resource
    private ContractOrderManager contractOrderManager;
    @Resource
    private RedisManager redisManager;

    @Test
    public void testListPositionByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setUserId(9528L);
        userPositionQuery.setContractId(1001L);
        Page<UserPositionDTO> page = userPositionService.listPositionByQuery(482,100, 1, 10 );
//        Assert.assertTrue(null != page && null != page.getData());
    }

    @Test
    public void testlistPositionByUserIdAndContractId() throws Exception {
        Page<UserPositionDTO> page = userPositionService.listPositionByUserIdAndContractId(188L,null, 1, 10 );
        log.info(page.toString());
//        Assert.assertTrue(null != page && null != page.getData());
    }

    @Test
    public void listPositionByUserIdTest(){
        List<UserPositionDTO> list = userPositionService.listPositionByUserId(282L);
        log.info("----"+list.size());
    }


    @Test
    public void testDeliveryPosition() {
        DeliveryCompletedDTO deliveryCompletedDTO = new DeliveryCompletedDTO();
        deliveryCompletedDTO.setContractId(1001L);
        deliveryCompletedDTO.setUserId(9528L);
        deliveryCompletedDTO.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        deliveryCompletedDTO.setAmount(BigDecimal.ONE);
        deliveryCompletedDTO.setFee(BigDecimal.ONE);
        deliveryCompletedDTO.setUserPositionId(1L);
        deliveryCompletedDTO.setContractName("BTC0102");
        deliveryCompletedDTO.setPrice(BigDecimal.ONE);

        ResultCode resultCode =userPositionService.deliveryPosition(deliveryCompletedDTO);


        List<ContractMatchedOrderDO> contractMatchedOrderDOS = contractMatchedOrderMapper.listByUserId(9528L, Arrays.asList(1001L), 0, 100, null, null);
        System.out.println(contractMatchedOrderDOS.get(0));
    }

    @Test
    public void test_getTotalPositionByContractId() {
        BigDecimal total = userPositionService.getTotalPositionByContractId(1006L);
        System.out.println("totalPosition" + total);
    }

    @Test
    public void test_updateTotalPosition() {
        ContractMatchedOrderDO contractMatchedOrderDO = new ContractMatchedOrderDO();
        contractMatchedOrderDO.setUserId(211L);
        contractMatchedOrderDO.setContractId(1000L);
        contractMatchedOrderDO.setFilledAmount(new BigDecimal("8"));
        //contractOrderManager.updateTotalPosition(contractMatchedOrderDO);
    }

    @Test
    public void test_counter() {
        Object a = redisManager.get("test_counter_aaaa");
        log.info("before:" + a);
        Long result = redisManager.counter("test_counter_aaaa", 0L);
        if (result != null) {
            log.info("resulttt: " + result);
        }
        Object b = redisManager.get("test_counter_aaaa");
        log.info("aaaaaaaaaaa:" + b);
    }


}
