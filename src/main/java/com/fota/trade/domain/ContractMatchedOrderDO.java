package com.fota.trade.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/31 19:49
 * @Modified:
 */
@Data
@Accessors(chain = true)
public class ContractMatchedOrderDO {
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private Long orderId;

    private Long userId;

    private BigDecimal orderPrice;

    private Integer orderDirection;

    private Integer closeType;

    private Long matchId;

    private Long matchUserId;

    private Integer matchType;

    private BigDecimal filledPrice;

    private BigDecimal filledAmount;

    private BigDecimal fee;

    private String contractName;

    private Long contractId;

    private Integer status;

    private BigDecimal platformProfit;
}
