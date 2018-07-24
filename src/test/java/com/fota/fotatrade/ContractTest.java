package com.fota.fotatrade;

import com.fota.client.domain.CompetitorsPriceDTO;
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

import java.math.BigDecimal;
import java.util.ArrayList;
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
        for (int i = 0;i < 1;i++){
            List<CompetitorsPriceDTO> list = new ArrayList<>();
            CompetitorsPriceDTO competitorsPriceDTO1 = new CompetitorsPriceDTO();
            competitorsPriceDTO1.setId(1000);
            competitorsPriceDTO1.setOrderDirection(1);
            competitorsPriceDTO1.setPrice(new BigDecimal("5600.22"));
            CompetitorsPriceDTO competitorsPriceDTO2 = new CompetitorsPriceDTO();
            competitorsPriceDTO2.setId(1000);
            competitorsPriceDTO2.setOrderDirection(2);
            competitorsPriceDTO2.setPrice(new BigDecimal("6600.22"));
            list.add(competitorsPriceDTO1);
            list.add(competitorsPriceDTO2);

            ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
            contractOrderDTO.setContractId(1000);
            contractOrderDTO.setContractName("BTC0102");
            contractOrderDTO.setUserId(282L);
            contractOrderDTO.setOrderDirection(1);
            contractOrderDTO.setOperateType(1);
            contractOrderDTO.setOperateDirection(2);
            contractOrderDTO.setOrderType(1);
            contractOrderDTO.setTotalAmount(2L);
            contractOrderDTO.setPrice("7000");
            contractOrderService.order(contractOrderDTO, list);
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
