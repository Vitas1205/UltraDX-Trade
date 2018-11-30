package com.fota.trade.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DeleverageDTO {
    private BigDecimal unfilledAmount;
    private Long matchId;

    private Long contractId;
    /**
     * 减仓方向
     */
    private Integer needPositionDirection;
    /**
     * 减仓价格
     */
    private BigDecimal adlPrice;

    public String key(){
        return matchId + "_"+ unfilledAmount;
    }

    public String queue(){
        return contractId + "_" + needPositionDirection;
    }
}
