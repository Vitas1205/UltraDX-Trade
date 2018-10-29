package com.fota.trade.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.risk.client.domain.UserRRLDTO;
import com.fota.trade.UpdateOrderItem;
import com.fota.trade.client.PostDealMessage;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderCloseType;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderTypeEnum;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;
import static com.fota.trade.domain.enums.OrderTypeEnum.ENFORCE;

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

    public static ContractMatchedOrderDO toMatchedOrderDO(ADLMatchedDTO adlMatchedDTO, int matchType, long enforceUserId, long contractId, String contractName){
        ContractMatchedOrderDO matchedOrderDO = new ContractMatchedOrderDO();
        matchedOrderDO.setOrderId(adlMatchedDTO.getId())
                .setUserId(adlMatchedDTO.getUserId())
                .setOrderPrice(adlMatchedDTO.getPrice())
                .setOrderDirection(adlMatchedDTO.getDirection())
                .setCloseType(OrderCloseType.MANUAL.getCode());

        matchedOrderDO.setMatchId(adlMatchedDTO.getId());
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

    public static UpdateOrderItem toUpdateOrderItem(ADLMatchedDTO adlMatchedDTO) {
        UpdateOrderItem updateOrderItem = new UpdateOrderItem();
        updateOrderItem.setFilledAmount(adlMatchedDTO.getMatchedAmount())
                .setFilledPrice(adlMatchedDTO.getFilledPrice())
                .setId(adlMatchedDTO.getId())
                .setUserId(adlMatchedDTO.getUserId());
        return updateOrderItem;
    }
}
