package com.fota.trade.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.Map;
import java.util.Optional;

import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;

/**
 * Created by Swifree on 2018/9/17.
 * Code is the law
 */
public class ConvertUtils {
    public static final JSONObject resolveCancelResult(String cancelResult) {
        Map<Long, JSONObject> resultMap = JSON.parseObject(cancelResult, Map.class);
        JSONObject res = null;
        Optional<JSONObject> optional = resultMap.entrySet().stream().map(x -> {
            JSONObject val = x.getValue();
            val.put("id", x.getKey());
            return val;
        }).findFirst();
        if (optional.isPresent()) {
            res = optional.get();
        }
        return res;
    }

    /**
     * 获取相反方向
     * @param direction
     * @return
     */
    public static final int opDirection(int direction) {
        return ASK.getCode() + BID.getCode() - direction;
    }
}
