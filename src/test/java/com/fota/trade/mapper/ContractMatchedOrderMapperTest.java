package com.fota.trade.mapper;

import com.fota.trade.domain.ContractMatchedOrderDO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author: Carl Zhang
 * @Descripyion:
 * @Date: Create in 下午 22:06 2018/8/23 0023
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@Transactional
public class ContractMatchedOrderMapperTest {
    @Resource
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Test
    public void test_getLatestContractMatched() {
        long time = System.currentTimeMillis();
        long id = contractMatchedOrderMapper.getLatestContractMatched();
        log.info("id== :{}, time :{}",id, System.currentTimeMillis() - time);
    }

    @Test
    public void test_getLatestContractMatchedList() {
        long time = System.currentTimeMillis();
        List<ContractMatchedOrderDO> list = contractMatchedOrderMapper.getLatestContractMatchedList(1010L, 610273L);
        log.info("result:{}, time :{}",list.toString(), System.currentTimeMillis() - time);
    }
}
