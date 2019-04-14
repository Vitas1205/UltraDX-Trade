package com.fota.trade.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

import static com.fota.trade.domain.enums.OrderStatusEnum.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsdkOrderDO {
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private Long userId;
    private Integer assetId;
    private String assetName;
    private Integer orderDirection;
    private Integer orderType;
    private BigDecimal totalAmount;
    private BigDecimal unfilledAmount;
    private BigDecimal price;
    private BigDecimal fee;
    private Integer status;
    private BigDecimal averagePrice;
    private String orderContext;
    private Long brokerId;

    public boolean fillAmount(BigDecimal filledAmount) {
        if (unfilledAmount.compareTo(filledAmount) < 0) {
            return false;
        }
        unfilledAmount = unfilledAmount.subtract(filledAmount);
        calStatus();
        return true;
    }
    private void calStatus(){
        //如果全成，更新为全成
        if (BigDecimal.ZERO.equals(unfilledAmount)) {
            status = MATCH.getCode();
            return;
        }
        //部成
        if (status == COMMIT.getCode() || status == PART_MATCH.getCode()) {
            status = PART_MATCH.getCode();
        }else {
            status = PART_CANCEL.getCode();
        }
    }
}