package com.fota.trade.common;

import com.fota.trade.domain.*;
import org.springframework.beans.BeansException;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
        contractCategoryDTO.setTotalAmount(contractCategoryDO.getTotalAmount());
        contractCategoryDTO.setUnfilledAmount(contractCategoryDO.getUnfilledAmount());
        contractCategoryDTO.setDeliveryDate(contractCategoryDO.getDeliveryDate().getTime());
        contractCategoryDTO.setStatus(contractCategoryDO.getStatus());
        contractCategoryDTO.setContractType(contractCategoryDO.getContractType());
        contractCategoryDTO.setContractSize(contractCategoryDO.getContractSize());
        return contractCategoryDTO;
    }

    public static ContractCategoryDO copy(ContractCategoryDTO contractCategoryDTO) {
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        contractCategoryDO.setId((contractCategoryDTO.getId()));
        contractCategoryDO.setGmtCreate(contractCategoryDTO.getGmtCreate());
        contractCategoryDO.setGmtModified(contractCategoryDTO.getGmtModified());
        contractCategoryDO.setContractName(contractCategoryDTO.getContractName());
        contractCategoryDO.setAssetId(contractCategoryDTO.getAssetId());
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
        usdkOrderDTO.setPrice(usdkOrderDO.getPrice());
        usdkOrderDTO.setFee(usdkOrderDO.getFee());
        usdkOrderDTO.setStatus(usdkOrderDO.getStatus());
        usdkOrderDTO.setAveragePrice(usdkOrderDO.getAveragePrice());
        return usdkOrderDTO;
    }

    public static UsdkOrderDO copy(UsdkOrderDTO usdkOrderDTO) {
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        if (usdkOrderDTO.getId() != null) {
            usdkOrderDO.setId(usdkOrderDTO.getId());
        }
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
        usdkOrderDO.setPrice(usdkOrderDTO.getPrice());
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
        contractOrderDTO.setPrice(contractOrderDO.getPrice());
        contractOrderDTO.setCloseType(contractOrderDO.getCloseType());
        contractOrderDTO.setFee(contractOrderDO.getFee());
        contractOrderDTO.setStatus(contractOrderDO.getStatus());
        contractOrderDTO.setAveragePrice(contractOrderDO.getAveragePrice());
        return contractOrderDTO;
    }

    public static ContractOrderDO copy(com.fota.trade.domain.ContractOrderDTO contractOrderDTO) {
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        if (contractOrderDTO.getId() != null) {
            contractOrderDO.setId(contractOrderDTO.getId());
        }
        if (contractOrderDTO.getGmtCreate() != null) {
            contractOrderDO.setGmtCreate(contractOrderDTO.getGmtCreate());
        }
        if (contractOrderDTO.getGmtModified() != null) {
            contractOrderDO.setGmtModified(contractOrderDTO.getGmtModified());
        }
        if (contractOrderDTO.getUserId() != null) {
            contractOrderDO.setUserId(contractOrderDTO.getUserId());
        }if (contractOrderDTO.getOrderDirection() != null) {
            contractOrderDO.setOrderDirection(contractOrderDTO.getOrderDirection());
        }if (contractOrderDTO.getOrderType() != null) {
            contractOrderDO.setOrderType(contractOrderDTO.getOrderType());
        }if (contractOrderDTO.getOperateType() != null) {
            contractOrderDO.setOperateType(contractOrderDTO.getOperateType());
        }if (contractOrderDTO.getOperateDirection() != null) {
            contractOrderDO.setOperateDirection(contractOrderDTO.getOperateDirection());
        }if (contractOrderDTO.getUnfilledAmount() != null) {
            contractOrderDO.setUnfilledAmount(contractOrderDTO.getUnfilledAmount());
        }if (contractOrderDTO.getCloseType() != null) {
            contractOrderDO.setCloseType(contractOrderDTO.getCloseType());
        }if (contractOrderDTO.getAveragePrice() != null) {
            contractOrderDO.setAveragePrice(contractOrderDTO.getAveragePrice());
        }
        contractOrderDO.setContractId(contractOrderDTO.getContractId());
        contractOrderDO.setContractName(contractOrderDTO.getContractName());
        if (contractOrderDTO.getTotalAmount() != null){
            contractOrderDO.setTotalAmount(contractOrderDTO.getTotalAmount());
        }
        contractOrderDO.setPrice(contractOrderDTO.getPrice());
        return contractOrderDO;
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
        userPositionDTO.setAveragePrice(userPositionDO.getAveragePrice().toString());
        userPositionDTO.setAmount(userPositionDO.getUnfilledAmount());
        userPositionDTO.setContractSize(userPositionDO.getContractSize());
        return userPositionDTO;
    }

    public static ContractMatchedOrderDO copy(ContractMatchedOrderDTO contractMatchedOrderDTO) {
        ContractMatchedOrderDO contractMatchedOrderDO = new ContractMatchedOrderDO();
        contractMatchedOrderDO.setAskOrderId(contractMatchedOrderDTO.getAskOrderId());
        contractMatchedOrderDO.setAskOrderPrice(new BigDecimal(contractMatchedOrderDTO.getAskOrderPrice()));
        contractMatchedOrderDO.setBidOrderId(contractMatchedOrderDTO.getBidOrderId());
        contractMatchedOrderDO.setBidOrderPrice(new BigDecimal(contractMatchedOrderDTO.getBidOrderPrice()));
        contractMatchedOrderDO.setMatchType(contractMatchedOrderDTO.getMatchType().byteValue());
        contractMatchedOrderDO.setFilledPrice(new BigDecimal(contractMatchedOrderDTO.getFilledPrice()));
        contractMatchedOrderDO.setFilledAmount(new BigDecimal(contractMatchedOrderDTO.getFilledAmount()));
        contractMatchedOrderDO.setContractName(contractMatchedOrderDTO.getContractName());
        contractMatchedOrderDO.setGmtCreate(contractMatchedOrderDTO.getGmtCreate());
        return contractMatchedOrderDO;
    }

    public static UsdkMatchedOrderDO copy(UsdkMatchedOrderDTO usdkMatchedOrderDTO) {
        UsdkMatchedOrderDO usdkMatchedOrderDO = new UsdkMatchedOrderDO();

        usdkMatchedOrderDO.setAssetName(usdkMatchedOrderDTO.getAssetName());
        usdkMatchedOrderDO.setAskOrderPrice(new BigDecimal(usdkMatchedOrderDTO.getAskOrderPrice()));
        usdkMatchedOrderDO.setAskOrderId(usdkMatchedOrderDTO.getAskOrderId());
        usdkMatchedOrderDO.setBidOrderId(usdkMatchedOrderDTO.getBidOrderId());
        usdkMatchedOrderDO.setBidOrderPrice(new BigDecimal(usdkMatchedOrderDTO.getBidOrderPrice()));
        usdkMatchedOrderDO.setFilledAmount(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()));
        usdkMatchedOrderDO.setFilledPrice(new BigDecimal(usdkMatchedOrderDTO.getFilledPrice()));
        usdkMatchedOrderDO.setMatchType(usdkMatchedOrderDTO.getMatchType().byteValue());
        usdkMatchedOrderDO.setGmtCreate(usdkMatchedOrderDTO.getGmtCreate());
        return usdkMatchedOrderDO;
    }



}
