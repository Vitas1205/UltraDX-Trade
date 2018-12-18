package com.fota.trade.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.common.enums.FotaApplicationEnum;
import com.fota.trade.UpdateOrderItem;
import com.fota.trade.client.PlaceContractOrderDTO;
import com.fota.trade.client.PlaceOrderRequest;
import com.fota.trade.client.UserLevelEnum;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.domain.ADLMatchedDTO;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.ContractOrderDTO;
import com.fota.trade.msg.ContractDealedMessage;
import com.fota.trade.msg.ContractPlaceOrderMessage;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;
import static com.fota.trade.domain.enums.OrderStatusEnum.COMMIT;
import static com.fota.trade.domain.enums.OrderTypeEnum.LIMIT;

/**
 * Created by Swifree on 2018/9/17.
 * Code is the law
 */
public class ConvertUtils {
    public static final JSONObject resolveCancelResult(String cancelResult) {
        Map<Long, JSONObject> resultMap = JSON.parseObject(cancelResult, Map.class);
        JSONObject res = null;
        Optional<JSONObject> optional = resultMap.entrySet().stream().map(x -> {
            JSONObject val = x.getValue();
            val.put("id", x.getKey());
            return val;
        }).findFirst();
        if (optional.isPresent()) {
            res = optional.get();
        }
        return res;
    }

    /**
     * 获取相反方向
     * @param direction
     * @return
     */
    public static final int opDirection(int direction) {
        return ASK.getCode() + BID.getCode() - direction;
    }


    public static ContractMatchedOrderDO toMatchedOrderDO(long matchId, ADLMatchedDTO adlMatchedDTO, int matchType, long enforceUserId, long contractId, String contractName){
        ContractMatchedOrderDO matchedOrderDO = new ContractMatchedOrderDO();
        matchedOrderDO.setOrderId(adlMatchedDTO.getId())
                .setUserId(adlMatchedDTO.getUserId())
                .setOrderPrice(adlMatchedDTO.getPrice())
                .setOrderDirection(adlMatchedDTO.getDirection())
                .setCloseType(adlMatchedDTO.getOrderType());

        matchedOrderDO.setMatchId(matchId);
        matchedOrderDO.setMatchUserId(enforceUserId);

        matchedOrderDO.setMatchType(matchType);
        matchedOrderDO.setFilledPrice(adlMatchedDTO.getFilledPrice());
        matchedOrderDO.setFilledAmount(adlMatchedDTO.getMatchedAmount());

        BigDecimal fee = adlMatchedDTO.getFilledPrice()
                .multiply(adlMatchedDTO.getMatchedAmount())
                .multiply(adlMatchedDTO.getFee());
        matchedOrderDO.setFee(fee);

        matchedOrderDO.setContractId(contractId);
        matchedOrderDO.setContractName(contractName);
        matchedOrderDO.setStatus(VALID);

        return matchedOrderDO;

    }

    public static ContractMatchedOrderDO toMatchedOrderDO(ContractDealedMessage postDealMessage, BigDecimal orderPrice, int closeType, long matchUserId, int matchType){
        ContractMatchedOrderDO matchedOrderDO = new ContractMatchedOrderDO();
        matchedOrderDO.setOrderId(postDealMessage.getOrderId())
                .setUserId(postDealMessage.getUserId())
                .setOrderPrice(orderPrice)
                .setOrderDirection(postDealMessage.getOrderDirection())
                .setCloseType(closeType);

        matchedOrderDO.setMatchId(postDealMessage.getMatchId());
        matchedOrderDO.setMatchUserId(matchUserId);

        matchedOrderDO.setMatchType(matchType);
        matchedOrderDO.setFilledPrice(postDealMessage.getFilledPrice());
        matchedOrderDO.setFilledAmount(postDealMessage.getFilledAmount());

        BigDecimal fee = postDealMessage.getFilledPrice()
                .multiply(postDealMessage.getFilledAmount())
                .multiply(postDealMessage.getFeeRate());
        matchedOrderDO.setFee(fee);

        matchedOrderDO.setContractId(postDealMessage.getSubjectId());
        matchedOrderDO.setContractName(postDealMessage.getSubjectName());
        matchedOrderDO.setStatus(VALID);

        return matchedOrderDO;

    }

    public static UpdateOrderItem toUpdateOrderItem(ADLMatchedDTO adlMatchedDTO) {
        UpdateOrderItem updateOrderItem = new UpdateOrderItem();
        updateOrderItem.setFilledAmount(adlMatchedDTO.getMatchedAmount())
                .setFilledPrice(adlMatchedDTO.getFilledPrice())
                .setId(adlMatchedDTO.getId())
                .setUserId(adlMatchedDTO.getUserId());
        return updateOrderItem;
    }

    public static ContractPlaceOrderMessage toContractPlaceOrderMessage(ContractOrderDO contractOrderDO){
        ContractPlaceOrderMessage placeOrderMessage = new ContractPlaceOrderMessage();
        placeOrderMessage.setTotalAmount(contractOrderDO.getTotalAmount());
        if (contractOrderDO.getPrice() != null){
            placeOrderMessage.setPrice(contractOrderDO.getPrice());
        }
        placeOrderMessage.setOrderDirection(contractOrderDO.getOrderDirection());
        placeOrderMessage.setOrderType(contractOrderDO.getOrderType());
        placeOrderMessage.setOrderId(contractOrderDO.getId());
        placeOrderMessage.setUserId(contractOrderDO.getUserId());
        placeOrderMessage.setSubjectId(contractOrderDO.getContractId());
        placeOrderMessage.setSubjectName(contractOrderDO.getContractName());
        placeOrderMessage.setFee(contractOrderDO.getFee());
        return placeOrderMessage;
    }



    public static PlaceOrderRequest toPlaceOrderRequest(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap, FotaApplicationEnum caller){
        PlaceOrderRequest<PlaceContractOrderDTO>  placeOrderRequest = new PlaceOrderRequest();
        PlaceContractOrderDTO placeContractOrderDTO = new PlaceContractOrderDTO();
        placeOrderRequest.setPlaceOrderDTOS(Arrays.asList(placeContractOrderDTO));
        placeOrderRequest.setUserId(contractOrderDTO.getUserId());

        placeContractOrderDTO.setSubjectId(contractOrderDTO.getContractId());
        placeContractOrderDTO.setSubjectName(contractOrderDTO.getContractName());
        placeContractOrderDTO.setExtOrderId("0");
        BeanUtils.copy(contractOrderDTO, placeContractOrderDTO);

        if (null != userInfoMap) {
            String userName = userInfoMap.get("username");
            String ip = userInfoMap.get("ip");
            placeOrderRequest.setUserName(userName);
            placeOrderRequest.setIp(ip);
        }
        placeOrderRequest.setCaller(caller);
        placeOrderRequest.setMakerFeeRate(contractOrderDTO.getFee());
        if (null == placeContractOrderDTO.getOrderType()) {
            placeContractOrderDTO.setOrderType(LIMIT.getCode());
        }

        return placeOrderRequest;
    }

    public static ContractOrderDO extractContractOrderDO(PlaceContractOrderDTO x, long userId, BigDecimal feeRate, String userName, String ip){
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setId(BasicUtils.generateId());
        contractOrderDO.setUserId(userId);
        contractOrderDO.setOrderDirection(x.getOrderDirection());
        contractOrderDO.setOrderType(x.getOrderType());
        contractOrderDO.setTotalAmount(x.getTotalAmount());
        contractOrderDO.setContractId(x.getSubjectId());
        contractOrderDO.setContractName(x.getSubjectName());
        contractOrderDO.setPrice(x.getPrice());
        contractOrderDO.setUnfilledAmount(contractOrderDO.getTotalAmount());

        Map<String, Object> newMap = new HashMap<>();
        if (null != userName) {
            newMap.put("username", userName);
        }
        if (null != ip) {
            newMap.put("ip", ip);
        }

        contractOrderDO.setOrderContext(JSON.toJSONString(newMap));
        contractOrderDO.setStatus(COMMIT.getCode());
        contractOrderDO.setFee(feeRate);
        if (null == contractOrderDO.getOrderType()) {
            contractOrderDO.setOrderType(LIMIT.getCode());
        }
        if (null == contractOrderDO.getCloseType()) {
            contractOrderDO.setCloseType(contractOrderDO.getOrderType());
        }
        return contractOrderDO;
    }
}
