package com.fota.trade.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author: Harry Wang
 * @Date: 2019/4/9 14:57
 * @Version 1.0
 */
@Data
public class BrokerrFeeRateDO {
    private Long brokerId;
    private BigDecimal feeRate;
}
