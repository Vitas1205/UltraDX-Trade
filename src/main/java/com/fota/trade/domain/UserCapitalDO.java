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
public class UserCapitalDO implements Serializable {
    private static final long serialVersionUID = -1986655986701025551L;
    private Long id;
    private Long userId;
    private Integer assetId;
    private String assetName;
    private BigDecimal amount;
    private BigDecimal withdrawLockedAmount;
    private BigDecimal orderLockedAmount;
    private Date gmtCreate;
    private Date gmtModified;
    private Integer status;
    private Integer version;
}
