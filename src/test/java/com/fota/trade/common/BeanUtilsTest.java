package com.fota.trade.common;

import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractCategoryDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.UsdkOrderDTO;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@RunWith(JUnit4.class)
//@SpringBootTest
//@Transactional
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
        usdkOrderDTO.setGmtCreate(new Date());
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        org.springframework.beans.BeanUtils.copyProperties(usdkOrderDTO, usdkOrderDO);
        Assert.assertTrue(usdkOrderDO.getUserId() == 234L);
    }

    @Test
    public void testCopyContractCategoryDO() throws Exception {
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        contractCategoryDO.setId(2L);
        contractCategoryDO.setGmtCreate(new Date());
        contractCategoryDO.setGmtModified(new Date());
        contractCategoryDO.setContractName("BTC0203");
        contractCategoryDO.setAssetId(2);
        contractCategoryDO.setAssetName("BTC");
        contractCategoryDO.setTotalAmount(BigDecimal.valueOf(20));
        contractCategoryDO.setUnfilledAmount(BigDecimal.valueOf(20));
        contractCategoryDO.setDeliveryDate(new Date());
        contractCategoryDO.setStatus(2);
        contractCategoryDO.setContractType(2);
        BeanUtils.copy(contractCategoryDO);
    }

    @Test
    public void testCopyContractCategoryDTO() throws Exception {
        ContractCategoryDTO contractCategoryDTO = new ContractCategoryDTO();
        contractCategoryDTO.setId(2L);
        contractCategoryDTO.setGmtCreate(new Date());
        contractCategoryDTO.setGmtModified(new Date());
        contractCategoryDTO.setContractName("BTC0203");
        contractCategoryDTO.setAssetId(2);
        contractCategoryDTO.setAssetName("BTC");
        contractCategoryDTO.setTotalAmount(20L);
        contractCategoryDTO.setUnfilledAmount(20L);
        contractCategoryDTO.setDeliveryDate(new Date().getTime());
        contractCategoryDTO.setStatus(2);
        contractCategoryDTO.setContractType(2);
        BeanUtils.copy(contractCategoryDTO);
    }


}
