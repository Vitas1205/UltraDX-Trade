package com.fota.trade.manager;

import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.mapper.sharding.ContractMatchedOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/31 14:56
 * @Modified:
 */
//@RunWith(SpringRunner.class)
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
@Transactional
public class ContractMatchOrderMapperTest {

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

//    @Test
    public void listByUserIdTest(){
        Long userId = 285L;
        List<Long> contractIds = new ArrayList<>();
        contractIds.add(1000L);
        List<ContractMatchedOrderDO> list = contractMatchedOrderMapper.listByUserId
                (userId, contractIds, 0,20,null,null);
        log.info("_____"+list);
    }

//    @Test
    public void countByUserIdTest(){
        Long userId = 285L;
        List<Long> contractIds = new ArrayList<>();
        contractIds.add(1000L);
        int ret = contractMatchedOrderMapper.countByUserId(userId, contractIds, null, null);
        log.info("----"+ret);
    }

//    @Test
    public void getAllFeeTest(){
        Date end = new Date();
        Date start = new Date(14000000000L);
        //BigDecimal ret = contractMatchedOrderMapper.getAllFee(start, end);
        //log.info("----"+ret);
    }
}
