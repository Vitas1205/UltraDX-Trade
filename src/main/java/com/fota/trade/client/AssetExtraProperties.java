package com.fota.trade.client;

import com.fota.asset.domain.enums.AssetTypeEnum;
import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by lds on 2018/10/20.
 * Code is the law
 */
@Getter
public enum AssetExtraProperties {
    BTC(AssetTypeEnum.BTC.getCode(), 1, 0),
    ETH(AssetTypeEnum.ETH.getCode(), 2, 1),
    EOS(AssetTypeEnum.EOS.getCode(), 3, 2),
    BCH(AssetTypeEnum.BCH.getCode(), 2, 3),
    ETC(AssetTypeEnum.ETC.getCode(), 3, 4),
    LTC(AssetTypeEnum.LTC.getCode(), 2, 5),
    ;

    private long assetId;
    private int precision;
    /**
     * 交割指数的ID
     */
    private int symbol;

    private static final Map<Long, AssetExtraProperties> assetPropertiesMap;

    static {
        assetPropertiesMap = Stream.of(AssetExtraProperties.values())
                .collect(Collectors.toMap(AssetExtraProperties::getAssetId, x -> x));
    }

    AssetExtraProperties(long assetId, int precision, int symbol) {
        this.assetId = assetId;
        this.precision = precision;
        this.symbol = symbol;
    }

    public static Integer getPrecisionByAssetId(long assetId) {
        AssetExtraProperties extraProperties =  assetPropertiesMap.get(assetId);
        if (null == extraProperties) {
            return null;
        }
        return extraProperties.getPrecision();
    }
    public static Integer getSymbolByAssetId(long assetId) {
        AssetExtraProperties extraProperties =  assetPropertiesMap.get(assetId);
        if (null == extraProperties) {
            return null;
        }
        return extraProperties.getSymbol();
    }

    public static AssetExtraProperties getAssetExtraProperties(long assetId){
        return assetPropertiesMap.get(assetId);
    }
    public static String getNameByAssetId(long assetId){
        AssetExtraProperties extraProperties =  assetPropertiesMap.get(assetId);
        if (null == extraProperties) {
            return null;
        }
        return extraProperties.name();
    }
    public static String getAssetNameByContractName(String contractName) {
        if (null == contractName) {
            return null;
        }
        for (AssetTypeEnum assetTypeEnum : AssetTypeEnum.values()) {
            if (contractName.contains(assetTypeEnum.name())) {
                return assetTypeEnum.name();
            }
        }
        return null;
    }



}
