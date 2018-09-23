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
     * 旧开仓均价
     */
    private BigDecimal oldOpenAveragePrice;
    /**
     * 新开仓均价
     */
    private BigDecimal newOpenAveragePrice;

    /**
     * @return 计算平仓数量,如果不是平仓，则为null
     */
    public BigDecimal getCloseAmount() {
        if (oldAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (newAmount.compareTo(BigDecimal.ZERO) == 0){
            return oldAmount.abs();
        }
        //方向相反
        if (oldAmount.multiply(newAmount).compareTo(BigDecimal.ZERO) < 0) {
            return oldAmount;
        }
        //方向相同,如果数量增加，则没有平仓
        if (oldAmount.abs().compareTo(newAmount.abs()) < 0) {
            return null;
        }
        return oldAmount.abs().subtract(newAmount.abs());
    }
    public BigDecimal getOldOpenPositionDirection(){
        if (oldAmount.compareTo(BigDecimal.ZERO) < 0) {
            return new BigDecimal(-1);
        }
        return new BigDecimal(1);
    }
}
