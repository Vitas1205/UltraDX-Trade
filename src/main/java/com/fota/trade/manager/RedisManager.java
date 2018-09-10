package com.fota.trade.manager;

import com.alibaba.fastjson.JSONObject;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.ContractOrderDTO;
import com.fota.trade.domain.UsdkOrderDTO;
import com.fota.trade.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/9 23:29
 * @Modified:
 */


@Slf4j
@Component
public class RedisManager {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    Random random = new Random();

    public boolean set(final String key, final Object value) {
        try {
            ValueOperations<String, Object> vOps = redisTemplate.opsForValue();
            vOps.set(key, value);
            return true;
        } catch (Exception e) {
            log.error("redis set error", e);
            return false;
        }
    }


    public Object get(final String key) {
        try {
            ValueOperations<String, Object> vOps = redisTemplate.opsForValue();
            return vOps.get(key);
        } catch (Exception e) {
            log.error("redis get error", e);
            return null;
        }
    }

    public void usdkOrderSave(UsdkOrderDTO usdkOrderDTO){
        Long count = getCount("test_usdk_pre_add");
        String key = Constant.USDK_ORDER_HEAD + count;
        String usdkOrderDTOStr = JSONObject.toJSONString(usdkOrderDTO);
        log.info("------------redisKey:"+key);
        log.info("------------redisValue:"+usdkOrderDTOStr);
        set(key,usdkOrderDTOStr);
        Long count1 = getCount(Constant.USDK_REDIS_KEY);

    }

    /**
     * 把数据放到Redis
     * @param usdkOrderDTO
     */
    public void usdtOrderSaveForMatch(UsdkOrderDTO usdkOrderDTO) {
        if (null != usdkOrderDTO && usdkOrderDTO.getId() != null){
            usdkOrderDTO.setOrderContext(null);
            hSet(Constant.REDIS_USDT_ORDER_FOR_MATCH_HASH, String.valueOf(usdkOrderDTO.getId()), JsonUtil.objectToJson(usdkOrderDTO));
        }
    }

    public void contractOrderSave(ContractOrderDTO contractOrderDTO){
        Long count = getCount("test_contract_pre_add");
        String key = Constant.CONTRACT_ORDER_HEAD + count;
        String usdkOrderDTOStr = JSONObject.toJSONString(contractOrderDTO);
        log.info("-----key"+key);
        log.info("-----value"+usdkOrderDTOStr);
        set(key,usdkOrderDTOStr);
    }

    /**
     * 把数据放到Redis
     * @param contractOrderDTO
     */
    public void contractOrderSaveForMatch(ContractOrderDTO contractOrderDTO) {
        if (null != contractOrderDTO && contractOrderDTO.getId() != null){
            contractOrderDTO.setOrderContext(null);
            hSet(Constant.REDIS_CONTRACT_ORDER_FOR_MATCH_HASH, String.valueOf(contractOrderDTO.getId()), JsonUtil.objectToJson(contractOrderDTO));
        }
    }


    public Long getCount(final String redisKey) {
        try {
            long count = redisTemplate.opsForValue().increment(redisKey, 1);
            return count;
        } catch (Exception e) {
            log.error("redis getCount", e);
        }
        return null;
    }

    public Long counter(final String redisKey, Long sum) {
        try {
            long count = redisTemplate.opsForValue().increment(redisKey, sum);
            return count;
        } catch (Exception e) {
            log.error("redis getCount", e);
        }
        return null;
    }

    public boolean expire(final String key, long expire) {
        return redisTemplate.expire(key, expire, TimeUnit.SECONDS);
    }

    public long lpush(final String key, Object obj) {
        try {
            ListOperations<String, Object> listOps = redisTemplate.opsForList();
            long result = listOps.leftPush(key, obj);


            return result;
        } catch (Exception e) {
            log.error("redis lpush error", e);
            return -1L;
        }
    }

    public long rpush(final String key, Object obj) {
        try {
            ListOperations<String, Object> listOps = redisTemplate.opsForList();
            long result = listOps.rightPush(key, obj);
            return result;
        } catch (Exception e) {
            log.error("redis rpush error", e);
            return -1L;
        }
    }

    public long rPushAll(final String key, Collection values) {
        try {
            ListOperations<String, Object> listOps = redisTemplate.opsForList();
            return listOps.rightPushAll(key, values);
        } catch (Exception e) {
            log.error("redis rpush all error", e);
            return -1L;
        }
    }

    public long lPushAll(String key, Collection values) {
        try {
            ListOperations<String, Object> listOps = redisTemplate.opsForList();
            return listOps.leftPushAll(key, values);
        } catch (Exception e) {
            log.error("redis lpush all error", e);
            return -1L;
        }
    }

    public Object lpop(final String key) {
        try {
            ListOperations<String, Object> listOps = redisTemplate.opsForList();
            Object result = listOps.leftPop(key);
            return result;
        } catch (Exception e) {
            log.error("redis lpop error", e);
            return null;
        }
    }

    /**
     * 获取列表长度
     *
     * @param key
     * @return
     */
    public Long lLen(String key) {
        try {
            return redisTemplate.opsForList().size(key);
        } catch (Exception e) {
            log.error("redis lLen error", e);
            return 0L;
        }

    }


    public Boolean hSet(String key, String hKey, Object value) {
        try {
            HashOperations<String, String, Object> hOps = redisTemplate.opsForHash();
            hOps.put(key, hKey, value);
            return true;
        } catch (Exception e) {
            log.error("redis hsetUser error", e);
            return false;
        }
    }

    /**
     * 删除hash表中的值
     *
     * @param key  键 不能为null
     * @param item 项 可以使多个 不能为null
     */
    public void hdel(String key, Object... item) {
        try {
            Long r = redisTemplate.opsForHash().delete(key, item);
        }catch (Exception e){
            e.printStackTrace();
            return;
        }
    }

    public Long hRemove(String key, String hKey) {
        try {
            HashOperations<String, String, Object> hOps = redisTemplate.opsForHash();
            return hOps.delete(key, hKey);
        } catch (Exception e) {
            log.error("redis hRemove User error", e);
            return -1L;
        }
    }

    public Long hSize(String key) {
        try {
            HashOperations<String, String, Object> hOps = redisTemplate.opsForHash();
            return hOps.size(key);
        } catch (Exception e) {
            log.error("redis hSize error", e);
            return -1L;
        }
    }

    public Boolean exists(String key, Integer u) {
        try {
            HashOperations<String, String, Object> hOps = redisTemplate.opsForHash();
            return hOps.hasKey(key, String.valueOf(u));
        } catch (Exception e) {
            log.error("redis exists error", e);
            return false;
        }
    }

    public Map<String, Object> hEntries(String hKey) {
        try {
            HashOperations<String, String, Object> hOps = redisTemplate.opsForHash();
            return hOps.entries(hKey);
        } catch (Exception e) {
            log.error("redis exists error", e);
            return null;
        }
    }

    public boolean hPutAll(String hKey, Map<String, Object> map) {
        try {
            HashOperations<String, String, Object> hOps = redisTemplate.opsForHash();
            hOps.putAll(hKey, map);
            return true;
        } catch (Exception e) {
            log.error("redis exists error", e);
            return false;
        }
    }

    /**
     * 获取hashKey对应的所有键值
     *
     * @param key 键
     * @return 对应的多个键值
     */
    public Map<Object, Object> hmget(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            log.error("redis exists error", e);
            return new HashMap<>();
        }
    }

    /**
     * 根据value从一个set中查询,是否存在
     *
     * @param key   键
     * @param value 值
     * @return true 存在 false不存在
     */
    public boolean sHasKey(String key, Object value) {
        try {
            return redisTemplate.opsForSet().isMember(key, value);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将数据放入set缓存
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public long sSet(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public long sRemove(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().remove(key, values);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public Set sMember(String key) {
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     *
     * @param lock 锁的key
     * @param lockExpire 锁超时间隔
     * @param retries 重试次数
     * @param retryDuration 重试间隔
     * @return
     */
    public boolean tryLock(String lock, Duration lockExpire, int retries, Duration retryDuration){
        for (int i=0;i<=retries;i++) {
            if (tryLock(lock, lockExpire)){
                return true;
            }
            try {
                Thread.sleep(retryDuration.toMillis());
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }


    public boolean tryLock(String lock, Duration expiration) {
        boolean a= redisTemplate.opsForValue().setIfAbsent(lock, "LOCK");
        if (!a) {
            return false;
        }
        redisTemplate.expire(lock, expiration.toMillis(), TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean releaseLock(String lock) {
        boolean suc=false;
        for (int i=0;i<3;i++) {
            suc = redisTemplate.delete(lock);
            if (suc) {
                return true;
            }
        }
        if (!suc) {
            log.error("release lock failed, key={}", lock);
        }
        return suc;
    }

    public boolean multiConcurrentLock(List<String> locks, Duration expireTime, Integer maxRetries) {
        List<String> locked = new ArrayList<>();
        for (String lock : locks) {
            boolean suc = concurrentLock(lock, expireTime,  maxRetries);
            if (!suc) {
                multiUnLock(locked);
                return false;
            }
            locked.add(lock);
        }
        return true;
    }

    public boolean multiUnLock(List<String> locked) {
        if (CollectionUtils.isEmpty(locked)) {
            return true;
        }
        int i=locked.size()-1;
        boolean suc = true;
        while (i>=0) {
            suc = suc && releaseLock(locked.get(i));
            i--;
        }
        return suc;
    }

    public boolean concurrentLock(String lock, Duration expireTime, Integer maxRetries) {
        long st = System.currentTimeMillis();
        boolean suc = false;
        int i=0;
        for (;i < maxRetries;i++)
        {
            suc = tryLock(lock, expireTime);
            if (suc) {
                break;
            }
            randomSleep();
        }
        if (!suc) {
            log.error("lock failed, key="+lock);
            return false;
        }
        log.info("lock profile: key={}, retries={}, cost={}",lock, i, System.currentTimeMillis() - st);
        return true;
    }
    private void randomSleep(){
        try {
            int mills = random.nextInt(10) + 5;
            Thread.sleep(mills);
        } catch (InterruptedException e) {
            new RuntimeException(e);
        }
    }

    public Long inc(String key, long value) {
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        return ops.increment(key, value);
    }

    public Long dec(String key, long value) {
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        return ops.increment(key, value);
    }

}

