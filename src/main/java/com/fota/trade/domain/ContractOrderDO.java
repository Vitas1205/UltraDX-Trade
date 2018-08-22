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
    private Integer orderType;
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
    private BigDecimal averagePrice;

    public boolean fillAmount(long filledAmount) {
        if (filledAmount - filledAmount < 0) {
            return false;
        }
        unfilledAmount -= filledAmount;
        if (unfilledAmount == 0) {
            setStatus(OrderStatusEnum.MATCH.getCode());
        } else {
            setStatus(OrderStatusEnum.PART_MATCH.getCode());
        }
        return true;
    }
}