package com.fota.trade.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/10/18 11:53
 * @Modified:
 */
@Data
public class EntrustMarginDO {
    /**
     * 使用买一卖一计算的委托冻结
     */
    private BigDecimal entrustMargin;

    /**
     * 使用简单现货指数计算的委托冻结
     */
    private BigDecimal entrustMarginByIndex;
}
