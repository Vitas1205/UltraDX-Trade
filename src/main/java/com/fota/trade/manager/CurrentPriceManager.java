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
//    @Autowired
//    private DeliveryIndexService deliveryIndexService;


//    public BigDecimal getDeliveryPrice(long assetId){
//        List<DeliveryIndexDTO> deliveryIndexDTOS = getAllDeliveryIndexes();
//        if (CollectionUtils.isEmpty(deliveryIndexDTOS)) {
//            log.error("empty deliveryIndexDTOS");
//            return null;
//        }
//        Integer symbol = AssetExtraProperties.getSymbolByAssetId(assetId);
//        if (null == symbol) {
//            log.error("no such asset");
//            return null;
//        }
//        DeliveryIndexDTO deliveryIndexDTO = deliveryIndexDTOS.stream()
//                .filter(x -> symbol.equals(x.getSymbol()))
//                .findFirst().orElse(null);
//        if (null == deliveryIndexDTO || null == deliveryIndexDTO.getSymbol()) {
//            log.error("null delivery index, assetId={}, symbol={}", assetId, symbol);
//            return null;
//        }
//
//        return deliveryIndexDTO.getIndex();
//    }
//
//




    @Cacheable("spotIndexes")
    public List<TickerDTO> getSpotIndexes(){
        Profiler profiler = ThreadContextUtil.getPrifiler();
        List<TickerDTO> ret = indexCacheManager.listCurrentSpotIndex();
        if (null != profiler) {
            profiler.complelete("getDeliveryIndex");
        }
        return ret;
    }


    @CacheEvict("spotIndexes")
    @Scheduled(fixedRate = 1000)
    public void deleteSpotIndexes() {

    }

    public BigDecimal getSpotIndexByContractName(String contractName){
        String assetName = AssetExtraProperties.getAssetNameByContractName(contractName);
        if (null == assetName) {
            return null;
        }
        return getSpotIndexByAssetName(assetName);
    }

    public BigDecimal getSpotIndexByAssetId(long assetId){
        List<TickerDTO> tickerDTOS = getSpotIndexes();
        if (CollectionUtils.isEmpty(tickerDTOS)) {
            log.error("empty spotIndexes");
            return null;
        }
        String assetName = AssetExtraProperties.getNameByAssetId(assetId);
        return getSpotIndexByAssetName(assetName);
    }


    public BigDecimal getSpotIndexByAssetName(String assetName){
        List<TickerDTO> tickerDTOS = getSpotIndexes();
        if (null == assetName) {
            log.error("no such asset");
            return null;
        }
        TickerDTO tickerDTO = tickerDTOS.stream()
                .filter(x -> assetName.equals(x.getSymbol()))
                .findFirst().orElse(null);
        if (null == tickerDTO || null == tickerDTO.getSymbol()) {
            log.error("null spotIndex, assetId={}, symbol={}", assetName);
            return null;
        }

        return tickerDTO.getPrice();
    }



}
