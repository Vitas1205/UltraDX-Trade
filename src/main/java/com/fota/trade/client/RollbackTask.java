package com.fota.trade.client;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Created by Swifree on 2018/8/16.
 * Code is the law
 */
@Data
@Accessors(chain = true)
public class RollbackTask {
    public static final String TASK_LOCK_KEY = "LOCK";
    public static final String TASK_AMOUNT_KEY = "AMOUNT";
    private int pageIndex;
    private int pageSize;
    private long contractId;
    private Date rollbackPoint;
    private Date taskStartPoint;

    public static final String getContractRollbackKey(long contractId) {
        return "ROLLBACK_" + contractId;
    }

    public int getStartRow() {
        return (pageIndex - 1) * pageSize;
    }
}
