package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class ContractCategoryDTO implements Serializable {
    private static final Long serialVersionUID = 3692011613792443928L;
    public Long id;
    public String contractName;
    public Integer assetId;
    public String assetName;
    public Integer status;
    public Long totalAmount;
    public Long unfilledAmount;
    public Long deliveryDate;
    public Integer contractType;
    public Date gmtCreate;
    public Date gmtModified;
    public BigDecimal contractSize;
}