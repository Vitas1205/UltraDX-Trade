package com.fota.trade.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class ContractOrderDO {
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private Long userId;
    private Integer contractId;
    private String contractName;
    private Integer orderDirection;
    private Integer operateType;
    private Integer operateDirection;
    private Integer lever;
    private BigDecimal totalAmount;
    private BigDecimal unfilledAmount;
    private Integer closeType;
    private BigDecimal price;
    private BigDecimal fee;
    private BigDecimal usdkLockedAmount;
    private BigDecimal positionLockedAmount;
    private Integer status;
}