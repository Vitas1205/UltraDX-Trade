package com.fota.trade.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPositionDO {
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private Long userId;
    private Long contractId;
    private String contractName;
    private BigDecimal lockedAmount;
    private Long unfilledAmount;
    private Integer positionType;
    private BigDecimal averagePrice;
    /**
     * 1 未交割，2 已交割
     */
    private Integer status;
    private Integer lever;
    private BigDecimal contractSize;
}