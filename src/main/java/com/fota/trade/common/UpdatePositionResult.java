package com.fota.trade.common;

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
     * 旧数量，负表示空仓
     */
    private BigDecimal oldAmount;
    /**
     * 新数量，负表示空仓
     */
    private BigDecimal newAmount;

    /**
     * 平仓盈亏
     */
    private BigDecimal closePL;

}
