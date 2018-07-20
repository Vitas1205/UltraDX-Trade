package com.fota.client.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@Data
public class CapitalAssetTransferDTO {

    private Integer assetId;
    private Long bidUserId;
    private Long askUserId;
    private BigDecimal addBidLockedAmount;
    private BigDecimal addTotalAmount;


}
