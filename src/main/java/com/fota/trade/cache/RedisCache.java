package com.fota.trade.cache;

import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/7 17:41
 * @Modified:
 */
@Component
public class RedisCache {


    /*@Autowired
    public RedisTemplate<String,Object> redis;*/


    /*public void set(String key, String value) {
        redis.boundValueOps(key).set(value, 10, TimeUnit.MINUTES);
    }
    public void set(String value) {
        redis.boundValueOps( redis.opsForValue().increment("0",1L).toString()).set(value, 10, TimeUnit.MINUTES);
    }

    public String get(String key) {
        Object object = redis.boundValueOps(key).get();
        if (object != null) {
            return object.toString();
        }
        return null;
    }*/

}
