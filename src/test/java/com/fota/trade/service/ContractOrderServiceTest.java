package com.fota.trade.service;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.client.domain.query.ContractOrderQuery;
import com.fota.client.service.ContractOrderService;
import com.fota.trade.domain.ContractMatchedOrderDTO;
import com.fota.trade.domain.ContractOrderDTOPage;
import com.fota.trade.domain.ContractOrderQueryDTO;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
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
    private ContractOrderServiceImpl contractOrderService;

    @Test
    public void testListContractOrderByQuery() throws Exception {
        ContractOrderQueryDTO contractOrderQuery = new ContractOrderQueryDTO();
        contractOrderQuery.setUserId(9527L);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        ContractOrderDTOPage result = contractOrderService.listContractOrderByQuery(contractOrderQuery);
        Assert.assertTrue(result != null && result.getData() != null);
    }

    @Test
    public void testUpdateOrderByMatch() throws Exception {


//        public long id;
//        public long askOrderId;
//        public String askOrderPrice;
//        public long bidOrderId;
//        public String bidOrderPrice;
//        public String filledPrice;
//        public long filledAmount;
//        public long contractId;
//        public String contractName;
//        public long gmtCreate;
//        public int matchType;
//        public String assetName;
//        public int contractType;

        ContractMatchedOrderDTO  contractMatchedOrderDTO = new ContractMatchedOrderDTO();
        contractMatchedOrderDTO.setAskOrderId(361);
        contractMatchedOrderDTO.setBidOrderId(426);
        contractMatchedOrderDTO.setFilledPrice("6000");
        contractMatchedOrderDTO.setFilledAmount(15);
        contractMatchedOrderDTO.setBidOrderPrice("6100");
        contractMatchedOrderDTO.setAskOrderPrice("6000");
        ResultCode resultCode = contractOrderService.updateOrderByMatch(contractMatchedOrderDTO);


    }

}
