package com.fota.trade.mapper;

import com.fota.trade.common.BeanUtils;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.ContractMatchedOrderDTO;
import com.fota.trade.service.ContractOrderService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;

import static com.fota.trade.common.TestConfig.*;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;

/**
 * @Author: Carl Zhang
 * @Descripyion:
 * @Date: Create in 下午 22:06 2018/8/23 0023
 * @Modified:
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@Transactional
public class ContractMatchedRecordTest {
    @Resource
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Resource
    private ContractOrderService contractOrderService;

    @Before
    public void init(){
        ContractMatchedOrderDTO contractMatchedOrderDTO = new ContractMatchedOrderDTO();
        contractMatchedOrderDTO.setId(1L);
        contractMatchedOrderDTO.setAskUserId(userId);
        contractMatchedOrderDTO.setAskOrderId(2L);
        contractMatchedOrderDTO.setAskOrderPrice("1.1");

        contractMatchedOrderDTO.setBidUserId(0L);
        contractMatchedOrderDTO.setBidOrderId(8L);
        contractMatchedOrderDTO.setBidOrderPrice("1.2");

        contractMatchedOrderDTO.setFilledAmount(new BigDecimal(1));
        contractMatchedOrderDTO.setFilledPrice("1.2");
        contractMatchedOrderDTO.setContractId(contractId);
        contractMatchedOrderDTO.setContractName("test");
        contractMatchedOrderDTO.setMatchType(matchType);

        ContractMatchedOrderDO ask = BeanUtils.extractContractMatchedRecord(contractMatchedOrderDTO, ASK.getCode(), BigDecimal.ONE, 0);
        ContractMatchedOrderDO bid = BeanUtils.extractContractMatchedRecord(contractMatchedOrderDTO, BID.getCode(), BigDecimal.ONE, 0);

        int aff = contractMatchedOrderMapper.insert(Arrays.asList(ask, bid));
        assert  2 == aff;
    }
    @Test
    public void testQuery(){
//        ContractMatchedOrderTradeDTOPage res = contractOrderService.getContractMacthRecord(userId, Arrays.asList(contractId), 1, 1, null, System.currentTimeMillis());
//        assert  res.getData().size() == 1;
//        log.info("res={}", res);
    }
}
