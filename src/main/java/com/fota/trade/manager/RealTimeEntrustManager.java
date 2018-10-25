package com.fota.trade.manager;

import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Created by lds on 2018/10/20.
 * Code is the law
 */
@Component
public class RealTimeEntrustManager {
    @Autowired
    private RealTimeEntrust realTimeEntrust;
    /**
     * 获取订单簿
     * @return
     */

    @Cacheable("competitorsPriceOrder")
    public List<CompetitorsPriceDTO> getContractCompetitorsPriceOrder() {
        return realTimeEntrust.getContractCompetitorsPriceOrder();
    }

    @CacheEvict("competitorsPriceOrder")
    @Scheduled(fixedRate = 500)
    public void deleteContractCompetitorsPriceOrder() {

    }
}
