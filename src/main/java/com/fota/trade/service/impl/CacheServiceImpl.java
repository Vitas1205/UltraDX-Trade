package com.fota.trade.service.impl;

import com.fota.common.Result;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * @author Yuanming Tao
 * Created on 2019/5/21
 * Description
 */
@Slf4j
public class CacheServiceImpl implements CacheService {

    @Autowired
    private RedisManager redisManager;

    @Override
    public Result<Object> updateRedisData(String key, Object value) {
        Result<Object> result = new Result<>();
        boolean success = redisManager.set(key, value);
        if (!success) {
            result.error(ResultCodeEnum.SYSTEM_ERROR.getCode(), ResultCodeEnum.SYSTEM_ERROR.getMessage());
        }
        return result;
    }

    @Override
    public Result<Object> getRedisData(String key) {
        Result<Object> result = new Result<>();
        result.success(redisManager.get(key));
        return result;
    }
}
