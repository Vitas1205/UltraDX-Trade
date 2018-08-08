package com.fota.trade.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/23 16:07
 * @Modified:
 */
@Data
public class CompetitorsPriceDTO {

    private Integer type;
    private Integer id;
    private Integer orderDirection;
    private BigDecimal price;
}
