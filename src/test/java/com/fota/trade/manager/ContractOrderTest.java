package com.fota.trade.manager;

import com.fota.asset.domain.UserContractDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/25 19:48
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class ContractOrderTest {
    @Autowired
    ContractOrderManager contractOrderManager;

    @Test
    public void test() {
        Long userId = 188L;
        Map<String, BigDecimal> resultMap = new HashMap<>();
        resultMap = contractOrderManager.getAccountMsg(userId);
        log.info("--------"+resultMap);
    }
}
