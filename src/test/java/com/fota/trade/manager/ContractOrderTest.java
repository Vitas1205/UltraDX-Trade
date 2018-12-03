package com.fota.trade.manager;

import com.fota.trade.client.ContractMarginDTO;
import com.fota.trade.domain.ContractOrderDTO;
import com.fota.trade.domain.enums.OrderTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/25 19:48
 * @Modified:
 */
@Slf4j
@Transactional
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ContractOrderTest {

    @Autowired
    private ContractOrderManager contractOrderManager;

    @Test
    public void test_precise_margin() {
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        contractOrderDTO.setUserId(17764594153L);
        contractOrderDTO.setContractId(1204L);
        contractOrderDTO.setContractName("BTC1901");
        contractOrderDTO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        contractOrderDTO.setPrice(BigDecimal.valueOf(3900.1));
        contractOrderDTO.setEntrustValue(BigDecimal.valueOf(0.01));
        Optional<ContractMarginDTO> preciseContractMargin = contractOrderManager.getPreciseContractMargin(contractOrderDTO);
        System.out.println(preciseContractMargin);
    }
}
