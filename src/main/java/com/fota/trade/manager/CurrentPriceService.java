package com.fota.trade.manager;

import com.fota.data.domain.TickerDTO;
import com.fota.trade.client.AssetExtraProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by lds on 2018/11/12.
 * Code is the law
 */
@Component
@Slf4j
public class CurrentPriceService {
    @Autowired
    private CurrentPriceManager currentPriceManager;
    public BigDecimal getSpotIndexByContractName(String contractName){
        String assetName = AssetExtraProperties.getAssetNameByContractName(contractName);
        if (null == assetName) {
            return null;
        }
        return getSpotIndexByAssetName(assetName);
    }

    public BigDecimal getSpotIndexByAssetId(long assetId){
        String assetName = AssetExtraProperties.getNameByAssetId(assetId);
        return getSpotIndexByAssetName(assetName);
    }

    public List<TickerDTO> getSpotIndexes(){
        return currentPriceManager.getSpotIndexes();
    }


    public BigDecimal getSpotIndexByAssetName(String assetName){
        List<TickerDTO> tickerDTOS = currentPriceManager.getSpotIndexes();
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
