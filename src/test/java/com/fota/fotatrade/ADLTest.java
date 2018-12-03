package com.fota.fotatrade;

import com.alibaba.fastjson.JSON;
import com.fota.risk.client.manager.RelativeRiskLevelManager;
import com.fota.trade.ADLConsumer;
import com.fota.trade.domain.ContractADLMatchDTO;
import com.fota.trade.manager.CurrentPriceService;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.test.BaseTest;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class ADLTest extends BaseTest {
    @Autowired
    private ADLConsumer adlManager;

    @Autowired
    ContractOrderMapper contractOrderMapper;

    @Autowired
    DefaultMQProducer defaultMQProducer;

    @Autowired
    CurrentPriceService currentPriceService;


    @Autowired
    RelativeRiskLevelManager riskLevelManager;

    ContractADLMatchDTO contractADLMatchDTO;
    Map<ContractADLMatchDTO, MessageExt> messageExtMap=new HashMap<>();

//    @Before
    public void init(){
        String str="{\"amount\":0.0047100000000000,\"contractId\":1069,\"contractName\":\"ETH1812\",\"direction\":2,\"id\":778540589869040641,\"matchedList\":[{\"direction\":1,\"fee\":0E-16,\"filledPrice\":212.5000000000000000,\"id\":495716989791457,\"matchedAmount\":0.0047100000000000,\"orderType\":1,\"price\":212.5000000000000000,\"unfilledAmount\":22.4420400000000000,\"userId\":11}],\"orderId\":675856890627061,\"price\":233.538000000000,\"time\":1541746807599,\"unfilledAmount\":1,\"userId\":330}";

        contractADLMatchDTO = JSON.parseObject(str, ContractADLMatchDTO.class);
        messageExtMap.put(contractADLMatchDTO, new MessageExt());
        mockCurrentPrice(new BigDecimal("200.1"));
        mockLock(1);
        when(riskLevelManager.range(anyLong(), anyInt(), anyInt(), anyInt())).thenReturn( null);
    }
    @Test
    public void testNoCurrentPrice(){
//        mockCurrentPrice(null);
//        adlManager.adl(messageExtMap, contractADLMatchDTO);
//        verify(defaultMQProducer, calls(1));
    }
//    @Test
//    public void testLockFailed(){
//        mockLock(0);
//        adlManager.adl(messageExtMap, contractADLMatchDTO);
//    }
//    @Test
//    public void testNoEnoughRRL(){
//       adlManager.adl(messageExtMap, contractADLMatchDTO);
//       verify(defaultMQProducer, calls(1));
//
//    }
//    @Test
//    public void testMockInsertFailed(){
//        adlManager.adl(messageExtMap, contractADLMatchDTO);
//        verify(contractOrderMapper.updateAAS(any(), any(), any(), any()), calls(1));
//        verify(defaultMQProducer, calls(1));
//    }

    private void mockCurrentPrice(BigDecimal price){
        when(currentPriceService.getSpotIndexByContractName(anyString())).thenReturn(price);
    }
    private void mockUpdateAAS(){
        when(contractOrderMapper.updateAAS(any(), any(), any(), anyInt())).thenReturn(1);
    }
    private void mockLock(int ret){
        when(contractOrderMapper.updateAmountAndStatus(any(), any(), any(), any(), any()))
                .thenReturn(ret);
    }

}
