package com.fota.trade.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/9/17 17:13
 * @Modified:
 */
@Data
public class UserFeeRateDO {
    private Integer id;
    private Integer level;
    private BigDecimal feeRate;
}
