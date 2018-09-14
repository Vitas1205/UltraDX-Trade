package com.fota.trade.domain;

import com.fota.trade.domain.enums.OrderStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

import static com.fota.trade.domain.enums.OrderStatusEnum.*;
import static com.fota.trade.domain.enums.OrderStatusEnum.CANCEL;

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
        if (unfilledAmount.compareTo(filledAmount) < 0) {
            return false;
        }
        unfilledAmount = unfilledAmount.subtract(filledAmount);
        calStatus();
        return true;
    }
    private void calStatus(){
        if (status == COMMIT.getCode() || status == PART_MATCH.getCode() || status == MATCH.getCode()) {
            if (unfilledAmount.compareTo(BigDecimal.ZERO) > 0) {
                status = PART_MATCH.getCode();
            }else status = MATCH.getCode();
        }else {
            if (unfilledAmount.compareTo(BigDecimal.ZERO) > 0) {
                status = PART_CANCEL.getCode();
            } else status = CANCEL.getCode();
        }
    }
}