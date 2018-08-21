package com.fota.trade.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/31 21:15
 * @Modified:
 */
@Data
public class UsdkMatchedOrderDO {
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private Long askOrderId;

    private BigDecimal askOrderPrice;

    private Byte askCloseType;

    private Long bidOrderId;

    private BigDecimal bidOrderPrice;

    private Byte bidCloseType;

    private Byte matchType;

    private BigDecimal filledPrice;

    private BigDecimal filledAmount;

    private Long askUserId;

    private Long bidUserId;

    private String assetName;

    private Integer assetId;
}
