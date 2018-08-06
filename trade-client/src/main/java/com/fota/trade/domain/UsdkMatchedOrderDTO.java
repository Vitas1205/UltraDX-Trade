package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class UsdkMatchedOrderDTO implements Serializable {

  private static final long serialVersionUID = -470905622170521001L;
  public Long id;
  public Long askOrderId;
  public Integer askOrderStatus;
  public String askOrderPrice;
  public Long bidOrderId;
  public Integer bidOrderStatus;
  public String bidOrderPrice;
  public String filledPrice;
  public String filledAmount;
  public Integer assetId;
  public String assetName;
  public Integer matchType;
  public Date gmtCreate;
}