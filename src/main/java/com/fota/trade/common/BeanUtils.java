package com.fota.trade.common;

import com.fota.trade.domain.*;
import org.springframework.beans.BeansException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;

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
        usdkOrderDTO.setBrokerId(usdkOrderDO.getBrokerId());
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
        if (usdkOrderDTO.getBrokerId() != null){
            usdkOrderDO.setBrokerId(usdkOrderDTO.getBrokerId());
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
        usdkOrderDO.setBrokerId(usdkOrderDTO.getBrokerId());
        return usdkOrderDO;
    }

    public static UsdkMatchedOrderDO extractUsdtRecord(UsdkMatchedOrderDTO usdkMatchedOrderDTO, int orderDirec, Long brokerId,
                                                       BigDecimal fee) {
        UsdkMatchedOrderDO usdkMatchedOrderDO = extractUsdtRecord(usdkMatchedOrderDTO, orderDirec,fee);
        usdkMatchedOrderDO.setBrokerId(brokerId);

        return usdkMatchedOrderDO;
    }

    public static UsdkMatchedOrderDO extractUsdtRecord(UsdkMatchedOrderDTO usdkMatchedOrderDTO, int orderDirec,BigDecimal fee) {
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
            usdkMatchedOrderDO.setBrokerId(usdkMatchedOrderDTO.getAskBrokerId());
            usdkMatchedOrderDO.setFee(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()).multiply(fee));
        }else {
            if (usdkMatchedOrderDTO.getBidOrderPrice() != null){
                usdkMatchedOrderDO.setOrderPrice(new BigDecimal(usdkMatchedOrderDTO.getBidOrderPrice()));
            }
            usdkMatchedOrderDO.setOrderId(usdkMatchedOrderDTO.getBidOrderId());
            usdkMatchedOrderDO.setUserId(usdkMatchedOrderDTO.getBidUserId());
            usdkMatchedOrderDO.setMatchUserId(usdkMatchedOrderDTO.getAskUserId());
            usdkMatchedOrderDO.setBrokerId(usdkMatchedOrderDTO.getBidBrokerId());
            usdkMatchedOrderDO.setFee(
                    new BigDecimal(StringUtils.isEmpty(usdkMatchedOrderDTO.getFilledAmount())?"0":usdkMatchedOrderDTO.getFilledAmount()).
                    multiply(
                            new BigDecimal(StringUtils.isEmpty(usdkMatchedOrderDTO.getFilledPrice())?"0":usdkMatchedOrderDTO.getFilledPrice())
                    ).multiply(fee)
            );
        }

        usdkMatchedOrderDO.setAssetName(usdkMatchedOrderDTO.getAssetName());
        usdkMatchedOrderDO.setAssetId(usdkMatchedOrderDTO.getAssetId());

        usdkMatchedOrderDO.setFilledAmount(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()));
        usdkMatchedOrderDO.setFilledPrice(new BigDecimal(usdkMatchedOrderDTO.getFilledPrice()));
        usdkMatchedOrderDO.setMatchType(usdkMatchedOrderDTO.getMatchType());
        usdkMatchedOrderDO.setGmtCreate(usdkMatchedOrderDTO.getGmtCreate());

        return usdkMatchedOrderDO;
    }





}
