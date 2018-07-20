package com.fota.trade.manager;

import com.fota.asset.domain.UserCapitalDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.CapitalService;
import com.fota.client.common.ResultCode;
import com.fota.client.common.ResultCodeEnum;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.trade.domain.OrderMessage;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.AssetTypeEnum;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderOperateTypeEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
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
    private RedisManager redisManager;

    @Autowired
    private RocketMqManager rocketMqManager;
    @Autowired
    private CapitalService capitalService;
    @Autowired
    private AssetService assetService;

    private CapitalService getCapitalService() {
        return capitalService;
    }
    private AssetService getAssetService() {
        return assetService;
    }

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

    @Transactional(rollbackFor={RuntimeException.class, Exception.class})
    public ResultCode placeOrder(UsdkOrderDO usdkOrderDO)throws Exception {
        ResultCode resultCode = new ResultCode();
        Integer assetId = usdkOrderDO.getAssetId();
        Long userId = usdkOrderDO.getUserId();
        Integer orderDirection = usdkOrderDO.getOrderDirection();
        BigDecimal price = usdkOrderDO.getPrice();
        BigDecimal totalAmount = usdkOrderDO.getTotalAmount();
        BigDecimal orderValue = totalAmount.multiply(price);
        List<UserCapitalDTO> list = getAssetService().getUserCapital(userId);

        usdkOrderDO.setFee(usdkFee);
        usdkOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        usdkOrderDO.setUnfilledAmount(usdkOrderDO.getTotalAmount());
        int ret = usdkOrderMapper.insertSelective(usdkOrderDO);
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
        if (ret > 0){
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                //查询usdk账户可用余额
                for(UserCapitalDTO userCapitalDTO : list){
                    if (userCapitalDTO.getAssetId() == AssetTypeEnum.USDK.getCode()){
                        BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                        BigDecimal lockedAmount = new BigDecimal(userCapitalDTO.getLockedAmount());
                        BigDecimal availableAmount = amount.subtract(lockedAmount);
                        //判断账户可用余额是否大于orderValue
                        if (availableAmount.compareTo(orderValue) >= 0){
                            Date gmtModified = userCapitalDTO.getGmtModified();
                            Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,
                                    userCapitalDTO.getAssetId(), String.valueOf(orderValue), gmtModified.getTime());
                            if (!updateLockedAmountRet){
                                throw new RuntimeException("update USDKCapital LockedAmount failed");
                            }
                        }else {
                            throw new RuntimeException("USDK Capital Amount Not Enough");
                        }
                    }
                }
            }else if (orderDirection == OrderDirectionEnum.ASK.getCode()){
                //查询对应资产账户可用余额
                for (UserCapitalDTO userCapitalDTO : list){
                    if (assetId.equals(userCapitalDTO.getAssetId())){
                        BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                        BigDecimal lockedAmount = new BigDecimal(userCapitalDTO.getLockedAmount());
                        BigDecimal availableAmount = amount.subtract(lockedAmount);
                        //断账户可用余额是否大于tatalValue
                        if (availableAmount.compareTo(usdkOrderDO.getTotalAmount()) >= 0){
                            Date gmtModified = userCapitalDTO.getGmtModified();
                            Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,
                                    userCapitalDTO.getAssetId(), String.valueOf(usdkOrderDO.getTotalAmount()), gmtModified.getTime());
                            if (!updateLockedAmountRet){
                                throw new RuntimeException("update Coin Capital LockedAmount failed");
                            }
                        }else {
                            throw new RuntimeException("Coin Capital Amount Not Enough");
                        }
                    }
                }
            }
            resultCode = ResultCode.success();
            usdkOrderDTO.setCompleteAmount(BigDecimal.ZERO);
            redisManager.usdkOrderSave(usdkOrderDTO);
            //todo 发送RocketMQ
            OrderMessage orderMessage = new OrderMessage();
            orderMessage.setOrderId(usdkOrderDTO.getId());
            orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
            orderMessage.setUserId(usdkOrderDTO.getUserId());
            orderMessage.setSubjectId(usdkOrderDTO.getAssetId());
            Boolean sendRet = rocketMqManager.sendMessage("order", "UsdkOrder", orderMessage);
            if (!sendRet){
                log.info("Send RocketMQ Message Failed ");
            }
        }else {
            resultCode = ResultCode.error(ResultCodeEnum.CREATE_USDKORDER_FAILED.getCode(),ResultCodeEnum.CREATE_USDKORDER_FAILED.getMessage());
        }
        return resultCode;
    }

    @Transactional(rollbackFor={RuntimeException.class, Exception.class})
    public ResultCode cancelOrder(Long userId, Long orderId) throws Exception{
        ResultCode resultCode = new ResultCode();
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByIdAndUserId(orderId, userId);
        Integer status = usdkOrderDO.getStatus();
        if (status == OrderStatusEnum.COMMIT.getCode()){
            usdkOrderDO.setStatus(OrderStatusEnum.CANCEL.getCode());
        }else if (status == OrderStatusEnum.PART_MATCH.getCode()){
            usdkOrderDO.setStatus(OrderStatusEnum.PART_CANCEL.getCode());
        }else if (status == OrderStatusEnum.MATCH.getCode() || status == OrderStatusEnum.PART_CANCEL.getCode()  | status == OrderStatusEnum.CANCEL.getCode()){
            resultCode = ResultCode.error(ResultCodeEnum.ORDER_IS_CANCLED.getCode(),ResultCodeEnum.ORDER_IS_CANCLED.getMessage());
            return resultCode;
        }else {
            resultCode = ResultCode.error(ResultCodeEnum.ORDER_STATUS_ILLEGAL.getCode(),ResultCodeEnum.ORDER_STATUS_ILLEGAL.getMessage());
            return resultCode;
        }
        int ret = usdkOrderMapper.updateByOpLock(usdkOrderDO);
        if (ret > 0){
            Integer orderDirection = usdkOrderDO.getOrderDirection();
            Integer assetId = 0;
            BigDecimal unlockAmount = BigDecimal.ZERO;
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                assetId = AssetTypeEnum.USDK.getCode();
                BigDecimal unfilledAmount = usdkOrderDO.getUnfilledAmount();
                BigDecimal price = usdkOrderDO.getPrice();
                unlockAmount = unfilledAmount.multiply(price);
                //解冻USDK钱包账户
                Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,AssetTypeEnum.USDK.getCode(),unlockAmount.negate().toString(), 0L);
                if (!updateLockedAmountRet){
                    throw new RuntimeException("Update USDK LockedAmount Failed");
                }
            }else if (orderDirection == OrderDirectionEnum.ASK.getCode()){
                assetId = usdkOrderDO.getAssetId();
                unlockAmount = usdkOrderDO.getUnfilledAmount();
                //解冻Coin钱包账户
                Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,assetId,unlockAmount.negate().toString(), 0L);
                if (!updateLockedAmountRet){
                    throw new RuntimeException("Update Coin LockedAmount Failed");
                }
            }
            UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
            BigDecimal matchAmount = usdkOrderDTO.getTotalAmount().subtract(usdkOrderDTO.getUnfilledAmount());
            usdkOrderDTO.setCompleteAmount(matchAmount);
            redisManager.usdkOrderSave(usdkOrderDTO);
            //todo 发送RocketMQ
            OrderMessage orderMessage = new OrderMessage();
            orderMessage.setOrderId(usdkOrderDTO.getId());
            orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
            orderMessage.setUserId(usdkOrderDTO.getUserId());
            orderMessage.setSubjectId(usdkOrderDTO.getAssetId());
            Boolean sendRet = rocketMqManager.sendMessage("order", "UsdkOrder", orderMessage);
            if (!sendRet){
                log.error("Send RocketMQ Message Failed ");
            }
            resultCode = ResultCode.success();
        }else {
            resultCode = ResultCode.error(ResultCodeEnum.UPDATE_USDKORDER_FAILED.getCode(),ResultCodeEnum.UPDATE_USDKORDER_FAILED.getMessage());
        }

        return resultCode;
    }


    public ResultCode cancelAllOrder(Long userId) throws Exception{
        ResultCode resultCode = null;
        List<UsdkOrderDO> list = usdkOrderMapper.selectByUserId(userId);
        int i = 0;
        if (list != null){
            for(UsdkOrderDO usdkOrderDO : list){
                if (usdkOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode() || usdkOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode()) {
                    i++;
                    Long orderId = usdkOrderDO.getId();
                    cancelOrder(userId, orderId);
                }
            }
        }
        if (i == 0){
            resultCode = ResultCode.error(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getCode(), ResultCodeEnum.NO_CANCELLABLE_ORDERS.getMessage());
            return resultCode;
        }
        resultCode = ResultCode.success();
        return resultCode;
    }

}



