package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author Gavin Shen
 * @Date 2019/1/21
 */
@Data
public class UserContractDO implements Serializable {
    private static final long serialVersionUID = -4313340612483305055L;
    private Long id;
    private Long userId;
    private BigDecimal amount;
    private BigDecimal lockedAmount;
    private Date gmtCreate;
    private Date gmtModified;
    private Integer version;
    private Integer status;
}