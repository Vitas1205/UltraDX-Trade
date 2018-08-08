package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class ContractMatchedOrderDTO implements Serializable {

  private static final long serialVersionUID = -3572118564551524451L;
  public Long id;
  public Long askOrderId;
  public Integer askOrderStatus;
  public String askOrderPrice;
  public Long bidOrderId;
  public Integer bidOrderStatus;
  public String bidOrderPrice;
  public String filledPrice;
  public Long filledAmount;
  public Long contractId;
  public String contractName;
  public Long gmtCreate;
  public Integer matchType;
  public String assetName;
  public Integer contractType;
}