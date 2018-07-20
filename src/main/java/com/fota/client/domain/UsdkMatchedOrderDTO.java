package com.fota.client.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@Data
public class UsdkMatchedOrderDTO {
    private Long id;
    private Long askOrderId;
    private BigDecimal askOrderPrice;
    private Long bidOrderId;
    private BigDecimal bidOrderPrice;
    private BigDecimal filledPrice;
    private BigDecimal filledAmount;
    private Integer assetId;
    private String assetName;
    private Date gmtCreate;
    private UsdkOrderDTO askUsdkOrder;
    private UsdkOrderDTO bidUsdkOrder;
}