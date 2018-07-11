package com.fota.trade.common;

import com.fota.client.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
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
        contractCategoryDTO.setGmtCreate(contractCategoryDO.getGmtCreate().getTime());
        contractCategoryDTO.setGmtModified(contractCategoryDO.getGmtModified().getTime());
        contractCategoryDTO.setContractName(contractCategoryDO.getContractName());
        contractCategoryDTO.setAssetId(contractCategoryDO.getAssetId());
        contractCategoryDTO.setAssetName(contractCategoryDO.getAssetName());
        contractCategoryDTO.setTotalAmount(contractCategoryDO.getTotalAmount());
        contractCategoryDTO.setUnfilledAmount(contractCategoryDO.getUnfilledAmount());
        contractCategoryDTO.setDeliveryDate(contractCategoryDO.getDeliveryDate().getTime());
        contractCategoryDTO.setStatus(contractCategoryDO.getStatus());
        contractCategoryDTO.setContractType(contractCategoryDO.getContractType());
        return contractCategoryDTO;
    }

    public static ContractCategoryDO copy(ContractCategoryDTO contractCategoryDTO) {
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        contractCategoryDO.setId((contractCategoryDTO.getId()));
        contractCategoryDO.setGmtCreate(new Date(contractCategoryDTO.getGmtCreate()));
        contractCategoryDO.setGmtModified(new Date(contractCategoryDTO.getGmtModified()));
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
        usdkOrderDTO.setGmtCreate(usdkOrderDO.getGmtCreate().getTime());
        usdkOrderDTO.setGmtModified(usdkOrderDO.getGmtModified().getTime());
        usdkOrderDTO.setUserId(usdkOrderDO.getUserId());
        usdkOrderDTO.setAssetId(usdkOrderDO.getAssetId());
        usdkOrderDTO.setAssetName(usdkOrderDO.getAssetName());
        usdkOrderDTO.setOrderDirection(usdkOrderDO.getOrderDirection());
        usdkOrderDTO.setOrderType(usdkOrderDO.getOrderType());
        usdkOrderDTO.setTotalAmount(usdkOrderDO.getTotalAmount().toString());
        usdkOrderDTO.setUnfilledAmount(usdkOrderDO.getUnfilledAmount().toString());
        usdkOrderDTO.setPrice(usdkOrderDO.getPrice().toString());
        usdkOrderDTO.setFee(usdkOrderDO.getFee().toString());
        usdkOrderDTO.setStatus(usdkOrderDO.getStatus());
        return usdkOrderDTO;
    }

    public static UsdkOrderDO copy(UsdkOrderDTO usdkOrderDTO) {
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        if (usdkOrderDTO.getId() > 0) {
            usdkOrderDO.setId(usdkOrderDTO.getId());
        }
        if (usdkOrderDTO.getGmtCreate() > 0) {
            usdkOrderDO.setGmtCreate(new Date(usdkOrderDTO.getGmtCreate()));
        }
        if (usdkOrderDTO.getGmtModified() > 0) {
            usdkOrderDO.setGmtModified(new Date(usdkOrderDTO.getGmtModified()));
        }
        usdkOrderDO.setUserId(usdkOrderDTO.getUserId());
        usdkOrderDO.setAssetId(usdkOrderDTO.getAssetId());
        usdkOrderDO.setAssetName(usdkOrderDTO.getAssetName());
        usdkOrderDO.setOrderDirection(usdkOrderDTO.getOrderDirection());
        usdkOrderDO.setOrderType(usdkOrderDTO.getOrderType());
        usdkOrderDO.setTotalAmount(new BigDecimal(usdkOrderDTO.getTotalAmount()));
        usdkOrderDO.setUnfilledAmount(new BigDecimal(usdkOrderDTO.getUnfilledAmount()));
        usdkOrderDO.setPrice(new BigDecimal(usdkOrderDTO.getPrice()));
        usdkOrderDO.setFee(new BigDecimal(usdkOrderDTO.getFee()));
        usdkOrderDO.setStatus(usdkOrderDTO.getStatus());
        return usdkOrderDO;
    }

    public static ResultCode copy(com.fota.client.common.ResultCode resultCode) {
        ResultCode targetResultCode = new ResultCode();
        targetResultCode.setCode(resultCode.getCode());
        targetResultCode.setMessage(resultCode.getMessage());
        return targetResultCode;
    }

    public static com.fota.client.common.ResultCode copy(ResultCode resultCode) {
        com.fota.client.common.ResultCode targetResultCode = new com.fota.client.common.ResultCode();
        targetResultCode.setCode(resultCode.getCode());
        targetResultCode.setMessage(resultCode.getMessage());
        return targetResultCode;
    }

    public static ContractOrderDTO copy(ContractOrderDO contractOrderDO) {
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        contractOrderDTO.setId(contractOrderDO.getId());
        contractOrderDTO.setGmtCreate(contractOrderDO.getGmtCreate().getTime());
        contractOrderDTO.setGmtModified(contractOrderDO.getGmtModified().getTime());
        contractOrderDTO.setUserId(contractOrderDO.getUserId());
        contractOrderDTO.setContractId(contractOrderDO.getContractId());
        contractOrderDTO.setContractName(contractOrderDO.getContractName());
        contractOrderDTO.setOrderDirection(contractOrderDO.getOrderDirection());
        contractOrderDTO.setOrderType(contractOrderDO.getOrderType());
        contractOrderDTO.setTotalAmount(contractOrderDO.getTotalAmount());
        contractOrderDTO.setUnfilledAmount(contractOrderDO.getUnfilledAmount());
        contractOrderDTO.setPrice(contractOrderDO.getPrice().toString());
        contractOrderDTO.setCloseType(contractOrderDO.getCloseType());
        contractOrderDTO.setFee(contractOrderDO.getFee().toString());
        contractOrderDTO.setStatus(contractOrderDO.getStatus());
        return contractOrderDTO;
    }

    public static ContractOrderDO copy(ContractOrderDTO contractOrderDTO) {
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        if (contractOrderDO.getId() > 0) {
            contractOrderDO.setId(contractOrderDO.getId());
        }
        if (contractOrderDTO.getGmtCreate() > 0) {
            contractOrderDO.setGmtCreate(new Date(contractOrderDTO.getGmtCreate()));
        }
        if (contractOrderDTO.getGmtModified() > 0) {
            contractOrderDO.setGmtModified(new Date(contractOrderDTO.getGmtModified()));
        }
        contractOrderDO.setUserId(contractOrderDTO.getUserId());
        contractOrderDO.setContractId(contractOrderDTO.getContractId());
        contractOrderDO.setContractName(contractOrderDTO.getContractName());
        contractOrderDO.setOrderDirection(contractOrderDTO.getOrderDirection());
        contractOrderDO.setOrderType(contractOrderDTO.getOrderType());
        contractOrderDO.setTotalAmount(contractOrderDTO.getTotalAmount());
        contractOrderDO.setUnfilledAmount(contractOrderDTO.getUnfilledAmount());
        contractOrderDO.setPrice(new BigDecimal(contractOrderDTO.getPrice()));
        contractOrderDO.setFee(new BigDecimal(contractOrderDTO.getFee()));
        contractOrderDO.setCloseType(contractOrderDTO.getCloseType());
        contractOrderDO.setStatus(contractOrderDTO.getStatus());
        return contractOrderDO;
    }

}
