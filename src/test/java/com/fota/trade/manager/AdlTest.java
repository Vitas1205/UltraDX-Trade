//package com.fota.trade.manager;
//
//import com.fota.trade.ADLConsumer;
//import com.fota.trade.domain.ADLMatchedDTO;
//import com.fota.trade.domain.ContractADLMatchDTO;
//import com.fota.trade.domain.ContractOrderDO;
//import com.fota.trade.mapper.ContractOrderMapper;
//import com.fota.trade.util.BasicUtils;
//import com.fota.trade.util.ConvertUtils;
//import com.fota.trade.util.MockUtils;
//import org.apache.rocketmq.common.message.MessageExt;
//import org.junit.Before;
//import org.junit.Test;
//import org.springframework.beans.BeanUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//
//import java.util.Arrays;
//import java.util.Date;
//import java.util.List;
//
///**
// * Created by lds on 2018/10/29.
// * Code is the law
// */
//public class AdlTest {
//
//    @Autowired
//    ADLConsumer adlConsumer;
//
//    @Autowired
//    private ContractOrderMapper contractOrderMapper;
//
//    @Before
//    public void init(){
//        ContractOrderDO mockOrderDO = MockUtils.mockEnformContractOrder();
//        contractOrderMapper.insert(mockOrderDO);
//        ContractADLMatchDTO contractADLMatchDTO = new ContractADLMatchDTO();
//        BeanUtils.copyProperties(mockOrderDO, contractADLMatchDTO);
//
//        contractADLMatchDTO.setId(BasicUtils.generateId());
//        contractADLMatchDTO.setContractId(mockOrderDO.getContractId());
//        contractADLMatchDTO.setAmount(mockOrderDO.getTotalAmount());
//        contractADLMatchDTO.setUnfilled(mockOrderDO.getUnfilledAmount());
//        contractADLMatchDTO.setOrderId(mockOrderDO.getId());
//        contractADLMatchDTO.setTime(new Date());
//        contractADLMatchDTO.setDirection(mockOrderDO.getOrderDirection());
//
//        ContractOrderDO matchedOrder = MockUtils.mockContractOrder();
//        matchedOrder.setOrderDirection(ConvertUtils.opDirection(contractADLMatchDTO.getDirection()));
//
//        ADLMatchedDTO matched1 = MockUtils.mockAdlMatchedDTO(matchedOrder);
//
//        contractADLMatchDTO.setMatchedList(Arrays.asList(matched1));
//
//    }
//
//
////    @Test
////    public void testConsumer(){
////        MessageExt messageExt = new MessageExt();
////        int aff = contractOrderMapper.insert(mockOrderDO);
////        assert 1 == aff;
////
////
////
////        messageExt.setBody();
////        List<MessageExt> messageExtList = Arrays.asList()
////        adlConsumer.messageListenerOrderly.consumeMessage()
////    }
////
////    public void test
//}
