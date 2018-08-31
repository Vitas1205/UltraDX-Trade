package com.fota.trade.mapper;

import com.fota.trade.domain.UsdkMatchedOrderDO;
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
 * @Date: Create in 下午 21:27 2018/8/23 0023
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@Transactional
public class UsdkMatchedOrderMapperTest {
    @Resource
    private UsdkMatchedOrderMapper usdkMatchedOrderMapper;

    @Test
    public void test_getLatestUsdkMatched() {
        UsdkMatchedOrderDO id = usdkMatchedOrderMapper.getLatestUsdkMatched();
        log.info("id== :{}",id.getId());
    }

    @Test
    public void test_getLatestUsdkMatchedList() {
        List<UsdkMatchedOrderDO> list = usdkMatchedOrderMapper.getLatestUsdkMatchedList(2,1000L);
        log.info("list usdkMatchedOrder :{}", list.toString());
    }

}
