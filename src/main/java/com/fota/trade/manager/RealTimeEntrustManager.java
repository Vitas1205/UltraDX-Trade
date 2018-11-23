package com.fota.trade.manager;

import com.fota.ticker.entrust.EntrustCacheConstants;
import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by lds on 2018/10/20.
 * Code is the law
 */
@Slf4j
@Component
public class RealTimeEntrustManager {
    @Autowired
    private RealTimeEntrust realTimeEntrust;
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 获取订单簿
     * @return
     */

    @Cacheable(value = "competitorsPriceOrder", sync = true)
    public List<CompetitorsPriceDTO> getContractCompetitorsPriceOrder() {
        return realTimeEntrust.getContractCompetitorsPriceOrder();
    }

    @CacheEvict("competitorsPriceOrder")
    @Scheduled(fixedRate = 500)
    public void deleteContractCompetitorsPriceOrder() {
    }

    @Cacheable(value = "usdtPriceOrder", sync = true)
    public List<CompetitorsPriceDTO> getUsdtCompetitorsPriceOrder() {
        return realTimeEntrust.getUsdtCompetitorsPriceOrder();
    }

    @CacheEvict("usdtPriceOrder")
    @Scheduled(fixedRate = 500)
    public void deleteUsdtCompetitorsPriceOrder() {

    }

    @Cacheable(value = "usdtLatestPrice", sync = true)
    public BigDecimal getUsdtLatestPrice (Integer id) {
        Object object = redisTemplate.opsForValue().get(EntrustCacheConstants.FOTA_LATEST_USDT_MATCHED_ORDER + id);
        if (object == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(object.toString());
    }
    @CacheEvict("usdtLatestPrice")
    @Scheduled(fixedRate = 500)
    public void deleteUsdtLatestPrice() {

    }
}
