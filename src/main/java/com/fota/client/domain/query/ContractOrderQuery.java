package com.fota.client.domain.query;

import com.fota.client.common.PageQuery;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Data
public class ContractOrderQuery extends PageQuery implements Serializable{
    private static final long serialVersionUID = 6238574825631206491L;
    private Long contractId;
    private Long userId;
    private Date startTime;
    private Date endTime;
    private List<Integer> orderStatus;
}
