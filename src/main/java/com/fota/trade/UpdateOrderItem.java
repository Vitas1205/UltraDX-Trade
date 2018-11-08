package com.fota.trade;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by lds on 2018/10/26.
 * Code is the law
 */
@Data
@Accessors(chain = true)
public class UpdateOrderItem {
    long userId;
    long id;
    BigDecimal filledAmount;
    BigDecimal filledPrice;
}
