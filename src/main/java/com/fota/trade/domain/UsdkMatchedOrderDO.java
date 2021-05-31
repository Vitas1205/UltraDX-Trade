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

    private Long orderId;

    private Long userId;

    private Long matchUserId;

    private BigDecimal orderPrice;

    private Integer orderDirection;
    private Integer closeType;

    private Long matchId;

    private Integer matchType;

    private BigDecimal filledPrice;

    private BigDecimal filledAmount;

    private String assetName;

    private Integer assetId;

    private Long brokerId;

    private BigDecimal fee;

}
