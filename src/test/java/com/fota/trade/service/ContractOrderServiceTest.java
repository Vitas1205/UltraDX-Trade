package com.fota.trade.service;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.client.domain.query.ContractOrderQuery;
import com.fota.client.service.ContractOrderService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ContractOrderServiceTest {

    @Resource
    private ContractOrderService contractOrderService;

    @Test
    public void testListContractOrderByQuery() throws Exception {
        ContractOrderQuery contractOrderQuery = new ContractOrderQuery();
        contractOrderQuery.setUserId(9527L);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        Result<Page<ContractOrderDTO>> result = contractOrderService.listContractOrderByQuery(contractOrderQuery);
        Assert.assertTrue(result != null && result.getData() != null && result.getData().getData() != null);
    }

}
