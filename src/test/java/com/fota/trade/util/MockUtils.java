package com.fota.trade.util;

import com.fota.trade.client.constants.Constants;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.ADLMatchedDTO;
import com.fota.trade.domain.ContractADLMatchDTO;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Date;

import static com.fota.trade.common.TestConfig.contractId;
import static com.fota.trade.common.TestConfig.userId;
import static com.fota.trade.domain.enums.OrderTypeEnum.ENFORCE;
import static com.fota.trade.domain.enums.OrderTypeEnum.LIMIT;

/**
 * Created by lds on 2018/10/26.
 * Code is the law
 */
public class MockUtils {
    public static ContractOrderDO mockContractOrder(){
        ContractOrderDO contractOrderDO;
        // 准备数据
        contractOrderDO = new ContractOrderDO();
        contractOrderDO.setId(BasicUtils.generateId());
        contractOrderDO.setCloseType(1);
        contractOrderDO.setContractId(contractId);
        contractOrderDO.setContractName("BTC0930");
        contractOrderDO.setFee(Constant.FEE_RATE);
        contractOrderDO.setLever(Constants.DEFAULT_LEVER);
        contractOrderDO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        contractOrderDO.setPrice(new BigDecimal("6000.1"));
        contractOrderDO.setTotalAmount(BigDecimal.valueOf(100L));
        contractOrderDO.setUnfilledAmount(BigDecimal.valueOf(100L));
        contractOrderDO.setUserId(userId);
        contractOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        contractOrderDO.setGmtCreate(new Date());
        contractOrderDO.setOrderType(LIMIT.getCode());
        return contractOrderDO;
    }

    public static ContractOrderDO mockEnformContractOrder(){
        ContractOrderDO contractOrderDO = mockContractOrder();
        contractOrderDO.setOrderType(ENFORCE.getCode());
        return contractOrderDO;
    }


    public static ADLMatchedDTO mockAdlMatchedDTO(ContractOrderDO matchedOrder) {
        ADLMatchedDTO adlMatchedDTO = new ADLMatchedDTO();
        BeanUtils.copyProperties(matchedOrder, adlMatchedDTO);

        adlMatchedDTO.setId(BasicUtils.generateId());
        adlMatchedDTO.setDirection(matchedOrder.getOrderDirection());
        adlMatchedDTO.setMatchedAmount(matchedOrder.getTotalAmount());
        adlMatchedDTO.setMatchedAmount(matchedOrder.getTotalAmount().divide(new BigDecimal(2), RoundingMode.DOWN));
        adlMatchedDTO.setUnfilledAmount(matchedOrder.getTotalAmount().subtract(adlMatchedDTO.getMatchedAmount()));
        return adlMatchedDTO;
    }
}
