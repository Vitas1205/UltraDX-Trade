package com.fota.fotatrade;

import com.fota.client.domain.UsdkOrderDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/8 11:24
 * @Modified:
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class UsdkTradeTest {

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Test
    public void InsertTest() {
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        usdkOrderDO.setStatus(8);
        usdkOrderDO.setFee(BigDecimal.valueOf(0.01));
        usdkOrderDO.setPrice(BigDecimal.valueOf(12));
        usdkOrderDO.setAssetId(2);
        usdkOrderDO.setAssetName("BTC");
        usdkOrderDO.setTotalAmount(BigDecimal.valueOf(20));
        usdkOrderDO.setUnfilledAmount(BigDecimal.valueOf(20));
        usdkOrderDO.setOrderType(1);
        usdkOrderDO.setOrderDirection(1);
        usdkOrderDO.setUserId(1L);
        //usdkOrderMapper.insertSelective(usdkOrderDO);
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
        log.info(usdkOrderDTO.getAssetName().toString());
    }

    @Test
    public void orderTest() throws Exception{
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        usdkOrderDO.setPrice(BigDecimal.valueOf(100));
        usdkOrderDO.setAssetId(2);
        usdkOrderDO.setAssetName("BTC");
        usdkOrderDO.setTotalAmount(BigDecimal.valueOf(5));
        usdkOrderDO.setOrderType(1);
        usdkOrderDO.setOrderDirection(1);
        usdkOrderDO.setUserId(282L);
//        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
//        BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
//        log.info("======================="+usdkOrderDTO.getAssetName());
        usdkOrderManager.placeOrder(usdkOrderDO);
    }

    @Test
    public void updateTest(){

    }

    @Test
    public void cancelTest() throws Exception{
        usdkOrderManager.cancelAllOrder(175L);
    }


    @Test
    public void getByUserIdTest(){
        Long userId = 1L;
        List<UsdkOrderDO> list = usdkOrderMapper.selectByUserId(userId);
        log.info("------------------"+list.size());
    }
}
