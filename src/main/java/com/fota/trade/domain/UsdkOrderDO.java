package com.fota.trade.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsdkOrderDO {
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private Long userId;
    private Integer assetId;
    private String assetName;
    private Integer orderDirection;
    private Integer orderType;
    private BigDecimal totalAmount;
    private BigDecimal unfilledAmount;
    private BigDecimal price;
    private BigDecimal fee;
    private Integer status;
}