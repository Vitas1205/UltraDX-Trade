package com.fota.client.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Data
public class UserPositionDTO implements Serializable {
    private static final long serialVersionUID = -549037927379729842L;
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
    private BigDecimal totalAmount;
    private BigDecimal unfilledAmount;
    private Integer closeType;
    private BigDecimal price;
    private BigDecimal fee;
    private BigDecimal usdkLockedAmount;
    private BigDecimal positionLockedAmount;
    private Integer status;
}
