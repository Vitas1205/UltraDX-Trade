package com.fota.trade.common;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Created by Swifree on 2018/8/22.
 * Code is the law
 */
@Data
@Accessors(chain = true)
public class UpdatePositionResult {
    private long originAmount;
    private long curAmount;
}
