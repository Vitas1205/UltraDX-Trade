package com.fota.trade.common;

import com.alibaba.fastjson.JSON;
import com.fota.match.domain.MatchedOrderMarketDTO;
import com.fota.trade.domain.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        contractCategoryDO.setTotalAmount(20L);
        contractCategoryDO.setUnfilledAmount(20L);
        contractCategoryDO.setDeliveryDate(new Date());
        contractCategoryDO.setStatus(2);
        contractCategoryDO.setContractType(2);
        contractCategoryDO.setContractSize(new BigDecimal(0.01));
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
        contractCategoryDTO.setContractSize(new BigDecimal(0.01));
        BeanUtils.copy(contractCategoryDTO);
    }


}
