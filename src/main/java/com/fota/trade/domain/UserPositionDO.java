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
    private BigDecimal unfilledAmount;
    private Integer positionType;
    private BigDecimal averagePrice;
    private Integer status;
}