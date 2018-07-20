package com.fota.client.domain;

import com.fasterxml.jackson.databind.util.BeanUtil;
import com.fota.trade.domain.ContractCategoryDO;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Data
public class ContractCategoryDTO implements Serializable {
    private static final long serialVersionUID = 5698808456294079091L;
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private String contractName;
    private Integer assetId;
    private String assetName;
    private Long totalAmount;
    private Long unfilledAmount;
    private Date deliveryDate;
    private Integer status;
    private Integer contractType;
    private BigDecimal price;
}
