package com.fota.client.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
@Data
public class UsdkOrderDTO implements Serializable {
    private static final long serialVersionUID = 2131171683542840926L;
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
    private BigDecimal matchAmount;
    private BigDecimal completeAmount;
}
