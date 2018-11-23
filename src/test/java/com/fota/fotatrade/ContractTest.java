package com.fota.fotatrade;

import com.alibaba.fastjson.JSON;
import com.fota.common.Result;
import com.fota.common.enums.FotaApplicationEnum;
import com.fota.trade.client.PlaceContractOrderDTO;
import com.fota.trade.client.PlaceOrderRequest;
import com.fota.trade.client.UserLevelEnum;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.PositionStatusEnum;
import com.fota.trade.manager.ADLManager;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.fota.trade.common.TestConfig.userId;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;
import static com.fota.trade.domain.enums.OrderTypeEnum.ENFORCE;
import static com.fota.trade.domain.enums.OrderTypeEnum.LIMIT;
import static com.fota.trade.domain.enums.OrderTypeEnum.PASSIVE;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/12 10:59
 * @Modified:
 */
@SpringBootTest
@Slf4j
@RunWith(SpringRunner.class)
//@Transactional
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

    @Autowired
    private ADLManager adlManager;

    @Test
    public void placeOrder(){
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        contractOrderDTO.setContractName("BTC0304");
        contractOrderDTO.setContractId(1217L);
        contractOrderDTO.setUserId(userId);
        contractOrderDTO.setOrderDirection(ASK.getCode());
        contractOrderDTO.setOrderType(PASSIVE.getCode());
        contractOrderDTO.setTotalAmount(new BigDecimal("0.02"));
        contractOrderDTO.setPrice(new BigDecimal("6604"));
        Map<String, String> map = new HashMap<>();
        map.put("usernmae", "123");
        map.put("ip", "192.169.1.1");
        ResultCode result = contractOrderService.order(contractOrderDTO,map);
        assert result.isSuccess();
        log.info("result={}", result);
    }

    @Test
    public void batchPlaceOrder(){

        PlaceOrderRequest<PlaceContractOrderDTO> placeOrderRequest = new PlaceOrderRequest();
        placeOrderRequest.setUserId(userId);
        placeOrderRequest.setUserLevel(UserLevelEnum.DEFAULT);
        placeOrderRequest.setCaller(FotaApplicationEnum.TRADE);
        PlaceContractOrderDTO placeContractOrderDTO = new PlaceContractOrderDTO();
        placeContractOrderDTO.setExtOrderId("1");
        placeContractOrderDTO.setSubjectName("BTC0304");
        placeContractOrderDTO.setSubjectId(1217L);
        placeContractOrderDTO.setOrderDirection(BID.getCode());
        placeContractOrderDTO.setOrderType(PASSIVE.getCode());
        placeContractOrderDTO.setTotalAmount(new BigDecimal("0.02"));
        placeContractOrderDTO.setPrice(new BigDecimal("1"));

        PlaceContractOrderDTO placeContractOrderDTO1 = new PlaceContractOrderDTO();
        BeanUtils.copyProperties(placeContractOrderDTO, placeContractOrderDTO1);
        placeContractOrderDTO1.setOrderType(LIMIT.getCode());

        placeOrderRequest.setPlaceOrderDTOS(Arrays.asList(placeContractOrderDTO,placeContractOrderDTO1));

        Result result = contractOrderService.batchOrder(placeOrderRequest);

        log.info("res=", result);
        assert result.isSuccess();


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
        //contractOrderService.cancelAllmatch_adlOrder(userId);
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

    @Test
    public void testAdl(){
        String str="{\"amount\":0.0047100000000000,\"contractId\":1069,\"contractName\":\"ETH1812\",\"direction\":2,\"id\":778540589869040641,\"matchedList\":[{\"direction\":1,\"fee\":0E-16,\"filledPrice\":212.5000000000000000,\"id\":495716989791457,\"matchedAmount\":0.0047100000000000,\"orderType\":1,\"price\":212.5000000000000000,\"unfilledAmount\":22.4420400000000000,\"userId\":11}],\"orderId\":675856890627061,\"price\":233.538000000000,\"time\":1541746807599,\"unfilled\":0E-16,\"userId\":330}";

        //        String str1="{\"amount\":0.00124,\"contractId\":1005,\"contractName\":\"ETH1811\",\"direction\":2,\"id\":778530085816632320,\"matchedList\":[{\"direction\":1,\"fee\":0.0005,\"filledPrice\":208,\"id\":388647496670778,\"matchedAmount\":0.00035,\"orderType\":1,\"price\":208,\"unfilledAmount\":0.00445,\"userId\":402},{\"direction\":1,\"fee\":0.0005,\"filledPrice\":205,\"id\":880205821685829,\"matchedAmount\":0.00089,\"orderType\":1,\"price\":205,\"unfilledAmount\":0.00000,\"userId\":405}],\"orderId\":696202187523784,\"price\":216.38522800000000190285831536129990126937627792358398437500,\"time\":1541666668039,\"unfilled\":0.00000,\"userId\":8}";

        Result result = adlManager.adl(JSON.parseObject(str, ContractADLMatchDTO.class));
//        Result result1 = adlManager.adl(JSON.parseObject(str1, ContractADLMatchDTO.class));
        assert result.isSuccess()
//                && result1.isSuccess()
                ;
    }

}
