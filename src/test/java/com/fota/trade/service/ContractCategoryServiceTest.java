package com.fota.trade.service;

import com.fota.client.service.ContractCategoryService;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.enums.ContractStatusEnum;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ContractCategoryServiceTest {

    @Resource
    private ContractCategoryService contractCategoryService;

    @Test
    public void testListActiveContract() throws Exception {
        List<ContractCategoryDO> list = contractCategoryService.listActiveContract();
        Assert.assertTrue(list != null);
    }

    @Test
    public void testListActiveContractByAssetName() throws Exception {
        List<ContractCategoryDO> list = contractCategoryService.listActiveContractByAssetId(3);
        Assert.assertTrue(list != null);
    }

    @Test
    public void testGetContractById() throws Exception {
        ContractCategoryDO contractCategoryDO = contractCategoryService.getContractById(3L);
        Assert.assertTrue(contractCategoryDO != null);
    }

    @Test
    public void testSaveContract() throws Exception {
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO(3L,
                new Date(), new Date(), "ETC0932", 3, "ETH",
                100L,100L, new Date(), 2, 1,new BigDecimal("1.1"));
        Integer saveRet = contractCategoryService.saveContract(contractCategoryDO);
        Assert.assertTrue(saveRet != null && saveRet > 0);
    }

    @Test
    public void testUpdateContract() throws Exception {
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        contractCategoryDO.setId(2L);
        contractCategoryDO.setStatus(ContractStatusEnum.PROCESSING.getCode());
        Integer updateRet = contractCategoryService.updateContract(contractCategoryDO);
        Assert.assertTrue(updateRet != null && updateRet > 0);
    }

    @Test
    public void testRemoveContract() throws Exception {
        Integer deleteRet = contractCategoryService.removeContract(2L);
        Assert.assertTrue(deleteRet != null && deleteRet > 0);
    }

}
