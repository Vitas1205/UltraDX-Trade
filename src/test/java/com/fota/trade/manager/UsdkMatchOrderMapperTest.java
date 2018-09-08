package com.fota.trade.manager;

import com.fota.trade.mapper.UsdkMatchedOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/8/31 15:27
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
@Transactional
public class UsdkMatchOrderMapperTest {

    @Autowired
    UsdkMatchedOrderMapper usdkMatchedOrderMapper;

    @Test
    public void countByUserIdTest(){
        /*Long userId = 285L;
        List<Long> assetIds = new ArrayList<>();
        assetIds.add(1L);
       // assetIds.add(2L);
        int ret = usdkMatchedOrderMapper.countByUserId(userId,assetIds,null,null);
        log.info("----------"+ret);*/
    }
}
