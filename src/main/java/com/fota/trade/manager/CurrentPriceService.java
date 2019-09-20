package com.fota.trade.manager;

import com.fota.common.manager.FotaAssetManager;
import com.fota.data.domain.TickerDTO;
import com.fota.trade.util.ConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    @Autowired
    private FotaAssetManager fotaAssetManager;

    public BigDecimal getSpotIndexByAssetId(long assetId){
        String assetName = fotaAssetManager.getAssetNameById((int) assetId);
        return getSpotIndexByAssetName(assetName);
    }

    public List<TickerDTO> getSpotIndexes(){
        return currentPriceManager.getSpotIndexes();
    }


    public BigDecimal getSpotIndexByAssetName(String assetName){
        List<TickerDTO> tickerDTOS = currentPriceManager.getSpotIndexes();
        if (StringUtils.isEmpty(assetName)) {
            log.error("no such asset, assetName={}", assetName);
            return null;
        }
        TickerDTO tickerDTO = tickerDTOS.stream()
                .filter(x -> assetName.equals(x.getSymbol()))
                .findFirst().orElse(null);
        if (null == tickerDTO || null == tickerDTO.getPrice()) {
            log.error("null spotIndex,  symbol={}, tickerDTO={}", assetName, tickerDTO);
            return null;
        }

        return tickerDTO.getPrice();
    }
}
