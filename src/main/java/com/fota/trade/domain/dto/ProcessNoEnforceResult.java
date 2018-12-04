package com.fota.trade.domain.dto;

import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.msg.ContractDealedMessage;
import lombok.Data;

import java.util.List;
@Data
public class ProcessNoEnforceResult {
    List<ContractMatchedOrderDO> contractMatchedOrderDOS;
}
