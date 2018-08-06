package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
@Data
public class OrderMessage implements Serializable {

  private static final long serialVersionUID = -4615544828197773722L;
  public int subjectId;
  public String subjectName;
  public long userId;
  public int event;
  public long orderId;
}