package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderMessage implements Serializable {
  /**
   * event
   * 下单-1
   * 撤单-2
   * 成交-3
   */
  private static final long serialVersionUID = -4615544828197773722L;
  public Long subjectId;
  public String subjectName;
  public Long userId;
  public Integer event;
  public Long orderId;
  public Long askUserId;
  public Long bidUserId;
  public Long askOrderId;
  public Long bidOrderId;
  public BigDecimal amount;
  public BigDecimal price;
  public Long transferTime;
  public String ip;
  public String username;
}