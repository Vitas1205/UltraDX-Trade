package com.fota.trade.mapper;

import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.mapper.trade.ContractCategoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@Transactional
public class ContractCategoryMapperTest {

    @Resource
    private ContractCategoryMapper contractCategoryMapper;

    @Test
    public void testInsert() throws Exception {
        /*ContractCategoryDO contractCategoryDO = new ContractCategoryDO(3L,
                new Date(), new Date(), "ETC0931", 3, "ETH",
                100L,100L, new Date(), 2, 1,new BigDecimal("1.1"));
        int insertRet = contractCategoryMapper.insert(contractCategoryDO);
        Assert.assertTrue(insertRet > 0);*/
    }

    @Test
    public void testListByQuery() throws Exception {
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        contractCategoryDO.setAssetId(3);
        List<ContractCategoryDO> queryList = contractCategoryMapper.listByQuery(contractCategoryDO);
        Assert.assertTrue(queryList != null && queryList.size() > 0);
    }

    @Test
    public void testSelectContractCategory() throws Exception {
        List<ContractCategoryDO> queryList = contractCategoryMapper.getAllContractCategory();
        log.info("---------------------"+queryList.size());
    }

}
