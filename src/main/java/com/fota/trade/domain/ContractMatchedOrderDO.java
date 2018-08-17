package com.fota.trade.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/31 19:49
 * @Modified:
 */
@Data
public class ContractMatchedOrderDO {
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private Long askOrderId;

    private BigDecimal askOrderPrice;

    private Long askUserId;

    private Byte askCloseType;

    private Long bidOrderId;

    private BigDecimal bidOrderPrice;

    private Long bidUserId;

    private Byte bidCloseType;

    private Byte matchType;

    private BigDecimal fee;

    private BigDecimal filledPrice;

    private BigDecimal filledAmount;

    private String contractName;

    private Integer contractId;

    private Integer status;
}
