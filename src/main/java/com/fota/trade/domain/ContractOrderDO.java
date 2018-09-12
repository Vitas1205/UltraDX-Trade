package com.fota.trade.domain;

import com.fota.trade.domain.enums.OrderStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContractOrderDO {
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private Long userId;
    private Long contractId;
    private String contractName;
    private Integer orderDirection;
    private Integer operateType;
    private Integer orderType;
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
    private BigDecimal averagePrice;
    private String orderContext;

    public boolean fillAmount(BigDecimal filledAmount) {
        unfilledAmount = unfilledAmount.subtract(filledAmount);
        if (BigDecimal.ZERO.equals(unfilledAmount)) {
            setStatus(OrderStatusEnum.MATCH.getCode());
        } else {
            setStatus(OrderStatusEnum.PART_MATCH.getCode());
        }
        return true;
    }
}