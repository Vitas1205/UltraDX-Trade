package com.fota.trade.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author Gavin Shen
 * @Date 2019/1/21
 */
@Data
public class CapitalAccountAddAmountDTO implements Serializable {
    private static final long serialVersionUID = -6238833324389455765L;
    private Long userId;
    private Integer assetId;
    private BigDecimal addWithdrawLocked;
    private BigDecimal addOrderLocked;
    private BigDecimal addTotal;
}

