package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ContractOrderDTO implements Serializable {

    private static final Long serialVersionUID = 2982208351452049900L;
    public Long id;
    public Long gmtCreate;
    public Long gmtModified;
    public Long userId;
    public Integer contractId;
    public String contractName;
    public Integer orderDirection;
    public Integer orderType;
    public Long totalAmount;
    public Long unfilledAmount;
    public String price;
    public String fee;
    public Integer status;
    public Long matchAmount;
    public Long completeAmount;
    public Integer closeType;
    public Integer operateType;
    public Integer operateDirection;
    public BigDecimal averagePrice;
}