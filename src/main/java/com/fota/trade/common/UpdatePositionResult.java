package com.fota.trade.common;

import com.fota.trade.domain.enums.PositionTypeEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Created by Swifree on 2018/8/22.
 * Code is the law
 */
@Data
@Accessors(chain = true)
public class UpdatePositionResult {
    /**
     * 平仓数量,如果不是平仓，则为null
     */
    private Long closeAmount;
    /**
     * 开仓方向
     */
    private int openPositionDirection;
    /**
     * 开仓均价
     */
    private BigDecimal openAveragePrice;

    /**
     * 新持仓方向
     */
    private int newPositionType;
    /**
     * 新持仓数量
     */
    private long newTotalAmount;
}
