package com.fota.trade.common;

import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.UsdkOrderDTO;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Date;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@RunWith(JUnit4.class)
//@SpringBootTest
//@Transactional
public class BeanUtilsTest {

    @Test
    public void testCopy() throws Exception {
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        usdkOrderDTO.setUserId(234L);
        usdkOrderDTO.setGmtCreate(new Date());
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        org.springframework.beans.BeanUtils.copyProperties(usdkOrderDTO, usdkOrderDO);
        Assert.assertTrue(usdkOrderDO.getUserId() == 234L);
    }


}
