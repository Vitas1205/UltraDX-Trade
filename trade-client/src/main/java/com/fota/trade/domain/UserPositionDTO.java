package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class UserPositionDTO implements Serializable {

  private static final long serialVersionUID = 741530840764452228L;
  public Long id;
  public Long gmtCreate;
  public Long gmtModified;
  public Long userId;
  public Long contractId;
  public String contractName;
  public Integer positionType;
  public Long amount;
  public String averagePrice;
  public BigDecimal contractSize;
}