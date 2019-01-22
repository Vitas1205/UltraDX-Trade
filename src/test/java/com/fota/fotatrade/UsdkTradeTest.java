package com.fota.fotatrade;

import com.fota.trade.domain.UsdkOrderDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.trade.UsdkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/8 11:24
 * @Modified:
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class UsdkTradeTest {

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Autowired
    private RedisManager redisManager;

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
//        usdkOrderManager.placeOrder(usdkOrderDO);
    }

    @Test
    public void updateTest(){
        log.info("---------"+redisManager.get("fota_usdk_entrust_1"));
    }

    @Test
    public void cancelTest() throws Exception{
        Map<String, String> map = new HashMap<>();
        map.put("usernmae", "123");
        map.put("ip", "192.169.1.1");
        //usdkOrderManager.cancelAllOrder(175L, map);
    }


    @Test
    public void getByUserIdTest(){
        BigDecimal a = new BigDecimal("2.3");
        BigDecimal b = new BigDecimal("2.300");
        log.info("--------"+a.hashCode());
        log.info("--------"+b.hashCode());
    }

    @Test
    public void beanUtilCopyTest() throws Exception{
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        usdkOrderDTO.setUserId(90L);
        UsdkOrderDO usdkOrderDO = com.fota.trade.common.BeanUtils.copy(usdkOrderDTO);
        System.out.println(usdkOrderDO.toString());
    }

    @Test
    public void getJudgeTest(){
//        boolean ret = usdkOrderManager.getJudegRet(614L,2,new BigDecimal(1));
//        log.info("--------"+ret);
    }

    @Test
    public void update(){
//        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
//        usdkOrderDO.setId(46L);
//        UsdkOrderDO usdkOrderDO2 = usdkOrderMapper.selectByUserIdAndId(usdkOrderDO.getId());
//        log.info("更新后的记录"+usdkOrderDO.getId()+":"+usdkOrderDO2);
//        if (usdkOrderDO2.getUnfilledAmount().compareTo(BigDecimal.ZERO) == 0){
//            usdkOrderDO2.setStatus(OrderStatusEnum.MATCH.getCode());
//            usdkOrderMapper.updateStatus(usdkOrderDO2);
//        }
    }
}
