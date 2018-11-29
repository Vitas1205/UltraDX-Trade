package com.fota.trade.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/25 19:48
 * @Modified:
 */
//@RunWith(SpringRunner.class)
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
@Transactional
public class ContractOrderTest {
    @Autowired
    private ContractOrderManager contractOrderManager;



}
