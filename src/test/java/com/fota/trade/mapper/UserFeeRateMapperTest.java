package com.fota.trade.mapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/9/20 10:26
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class UserFeeRateMapperTest {
    @Autowired
    private UserFeeRateMapper userFeeRateMapper;

    @Test
    public void getUserFeeRateTest(){

    }
}
