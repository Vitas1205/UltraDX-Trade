package com.fota.trade.manager;

import com.fota.asset.domain.UserContractDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/25 19:48
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
@Transactional
public class ContractOrderTest {
    @Autowired
    private ContractOrderManager contractOrderManager;



}
