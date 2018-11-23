package com.fota.trade.domain;

import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.util.Map;

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

    /**
     * 挂单价值
     */
    private BigDecimal entrustValue;


    private Pair<BigDecimal, Map<String, Object>> pair;

}
