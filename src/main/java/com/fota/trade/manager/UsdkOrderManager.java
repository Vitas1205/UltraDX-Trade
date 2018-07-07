package com.fota.trade.manager;

import com.fota.client.common.BeanUtil;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class UsdkOrderManager {

    private static BigDecimal usdkFee = BigDecimal.valueOf(0.001);

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    public List<UsdkOrderDO> listNotMatchOrder(Long contractOrderIndex, Integer orderDirection) {
        List<UsdkOrderDO> notMatchOrderList = null;
        try {
            notMatchOrderList = usdkOrderMapper.notMatchOrderList(
                    OrderStatusEnum.COMMIT.getCode(), OrderStatusEnum.PART_MATCH.getCode(), contractOrderIndex, orderDirection);
        } catch (Exception e) {
            log.error("usdkOrderMapper.notMatchOrderList error", e);
        }
        if (notMatchOrderList == null) {
            notMatchOrderList = new ArrayList<>();
        }
        return notMatchOrderList;
    }

    public ResultCode orderImp(UsdkOrderDTO usdkOrderDTO){
        ResultCode resultCode = null;
        UsdkOrderDO usdkOrderDO = BeanUtil.copy(usdkOrderDTO);
        BigDecimal unfilledAmount = usdkOrderDTO.getTotalAmount();
        Integer status = OrderStatusEnum.COMMIT.getCode();
        BigDecimal fee = usdkFee;
        int Ret = usdkOrderMapper.insert(usdkOrderDO);
        return resultCode;
    }

}



