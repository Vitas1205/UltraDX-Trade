package com.fota.trade.manager;

import com.fota.client.common.ResultCode;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/7
 * @Modified:
 */

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

    public ResultCode order(UsdkOrderDTO usdkOrderDTO){
        ResultCode resultCode = null;
        UsdkOrderDO usdkOrderDO = null;
        BeanUtils.copyProperties(usdkOrderDTO,usdkOrderDO);
        Integer assetId = usdkOrderDO.getAssetId();
        Long userId = usdkOrderDO.getUserId();
        Integer orderDirection = usdkOrderDO.getOrderDirection();
        BigDecimal price = usdkOrderDO.getPrice();
        BigDecimal totalAmount = usdkOrderDO.getTotalAmount();
        BigDecimal orderValue = totalAmount.multiply(price);
        BigDecimal feeValue = orderValue.multiply(usdkFee);
        BigDecimal totalValue = orderValue.add(feeValue);
        if (orderDirection == OrderDirectionEnum.BID.getCode()){
            //todo 查询usdk账户可用余额
            //todo 判断账户可用余额是否大于tatalValue
        }else if (orderDirection == OrderDirectionEnum.ASK.getCode()){
            //todo 查询对应资产账户可用余额
            //todo 判断账户可用余额是否大于tatalValue
        }
        usdkOrderDO.setFee(usdkFee);
        usdkOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        usdkOrderDO.setUnfilledAmount(usdkOrderDTO.getTotalAmount());
        int Ret = usdkOrderMapper.insert(usdkOrderDO);
        if (Ret > 0){
            resultCode = ResultCode.success();
            //todo 放入缓存

            //todo 发送RocketMQ
        }else {
            resultCode = ResultCode.error(1,"usdkOrder insert failed");
        }
        return resultCode;
    }

}



