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
public class ContractOrderDTO implements Serializable{
    private static final long serialVersionUID = 8886487787787250306L;
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private Long userId;
    private Integer contractId;
    private String contractName;
    private Integer orderDirection;
    private Integer operateType;
    private Integer operateDirection;
    private Integer lever;
    private Long totalAmount;
    private Long unfilledAmount;
    private Integer closeType;
    private BigDecimal price;
    private BigDecimal fee;
    private BigDecimal usdkLockedAmount;
    private BigDecimal positionLockedAmount;
    private Integer status;
    private BigDecimal completeAmount;

}
