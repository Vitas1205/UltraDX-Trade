package com.fota.client.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/13 16:42
 * @Modified:
 */
@Data
public class OrderMessage implements Serializable {

    private static final long serialVersionUID = 3668781193482987106L;
    /**
     * @see com.fota.trade.domain.enums.OrderOperateTypeEnum
     */
    private Integer type;
    private Object Message;
}
