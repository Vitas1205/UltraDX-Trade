package com.fota.trade.domain.dto;

import com.fota.trade.domain.ContractMatchedOrderDO;
import lombok.Data;

import java.util.List;
@Data
public class ProcessNoEnforceResult {
    List<ContractMatchedOrderDO> contractMatchedOrderDOS;
}
