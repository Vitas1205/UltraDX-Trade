package com.fota.trade.manager;

import com.fota.data.domain.DeliveryIndexDTO;
import com.fota.data.service.DeliveryIndexService;
import com.fota.trade.client.AssetExtraProperties;
import com.fota.trade.domain.enums.AssetTypeEnum;
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
import java.util.List;

/**
 * Created by lds on 2018/10/20.
 * Code is the law
 */
@Component
@Slf4j
public class DeliveryPriceManager {
    @Autowired
    private DeliveryIndexService deliveryIndexService;

    public BigDecimal getDeliveryPrice(long assetId){
        List<DeliveryIndexDTO> deliveryIndexDTOS = getAllDeliveryIndexes();
        if (CollectionUtils.isEmpty(deliveryIndexDTOS)) {
            log.error("empty deliveryIndexDTOS");
            return null;
        }
        Integer symbol = AssetExtraProperties.getSymbolByAssetId(assetId);
        if (null == symbol) {
            log.error("no such asset");
            return null;
        }
        DeliveryIndexDTO deliveryIndexDTO = deliveryIndexDTOS.stream()
                .filter(x -> symbol.equals(x.getSymbol()))
                .findFirst().orElse(null);
        if (null == deliveryIndexDTO || null == deliveryIndexDTO.getSymbol()) {
            log.error("null delivery index, assetId={}, symbol={}", assetId, symbol);
            return null;
        }

        return deliveryIndexDTO.getIndex();
    }


    @Cacheable("allDeliveryIndexes")
    public List<DeliveryIndexDTO> getAllDeliveryIndexes(){
        Profiler profiler = ThreadContextUtil.getPrifiler();
        List<DeliveryIndexDTO> ret = deliveryIndexService.getDeliveryIndex();
        if (null != profiler) {
            profiler.complelete("getDeliveryIndex");
        }
        return ret;
    }
    @CacheEvict("allDeliveryIndexes")
    @Scheduled(fixedRate = 500)
    public void deleteDeliveryIndexes() {

    }

}
