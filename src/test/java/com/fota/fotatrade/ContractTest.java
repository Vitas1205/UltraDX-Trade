package com.fota.fotatrade;

import com.fota.trade.common.BeanUtils;
import com.fota.trade.domain.ContractOrderDTO;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/12 10:59
 * @Modified:
 */
@SpringBootTest
@Slf4j
@RunWith(SpringRunner.class)
public class ContractTest {

    @Autowired
    private ContractOrderServiceImpl contractOrderService;

    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Test
    public void placeOrder(){
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        contractOrderDTO.setContractId(3);
        contractOrderDTO.setContractName("test3");
        contractOrderDTO.setUserId(9527L);
        contractOrderDTO.setOrderDirection(1);
        contractOrderDTO.setOrderType(1);
        contractOrderDTO.setOperateDirection(1);
        contractOrderDTO.setOperateType(1);
        contractOrderDTO.setTotalAmount(5);
        contractOrderDTO.setUnfilledAmount(5);
        contractOrderDTO.setPrice("20");
        contractOrderDTO.setFee("0.01");
        contractOrderDTO.setStatus(8);
        //contractOrderService.order(contractOrderDTO);
        int insertContractOrderRet = contractOrderMapper.insertSelective(BeanUtils.copy(contractOrderDTO));
    }

}
