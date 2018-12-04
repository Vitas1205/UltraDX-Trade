package com.fota.trade.manager;

import com.fota.data.domain.DeliveryIndexDTO;
import com.fota.data.domain.TickerDTO;
import com.fota.data.manager.IndexCacheManager;
import com.fota.data.service.SpotIndexService;
import com.fota.trade.client.AssetExtraProperties;
import com.fota.trade.util.Profiler;
import com.fota.trade.util.ThreadContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by lds on 2018/10/20.
 * Code is the law
 */
@Component
@Slf4j
public class CurrentPriceManager {
    @Autowired
    private IndexCacheManager indexCacheManager;

    @Cacheable(value = "spotIndexes", sync = true)
    public List<TickerDTO> getSpotIndexes(){
        Profiler profiler = ThreadContextUtil.getPrifiler();
        try {
            List<TickerDTO> ret = indexCacheManager.listCurrentSpotIndex();
            if (null != profiler) {
                profiler.complelete("getDeliveryIndex");
            }
            return ret;
        }catch (Throwable t) {
            log.error("getDeliveryIndex exception, ", t);
            return new LinkedList<>();
        }

    }




    @CacheEvict("spotIndexes")
    @Scheduled(fixedRate = 500)
    public void deleteSpotIndexes() {

    }








}
