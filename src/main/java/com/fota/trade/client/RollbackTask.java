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

    private int pageIndex;
    private int pageSize;
    private long contractId;
    private Date rollbackPoint;
    private Date taskStartPoint;

    public static final String getContractRollbackLock(long contractId) {
        return "ROLLBACK_LOCK_" + contractId;
    }

    public int getStartRow() {
        return (pageIndex - 1) * pageSize;
    }
}
