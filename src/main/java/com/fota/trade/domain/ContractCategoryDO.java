package com.fota.trade.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@AllArgsConstructor @NoArgsConstructor
public class ContractCategoryDO {
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private String contractName;
    private Integer assetId;
    private String assetName;
    private Long totalAmount;
    private Long unfilledAmount;
    private Date deliveryDate;
    private Integer status;
    private Integer contractType;
    private BigDecimal price;
    private BigDecimal contractSize;
}