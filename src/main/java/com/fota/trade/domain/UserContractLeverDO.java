package com.fota.trade.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author Gavin Shen
 * @Date 2018/7/12
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserContractLeverDO {
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private Long userId;
    private Integer assetId;
    private String assetName;
    private Integer lever;
}
