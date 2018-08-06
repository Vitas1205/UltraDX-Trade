package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class ResultCode implements Serializable {

  private static final long serialVersionUID = -995351386097733811L;
  public Integer code;
  public String message;

  public static ResultCode success() {
    ResultCode resultCode = new ResultCode();
    resultCode.code = 0;
    return resultCode;
  }

  public static ResultCode error(int code, String message) {
    ResultCode resultCode = new ResultCode();
    resultCode.code = code;
    resultCode.message = message;
    return resultCode;
  }

  public boolean isSuccess() {
    return this.code == 0;
  }

}