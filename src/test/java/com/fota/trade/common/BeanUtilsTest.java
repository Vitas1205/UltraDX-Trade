package com.fota.trade.common;

import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractCategoryDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.UsdkOrderDTO;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class BeanUtilsTest {

    @Test
    public void testCopyProperties() throws Exception {
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        contractCategoryDO.setAssetId(1);
        List<ContractCategoryDO> list = new ArrayList<>();
        list.add(contractCategoryDO);
        List a = BeanUtils.copyList(list, ContractCategoryDTO.class);
        Assert.assertTrue(a != null);
    }

    @Test
    public void testCopy() throws Exception {
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        usdkOrderDTO.setUserId(234L);
        usdkOrderDTO.setGmtCreate(new Date().getTime());
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        org.springframework.beans.BeanUtils.copyProperties(usdkOrderDTO, usdkOrderDO);
        Assert.assertTrue(usdkOrderDO.getUserId() == 234L);
    }

}
