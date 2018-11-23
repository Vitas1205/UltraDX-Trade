package com.fota.trade.common;

import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.trade.client.PlaceOrderRequest;
import com.fota.trade.domain.*;
import org.springframework.beans.BeansException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderStatusEnum.COMMIT;
import static com.fota.trade.domain.enums.OrderTypeEnum.LIMIT;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public class BeanUtils {

    public static <T> List<T> copyList(List source, Class<T> clazz) throws IllegalAccessException, InstantiationException {
        if (source == null) {
            return null;
        }
        List<T> targetList = new ArrayList<>();
        if (CollectionUtils.isEmpty(source)) {
            return targetList;
        }
        for (Object tempSource : source) {
            T tempTarget = clazz.newInstance();
            org.springframework.beans.BeanUtils.copyProperties(tempSource, tempTarget);
            targetList.add(tempTarget);
        }
        return targetList;
    }

    public static void copy(Object source, Object target) throws BeansException {
        org.springframework.beans.BeanUtils.copyProperties(source, target);
    }


    public static ContractCategoryDTO copy(ContractCategoryDO contractCategoryDO) {
        ContractCategoryDTO contractCategoryDTO = new ContractCategoryDTO();
        contractCategoryDTO.setId(contractCategoryDO.getId());
        contractCategoryDTO.setGmtCreate(contractCategoryDO.getGmtCreate());
        contractCategoryDTO.setGmtModified(contractCategoryDO.getGmtModified());
        contractCategoryDTO.setContractName(contractCategoryDO.getContractName());
        contractCategoryDTO.setAssetId(contractCategoryDO.getAssetId());
        contractCategoryDTO.setAssetName(contractCategoryDO.getAssetName());
//        contractCategoryDTO.setTotalAmount(contractCategoryDO.getTotalAmount());
//        contractCategoryDTO.setUnfilledAmount(contractCategoryDO.getUnfilledAmount());
        contractCategoryDTO.setDeliveryDate(contractCategoryDO.getDeliveryDate().getTime());
        contractCategoryDTO.setStatus(contractCategoryDO.getStatus());
        contractCategoryDTO.setContractType(contractCategoryDO.getContractType());
        return contractCategoryDTO;
    }

    public static ContractCategoryDO copy(ContractCategoryDTO contractCategoryDTO) {
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        contractCategoryDO.setId((contractCategoryDTO.getId()));
        contractCategoryDO.setGmtCreate(contractCategoryDTO.getGmtCreate());
        contractCategoryDO.setGmtModified(contractCategoryDTO.getGmtModified());
        contractCategoryDO.setContractName(contractCategoryDTO.getContractName());
        contractCategoryDO.setAssetId(contractCategoryDTO.getAssetId());
        contractCategoryDO.setAssetName(contractCategoryDTO.getAssetName());
        contractCategoryDO.setTotalAmount(contractCategoryDO.getTotalAmount());
        contractCategoryDO.setUnfilledAmount(contractCategoryDO.getUnfilledAmount());
        contractCategoryDO.setDeliveryDate(new Date(contractCategoryDTO.getDeliveryDate()));
        contractCategoryDO.setStatus(contractCategoryDTO.getStatus());
        contractCategoryDO.setContractType(contractCategoryDTO.getContractType());
        contractCategoryDO.setPrice(new BigDecimal("0.01"));
        return contractCategoryDO;
    }

//    private Long id;
//    private Date gmtCreate;
//    private Date gmtModified;
//    private Long userId;
//    private Integer assetId;
//    private String assetName;
//    private Integer orderDirection;
//    private Integer orderType;
//    private BigDecimal totalAmount;
//    private BigDecimal unfilledAmount;
//    private BigDecimal price;
//    private BigDecimal fee;
//    private Integer status;

    public static UsdkOrderDTO copy(UsdkOrderDO usdkOrderDO) {
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        usdkOrderDTO.setId(usdkOrderDO.getId());
        usdkOrderDTO.setGmtCreate(usdkOrderDO.getGmtCreate());
        usdkOrderDTO.setGmtModified(usdkOrderDO.getGmtModified());
        usdkOrderDTO.setUserId(usdkOrderDO.getUserId());
        usdkOrderDTO.setAssetId(usdkOrderDO.getAssetId());
        usdkOrderDTO.setAssetName(usdkOrderDO.getAssetName());
        usdkOrderDTO.setOrderDirection(usdkOrderDO.getOrderDirection());
        usdkOrderDTO.setOrderType(usdkOrderDO.getOrderType());
        usdkOrderDTO.setTotalAmount(usdkOrderDO.getTotalAmount());
        usdkOrderDTO.setUnfilledAmount(usdkOrderDO.getUnfilledAmount());
        if (usdkOrderDO.getPrice() != null){
            usdkOrderDTO.setPrice(usdkOrderDO.getPrice());
        }
        usdkOrderDTO.setFee(usdkOrderDO.getFee());
        usdkOrderDTO.setStatus(usdkOrderDO.getStatus());
        usdkOrderDTO.setAveragePrice(usdkOrderDO.getAveragePrice());
        return usdkOrderDTO;
    }

    public static UsdkOrderDO copy(UsdkOrderDTO usdkOrderDTO) {
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        usdkOrderDO.setId(usdkOrderDTO.getId());
        if (usdkOrderDTO.getGmtCreate() != null) {
            usdkOrderDO.setGmtCreate(usdkOrderDTO.getGmtCreate());
        }
        if (usdkOrderDTO.getGmtModified() != null) {
            usdkOrderDO.setGmtModified(usdkOrderDTO.getGmtModified());
        }
        usdkOrderDO.setUserId(usdkOrderDTO.getUserId());
        usdkOrderDO.setAssetId(usdkOrderDTO.getAssetId());
        usdkOrderDO.setAssetName(usdkOrderDTO.getAssetName());
        usdkOrderDO.setOrderDirection(usdkOrderDTO.getOrderDirection());
        usdkOrderDO.setOrderType(usdkOrderDTO.getOrderType());
        usdkOrderDO.setTotalAmount(usdkOrderDTO.getTotalAmount());
        usdkOrderDO.setUnfilledAmount(usdkOrderDTO.getUnfilledAmount());
        if (usdkOrderDTO.getPrice() != null){
            usdkOrderDO.setPrice(usdkOrderDTO.getPrice());
        }
        usdkOrderDO.setFee(usdkOrderDTO.getFee());
        usdkOrderDO.setStatus(usdkOrderDTO.getStatus());
        usdkOrderDO.setAveragePrice(usdkOrderDTO.getAveragePrice());
        return usdkOrderDO;
    }

    public static com.fota.trade.domain.ContractOrderDTO copy(ContractOrderDO contractOrderDO) {
        com.fota.trade.domain.ContractOrderDTO contractOrderDTO = new com.fota.trade.domain.ContractOrderDTO();
        contractOrderDTO.setId(contractOrderDO.getId());
        contractOrderDTO.setGmtCreate(contractOrderDO.getGmtCreate());
        contractOrderDTO.setGmtModified(contractOrderDO.getGmtModified());
        contractOrderDTO.setUserId(contractOrderDO.getUserId());
        contractOrderDTO.setContractId(contractOrderDO.getContractId());
        contractOrderDTO.setContractName(contractOrderDO.getContractName());
        contractOrderDTO.setOrderDirection(contractOrderDO.getOrderDirection());
        contractOrderDTO.setOrderType(contractOrderDO.getOrderType());
        contractOrderDTO.setTotalAmount(contractOrderDO.getTotalAmount());
        contractOrderDTO.setUnfilledAmount(contractOrderDO.getUnfilledAmount());
        if (contractOrderDO.getPrice() != null){
            contractOrderDTO.setPrice(contractOrderDO.getPrice());
        }
        contractOrderDTO.setCloseType(contractOrderDO.getCloseType());
        contractOrderDTO.setFee(contractOrderDO.getFee());
        contractOrderDTO.setStatus(contractOrderDO.getStatus());
        contractOrderDTO.setAveragePrice(contractOrderDO.getAveragePrice());
        return contractOrderDTO;
    }



    public static UserPositionDTO copy(UserPositionDO userPositionDO) {
        UserPositionDTO userPositionDTO = new UserPositionDTO();
        userPositionDTO.setId(userPositionDO.getId());
        userPositionDTO.setGmtCreate(userPositionDO.getGmtCreate().getTime());
        userPositionDTO.setGmtModified(userPositionDO.getGmtModified().getTime());
        userPositionDTO.setUserId(userPositionDO.getUserId());
        userPositionDTO.setContractId(userPositionDO.getContractId());
        userPositionDTO.setContractName(userPositionDO.getContractName());
        userPositionDTO.setPositionType(userPositionDO.getPositionType());
        userPositionDTO.setAveragePrice(userPositionDO.getAveragePrice().toPlainString());
        userPositionDTO.setAmount(userPositionDO.getUnfilledAmount());
        userPositionDTO.setContractSize(BigDecimal.ONE);
        userPositionDTO.setFeeRate(userPositionDO.getFeeRate());
        return userPositionDTO;
    }

    public static ContractMatchedOrderDO extractContractMatchedRecord(ContractMatchedOrderDTO contractMatchedOrderDTO, int orderDirection, BigDecimal fee, Integer closeType) {
        ContractMatchedOrderDO res = new ContractMatchedOrderDO();
        res.setGmtCreate(contractMatchedOrderDTO.getGmtCreate())
                .setMatchId(contractMatchedOrderDTO.getId())
                .setMatchType(contractMatchedOrderDTO.getMatchType())
                .setOrderDirection(orderDirection)
                .setStatus(VALID)
                .setFilledAmount(contractMatchedOrderDTO.getFilledAmount())
                .setFilledPrice(new BigDecimal(contractMatchedOrderDTO.getFilledPrice()))
                .setContractId(contractMatchedOrderDTO.getContractId())
                .setContractName(contractMatchedOrderDTO.getContractName());

        res.setFee(fee);
        res.setCloseType(closeType);

        BigDecimal askPrice = null, bidPrice = null;
        if (!StringUtils.isEmpty(contractMatchedOrderDTO.getAskOrderPrice())){
            askPrice = new BigDecimal(contractMatchedOrderDTO.getAskOrderPrice());
        }
        if (!StringUtils.isEmpty(contractMatchedOrderDTO.getBidOrderPrice())){
            bidPrice = new BigDecimal(contractMatchedOrderDTO.getBidOrderPrice());
        }
        if (ASK.getCode() == orderDirection) {
            res.setUserId(contractMatchedOrderDTO.getAskUserId());
            res.setOrderId(contractMatchedOrderDTO.getAskOrderId());
            res.setOrderPrice(askPrice);
            res.setMatchUserId(contractMatchedOrderDTO.getBidUserId());
        }else {
            res.setUserId(contractMatchedOrderDTO.getBidUserId());
            res.setOrderId(contractMatchedOrderDTO.getBidOrderId());
            res.setOrderPrice(bidPrice);
            res.setMatchUserId(contractMatchedOrderDTO.getAskUserId());
        }
        return res;
    }

    public static UsdkMatchedOrderDO extractUsdtRecord(UsdkMatchedOrderDTO usdkMatchedOrderDTO, int orderDirec) {
        UsdkMatchedOrderDO usdkMatchedOrderDO = new UsdkMatchedOrderDO();
        usdkMatchedOrderDO.setMatchId(usdkMatchedOrderDTO.getId());
        usdkMatchedOrderDO.setOrderDirection(orderDirec);
        usdkMatchedOrderDO.setCloseType(0);
        if (orderDirec == ASK.getCode()) {
            if (usdkMatchedOrderDTO.getAskOrderPrice() != null){
                usdkMatchedOrderDO.setOrderPrice(new BigDecimal(usdkMatchedOrderDTO.getAskOrderPrice()));
            }
            usdkMatchedOrderDO.setOrderId(usdkMatchedOrderDTO.getAskOrderId());
            usdkMatchedOrderDO.setUserId(usdkMatchedOrderDTO.getAskUserId());
            usdkMatchedOrderDO.setMatchUserId(usdkMatchedOrderDTO.getBidUserId());
        }else {
            if (usdkMatchedOrderDTO.getBidOrderPrice() != null){
                usdkMatchedOrderDO.setOrderPrice(new BigDecimal(usdkMatchedOrderDTO.getBidOrderPrice()));
            }
            usdkMatchedOrderDO.setOrderId(usdkMatchedOrderDTO.getBidOrderId());
            usdkMatchedOrderDO.setUserId(usdkMatchedOrderDTO.getBidUserId());
            usdkMatchedOrderDO.setMatchUserId(usdkMatchedOrderDTO.getAskUserId());
        }

        usdkMatchedOrderDO.setAssetName(AssetTypeEnum.getAssetNameByAssetId(usdkMatchedOrderDTO.getAssetId()));
        usdkMatchedOrderDO.setAssetId(usdkMatchedOrderDTO.getAssetId());

        usdkMatchedOrderDO.setFilledAmount(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()));
        usdkMatchedOrderDO.setFilledPrice(new BigDecimal(usdkMatchedOrderDTO.getFilledPrice()));
        usdkMatchedOrderDO.setMatchType(usdkMatchedOrderDTO.getMatchType());
        usdkMatchedOrderDO.setGmtCreate(usdkMatchedOrderDTO.getGmtCreate());
        return usdkMatchedOrderDO;
    }



}
