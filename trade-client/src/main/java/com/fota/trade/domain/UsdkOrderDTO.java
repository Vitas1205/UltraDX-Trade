package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class UsdkOrderDTO implements Serializable {

  private static final long serialVersionUID = 1751138088083679930L;
  public Long id;
  public Long gmtCreate;
  public Long gmtModified;
  public Long userId;
  public Integer assetId;
  public String assetName;
  public Integer orderDirection;
  public Integer orderType;
  public BigDecimal totalAmount;
  public BigDecimal unfilledAmount;
  public BigDecimal price;
  public BigDecimal fee;
  public Integer status;
  public String matchAmount;
  public BigDecimal completeAmount;
  public BigDecimal averagePrice;
}