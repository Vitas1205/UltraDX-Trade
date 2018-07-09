package com.fota.trade.manager;

import com.alibaba.rocketmq.shade.com.alibaba.fastjson.JSONObject;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.trade.cache.RedisCache;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.Serializable;
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

    @Autowired
    private RedisCache redisCache;

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

    public ResultCode placeOrder(UsdkOrderDTO usdkOrderDTO){
        ResultCode resultCode = new ResultCode();
        UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
        BeanUtils.copyProperties(usdkOrderDTO,usdkOrderDO);
        Integer assetId = usdkOrderDO.getAssetId();
        Long userId = usdkOrderDO.getUserId();
        Integer orderDirection = usdkOrderDO.getOrderDirection();
        BigDecimal price = usdkOrderDO.getPrice();
        BigDecimal totalAmount = usdkOrderDO.getTotalAmount();
        BigDecimal orderValue = totalAmount.multiply(price);
        BigDecimal feeValue = orderValue.multiply(usdkFee);
        //BigDecimal totalValue = orderValue.add(feeValue);
        /*if (orderDirection == OrderDirectionEnum.BID.getCode()){
            //todo 查询usdk账户可用余额
            //todo 判断账户可用余额是否大于tatalValue

        }else if (orderDirection == OrderDirectionEnum.ASK.getCode()){
            //todo 查询对应资产账户可用余额
            //todo 判断账户可用余额是否大于tatalValue
        }*/
        usdkOrderDO.setFee(usdkFee);
        usdkOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        usdkOrderDO.setUnfilledAmount(usdkOrderDTO.getTotalAmount());
        int ret = usdkOrderMapper.insertSelective(usdkOrderDO);
        BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
        if (ret > 0){
            resultCode = ResultCode.success();
            /*//todo 放入缓存
            String jsonStr = JSONObject.toJSONString(usdkOrderDTO);
            //redisCache.set(jsonStr);
            //todo 发送RocketMQ*/

        }else {
            resultCode = ResultCode.error(1,"usdkOrder insert failed");
        }
        return resultCode;
    }

    public ResultCode cancelOrder(Long userId, Long orderId){
        ResultCode resultCode = null;
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByIdAndUserId(orderId, userId);
        Integer status = usdkOrderDO.getStatus();
        if (status == OrderStatusEnum.COMMIT.getCode() || status == OrderStatusEnum.CANCEL.getCode()){
            usdkOrderDO.setStatus(OrderStatusEnum.CANCEL.getCode());
        }else if (status == OrderStatusEnum.PART_MATCH.getCode() || status == OrderStatusEnum.PART_CANCEL.getCode()){
            usdkOrderDO.setStatus(OrderStatusEnum.PART_CANCEL.getCode());
        }else if (status == OrderStatusEnum.MATCH.getCode()){
            usdkOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
        }else {
            resultCode = ResultCode.error(2,"usdkOrder status illegal");
        }
        int ret = usdkOrderMapper.updateByOpLock(usdkOrderDO);
        if (ret > 0){
            resultCode = ResultCode.success();
        }else {
            resultCode = ResultCode.error(3,"usdkOrder update failed");
        }
        //todo 放入缓存
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
        String jsonStr = JSONObject.toJSONString(usdkOrderDTO);
        //redisCache.set(jsonStr);
        //todo 发送RocketMQ
        return resultCode;
    }

    @Transactional(rollbackFor={RuntimeException.class, Exception.class})
    public ResultCode cancelAllOrder(Long userId){
        ResultCode resultCode = null;
        List<UsdkOrderDO> list = usdkOrderMapper.selectByUserID(userId);
        UsdkOrderManager usdkOrderManager = new UsdkOrderManager();
        int ret = 0;
        UsdkOrderDTO usdkOrderDTO = null;
        for(UsdkOrderDO usdkOrderDO : list){
            Long orderId = usdkOrderDO.getId();
            resultCode =  usdkOrderManager.cancelOrder(userId, orderId);
            ret = resultCode.getCode();
            if (ret != ResultCode.success().getCode()){
                throw new RuntimeException("cancelAllOrder failed");
            }else {
                //todo 放入缓存
                BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
                String jsonStr = JSONObject.toJSONString(usdkOrderDTO);
                //redisCache.set(jsonStr);
                //todo 发送RocketMQ
            }
        }
        resultCode = ResultCode.success();
        return resultCode;
    }

}



