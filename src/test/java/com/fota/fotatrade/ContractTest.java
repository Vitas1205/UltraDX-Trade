package com.fota.fotatrade;

import com.fota.trade.common.BeanUtils;
import com.fota.trade.domain.ContractOrderDO;
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

import java.util.List;

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
        for (int i = 0;i < 3;i++){
            ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
            contractOrderDTO.setContractId(7);
            contractOrderDTO.setContractName("test7");
            contractOrderDTO.setUserId(9527L);
            contractOrderDTO.setOrderDirection(1);
            contractOrderDTO.setOrderType(1);
            contractOrderDTO.setOperateDirection(1);
            contractOrderDTO.setOperateType(1);
            contractOrderDTO.setTotalAmount(15);
            //contractOrderDTO.setUnfilledAmount(15);
            contractOrderDTO.setPrice("2");
            contractOrderService.order(contractOrderDTO);
        }
        //int insertContractOrderRet = contractOrderMapper.insertSelective(BeanUtils.copy(contractOrderDTO));
    }

    @Test
    public void cancelOrder(){
        Long userId = 9527L;
        Long orderId = 349L;
        contractOrderService.cancelOrder(userId, orderId);
    }
    @Test
    public void cancleAllOrder(){
        Long userId = 9527L;
        contractOrderService.cancelAllOrder(userId);
    }

    @Test
    public void TestSelect(){
        Long userId = 9527L;
        /*Long orderId = 349L;
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByIdAndUserId(orderId,userId);
        log.info(contractOrderDO.toString());*/
        List<ContractOrderDO> list = contractOrderMapper.selectByUserId(userId);
        log.info("----------------"+list.size());
    }

}
