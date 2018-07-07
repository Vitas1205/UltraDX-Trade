package com.fota.trade.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class UserPositionDO {
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private Long userId;
    private Integer contractId;
    private String contractName;
    private BigDecimal lockedAmount;
    private BigDecimal unfilledAmount;
    private Integer positionType;
    private BigDecimal averagePrice;
    private Integer status;
}