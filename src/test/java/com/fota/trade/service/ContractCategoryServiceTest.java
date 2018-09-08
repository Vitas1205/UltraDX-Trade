package com.fota.trade.service;

import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractCategoryDTO;
import com.fota.trade.domain.enums.ContractStatus;
import com.fota.trade.domain.enums.ContractTypeEnum;
import com.fota.trade.service.impl.ContractCategoryServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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
@Transactional
public class ContractCategoryServiceTest {

    @Resource
    private ContractCategoryServiceImpl contractCategoryService;

    @Test
    public void testListActiveContract() throws Exception {
        List<ContractCategoryDTO> list = contractCategoryService.listActiveContract();
        Assert.assertTrue(list != null);
    }

    @Test
    public void testListActiveContractByAssetName() throws Exception {
        List<ContractCategoryDTO> list = contractCategoryService.listActiveContractByAssetId(2);
        Assert.assertTrue(list != null);
    }

    @Test
    public void testGetContractById() throws Exception {
        ContractCategoryDTO contractCategoryDO = contractCategoryService.getContractById(1000L);
        Assert.assertTrue(contractCategoryDO != null);
    }

    @Test
    public void testGetContractByStatus() throws Exception {
        List<ContractCategoryDTO> list = contractCategoryService.getContractByStatus(2);
        Assert.assertTrue(!CollectionUtils.isEmpty(list));
    }

    @Test
    public void testUpdataStatusById() throws Exception {
        //int ret = contractCategoryService.updateContractStatus(1056L, ContractStatus.DELETED);
        //Assert.assertTrue(ret>0);
    }

    @Test
    public void testSaveContract() throws Exception {
        ContractCategoryDTO newContract = new ContractCategoryDTO();
        newContract.setId(null);
        newContract.setContractName("test_btc3");
        newContract.setAssetId(0);
        newContract.setAssetName("btc");
        newContract.setStatus(ContractStatus.UNOPENED.getCode());
        newContract.setTotalAmount(0L);
        newContract.setUnfilledAmount(0L);
        newContract.setDeliveryDate(System.currentTimeMillis());
        newContract.setContractType(ContractTypeEnum.MONTH.getCode());
        newContract.setGmtCreate(new Date());
        newContract.setGmtModified(new Date());
        newContract.setContractSize(new BigDecimal(0));
        //Integer saveRet = contractCategoryService.saveContract(newContract);
        //Assert.assertTrue(saveRet != null && saveRet > 0);
    }

    @Test
    public void testUpdateContract() throws Exception {
//        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
//        contractCategoryDO.setId(2L);
//        contractCategoryDO.setStatus(ContractStatusEnum.PROCESSING.getCode());
//        Integer updateRet = contractCategoryService.updateContract(contractCategoryDO);
//        Assert.assertTrue(updateRet != null && updateRet > 0);
    }

    @Test
    public void testRemoveContract() throws Exception { //need insert before
        Integer deleteRet = contractCategoryService.removeContract(2L);
//        Assert.assertTrue(deleteRet != null && deleteRet > 0);
    }

}
