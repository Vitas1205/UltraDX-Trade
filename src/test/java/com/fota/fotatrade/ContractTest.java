package com.fota.fotatrade;

import com.fota.trade.common.Constant;
import com.fota.trade.common.TestConfig;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractOrderDTO;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.PositionStatusEnum;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fota.trade.common.TestConfig.userId;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/12 10:59
 * @Modified:
 */
@SpringBootTest
@Slf4j
@RunWith(SpringRunner.class)
@Transactional
public class ContractTest {

    @Autowired
    private ContractOrderServiceImpl contractOrderService;

    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Autowired
    private  ContractOrderManager contractOrderManager;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private ContractCategoryMapper contractCategoryMapper;

    @Autowired
    private RedisManager redisManager;

    @Test
    public void placeOrder(){
        for (int i = 0;i < 1;i++){
            /*List<CompetitorsPriceDTO> list = new ArrayList<>();
            CompetitorsPriceDTO competitorsPriceDTO1 = new CompetitorsPriceDTO();
            competitorsPriceDTO1.setId(1000);
            competitorsPriceDTO1.setOrderDirection(1);
            competitorsPriceDTO1.setPrice(new BigDecimal("5600.22"));
            CompetitorsPriceDTO competitorsPriceDTO2 = new CompetitorsPriceDTO();
            competitorsPriceDTO2.setId(1000);
            competitorsPriceDTO2.setOrderDirection(2);
            competitorsPriceDTO2.setPrice(new BigDecimal("6600.22"));
            list.add(competitorsPriceDTO1);
            list.add(competitorsPriceDTO2);*/

            ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
            //contractOrderDTO.setContractId(1000);
            contractOrderDTO.setContractName("BTC0906");
            contractOrderDTO.setContractId(1214L);
            contractOrderDTO.setUserId(userId);
            contractOrderDTO.setOrderDirection(BID.getCode());
            contractOrderDTO.setOperateType(0);
            contractOrderDTO.setOrderType(1);
            contractOrderDTO.setPriceType(1);
            contractOrderDTO.setTotalAmount(BigDecimal.ONE);
            contractOrderDTO.setPrice(new BigDecimal("8500"));
            Map<String, String> map = new HashMap<>();
            map.put("usernmae", "123");
            map.put("ip", "192.169.1.1");
            ResultCode result = contractOrderService.order(contractOrderDTO,map);
            log.info("result={}", result);
        }
//        int insertContractOrderRet = contractOrderMapper.insertSelective(BeanUtils.copy(contractOrderDTO));
    }

    @Test
    public void cancelOrderWithCompetitorsPrice() throws Exception{
        /*List<CompetitorsPriceDTO> list = new ArrayList<>();
        CompetitorsPriceDTO competitorsPriceDTO1 = new CompetitorsPriceDTO();
        competitorsPriceDTO1.setId(1000);
        competitorsPriceDTO1.setOrderDirection(1);
        competitorsPriceDTO1.setPrice(new BigDecimal("5600.22"));
        CompetitorsPriceDTO competitorsPriceDTO2 = new CompetitorsPriceDTO();
        competitorsPriceDTO2.setId(1000);
        competitorsPriceDTO2.setOrderDirection(2);
        competitorsPriceDTO2.setPrice(new BigDecimal("6600.22"));
        list.add(competitorsPriceDTO1);
        list.add(competitorsPriceDTO2);*/

        long userId = 200L;
        long contractId = 332L;
//        ResultCode resultCode = contractOrderManager.cancelOrder(userId,contractId);
//        log.info("-------------------"+resultCode.toString());
    }

    @Test
    public void cancelOrder(){
        Long userId = 9527L;
        Long orderId = 349L;
//        contractOrderService.cancelOrder(userId, orderId);
    }
    @Test
    public void cancleAllOrder(){
        Long userId = 284L;
        //contractOrderService.cancelAllOrder(userId);
    }

    @Test
    public void competitorsPriceList_test(){
        Object competiorsPriceObj = redisManager.get(Constant.CONTRACT_COMPETITOR_PRICE_KEY);
        //List<CompetitorsPriceDTO> competitorsPriceList = JSON.parseArray(competiorsPriceObj.toString(),CompetitorsPriceDTO.class);
        //log.info("---------------"+competitorsPriceList);
    }


    @Test
    public void testbug(){
        long userId = 17764594100L;
        List<ContractCategoryDO> queryList = contractCategoryMapper.getAllContractCategory();
        List<UserPositionDO> positionlist = userPositionMapper.selectByUserId(userId, PositionStatusEnum.DELIVERED.getCode());
        for (ContractCategoryDO contractCategoryDO : queryList){
            List<UserPositionDO> userPositionDOlist = new ArrayList<>();
            userPositionDOlist = positionlist.stream().filter(userPosition-> userPosition.getContractId().equals(contractCategoryDO.getId()))
                    .limit(1).collect(Collectors.toList());
            log.info("-------------"+userPositionDOlist.size());
        }

    }

    @Test
    public void updateByFilledAmount(){
        /*int ret = contractOrderMapper.updateByFilledAmount(1L,9,2L);
        log.info("--------"+ret);*/
    }

    @Test
    public void getContractSize(){
        long contractId = 1000L;
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.getContractCategoryById(contractId);
    }

}
