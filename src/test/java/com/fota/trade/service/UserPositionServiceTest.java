package com.fota.trade.service;

import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.DeliveryCompletedDTO;
import com.fota.trade.domain.UserPositionDTO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.query.UserPositionQuery;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    public void testListPositionByQuery() throws Exception {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setUserId(9528L);
        userPositionQuery.setContractId(1001L);
        com.fota.common.Page<com.fota.trade.domain.UserPositionDTO> page = userPositionService.listPositionByQuery(482,100, 1, 10 );
//        Assert.assertTrue(null != page && null != page.getData());
    }

    @Test
    public void listPositionByUserIdTest(){
        List<UserPositionDTO> list = userPositionService.listPositionByUserId(285L);
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

        userPositionService.deliveryPosition(deliveryCompletedDTO);

        List<ContractMatchedOrderDO> contractMatchedOrderDOS = contractMatchedOrderMapper.listByUserId(9528L, Arrays.asList(1001L), 0, 100, null, null);
        System.out.println(contractMatchedOrderDOS.get(0));
    }
}
