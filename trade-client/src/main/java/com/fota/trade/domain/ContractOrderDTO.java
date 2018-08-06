package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class ContractOrderDTO implements Serializable {

    private static final Long serialVersionUID = 2982208351452049900L;
    public Long id;
    public Date gmtCreate;
    public Date gmtModified;
    public Long userId;
    public Long contractId;
    public String contractName;
    public Integer orderDirection;
    public Integer orderType;
    public Integer operateType;
    public Integer operateDirection;
    public Integer lever;
    public Long totalAmount;
    public Long unfilledAmount;
    public BigDecimal price;
    public BigDecimal fee;
    public Integer status;
    public Long matchAmount;
    public Long completeAmount;
    public Integer closeType;
    public BigDecimal averagePrice;
}