package com.fota.trade.domain.query;

import com.fota.trade.common.PageQuery;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Data
public class UserPositionQuery extends PageQuery implements Serializable{
    private static final long serialVersionUID = 8046590860424387795L;
    private Long contractId;
    private Long userId;
    private Integer status;
}
