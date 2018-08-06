package com.fota.trade.manager;

import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.BalanceTransferDTO;
import com.fota.asset.domain.UserCapitalDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.CapitalService;

import com.fota.match.domain.UsdkMatchedOrderMarketDTO;
import com.fota.match.domain.UsdkMatchedOrderTradeDTO;
import com.fota.match.service.UsdkMatchedOrderService;
import com.fota.trade.common.BusinessException;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.AssetTypeEnum;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderOperateTypeEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.UsdkMatchedOrderMapper;
import com.fota.trade.mapper.UsdkOrderMapper;
import com.fota.trade.util.PriceUtil;
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

    private static BigDecimal usdkFee = BigDecimal.valueOf(0);

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

    @Autowired
    private UsdkMatchedOrderService usdkMatchedOrderService;

    @Autowired
    private UsdkMatchedOrderMapper usdkMatchedOrder;

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

    @Transactional(rollbackFor={RuntimeException.class, Exception.class, BusinessException.class})
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
                                throw new BusinessException(ResultCodeEnum.UPDATE_USDK_CAPITAL_LOCKEDAMOUNT_FAILED.getCode(), ResultCodeEnum.UPDATE_USDK_CAPITAL_LOCKEDAMOUNT_FAILED.getMessage());
                            }
                        }else {
                            throw new BusinessException(ResultCodeEnum.USDK_CAPITAL_AMOUNT_NOT_ENOUGH.getCode(), ResultCodeEnum.USDK_CAPITAL_AMOUNT_NOT_ENOUGH.getMessage());
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
                                throw new BusinessException(ResultCodeEnum.UPDATE_COIN_CAPITAL_LOCKEDAMOUNT_FAILED.getCode(), ResultCodeEnum.UPDATE_COIN_CAPITAL_LOCKEDAMOUNT_FAILED.getMessage());
                            }
                        }else {
                            throw new BusinessException(ResultCodeEnum.COIN_CAPITAL_AMOUNT_NOT_ENOUGH.getCode(), ResultCodeEnum.COIN_CAPITAL_AMOUNT_NOT_ENOUGH.getMessage());
                        }
                    }
                }
            }
            resultCode = ResultCode.success();
            usdkOrderDTO.setCompleteAmount("0");
            redisManager.usdkOrderSave(usdkOrderDTO);
            //todo 发送RocketMQ
            OrderMessage orderMessage = new OrderMessage();
            orderMessage.setOrderId(usdkOrderDTO.getId());
            orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
            orderMessage.setUserId(usdkOrderDTO.getUserId());
            orderMessage.setSubjectId(usdkOrderDTO.getAssetId().longValue());
            orderMessage.setAmount(usdkOrderDO.getTotalAmount());
            orderMessage.setPrice(usdkOrderDO.getPrice());
            orderMessage.setTransferTime(System.currentTimeMillis());
            Boolean sendRet = rocketMqManager.sendMessage("order", "UsdkOrder", orderMessage);
            if (!sendRet){
                log.error("Send RocketMQ Message Failed ");
            }
        }else {
            resultCode = ResultCode.error(ResultCodeEnum.INSERT_USDK_ORDER_FAILED.getCode(),ResultCodeEnum.INSERT_USDK_ORDER_FAILED.getMessage());
        }
        return resultCode;
    }

    @Transactional(rollbackFor={RuntimeException.class, Exception.class, BusinessException.class})
    public ResultCode cancelOrder(Long userId, Long orderId) throws Exception{
        ResultCode resultCode = new ResultCode();
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByIdAndUserId(orderId, userId);
        Integer status = usdkOrderDO.getStatus();
        boolean judegRet = getJudegRet(orderId,usdkOrderDO.getOrderDirection(),usdkOrderDO.getUnfilledAmount());
        if (!judegRet){
            resultCode = ResultCode.error(ResultCodeEnum.ORDER_CAN_NOT_CANCLE.getCode(),ResultCodeEnum.ORDER_CAN_NOT_CANCLE.getMessage());
            return resultCode;
        }
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
                    throw new BusinessException(ResultCodeEnum.UPDATE_USDK_CAPITAL_LOCKEDAMOUNT_FAILED.getCode(), ResultCodeEnum.UPDATE_USDK_CAPITAL_LOCKEDAMOUNT_FAILED.getMessage());
                }
            }else if (orderDirection == OrderDirectionEnum.ASK.getCode()){
                assetId = usdkOrderDO.getAssetId();
                unlockAmount = usdkOrderDO.getUnfilledAmount();
                //解冻Coin钱包账户
                Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,assetId,unlockAmount.negate().toString(), 0L);
                if (!updateLockedAmountRet){
                    throw new BusinessException(ResultCodeEnum.UPDATE_COIN_CAPITAL_LOCKEDAMOUNT_FAILED.getCode(), ResultCodeEnum.UPDATE_COIN_CAPITAL_LOCKEDAMOUNT_FAILED.getMessage());
                }
            }
            UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
            BigDecimal matchAmount = new BigDecimal(usdkOrderDTO.getTotalAmount()).subtract(new BigDecimal(usdkOrderDTO.getUnfilledAmount()));
            usdkOrderDTO.setCompleteAmount(String.valueOf(matchAmount));
            redisManager.usdkOrderSave(usdkOrderDTO);
            //todo 发送RocketMQ
            OrderMessage orderMessage = new OrderMessage();
            orderMessage.setOrderId(usdkOrderDTO.getId());
            orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
            orderMessage.setUserId(usdkOrderDTO.getUserId());
            orderMessage.setSubjectId(usdkOrderDTO.getAssetId().longValue());
            orderMessage.setAmount(usdkOrderDO.getTotalAmount());
            orderMessage.setPrice(usdkOrderDO.getPrice());
            orderMessage.setTransferTime(System.currentTimeMillis());
            Boolean sendRet = rocketMqManager.sendMessage("order", "UsdkOrder", orderMessage);
            if (!sendRet){
                log.error("Send RocketMQ Message Failed ");
            }
            resultCode = ResultCode.success();
        }else {
            resultCode = ResultCode.error(ResultCodeEnum.UPDATE_USDK_ORDER_FAILED.getCode(),ResultCodeEnum.UPDATE_USDK_ORDER_FAILED.getMessage());
        }

        return resultCode;
    }

    public boolean getJudegRet(Long orderId, Integer orderDeriction, BigDecimal unfilledAmount){
        return usdkMatchedOrderService.cancelOrderUsdk(orderId, orderDeriction, unfilledAmount);
    }


    public ResultCode cancelAllOrder(Long userId) throws Exception{
        ResultCode resultCode = new ResultCode();
        List<UsdkOrderDO> list = usdkOrderMapper.selectUnfinishedOrderByUserId(userId);
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

    @Transactional(rollbackFor = {Exception.class, RuntimeException.class, BusinessException.class})
    public com.fota.trade.domain.ResultCode updateOrderByMatch(UsdkMatchedOrderDTO usdkMatchedOrderDTO) throws Exception {
        if (usdkMatchedOrderDTO == null) {
            log.error(ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            throw new RuntimeException(ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        UsdkOrderDTO bidUsdkOrderDTO = new UsdkOrderDTO();
        UsdkOrderDTO askUsdkOrderDTO = new UsdkOrderDTO();
        UsdkOrderDO askUsdkOrder = usdkOrderMapper.selectByPrimaryKey(usdkMatchedOrderDTO.getAskOrderId());
        UsdkOrderDO bidUsdkOrder = usdkOrderMapper.selectByPrimaryKey(usdkMatchedOrderDTO.getBidOrderId());
        log.info("---------------"+usdkMatchedOrderDTO.toString());
        log.info("---------------"+askUsdkOrder.toString());
        log.info("---------------"+bidUsdkOrder.toString());
        if (askUsdkOrder.getUnfilledAmount().compareTo(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount())) < 0
                || bidUsdkOrder.getUnfilledAmount().compareTo(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount())) < 0){
            log.error(ResultCodeEnum.ORDER_UNFILLEDAMOUNT_NOT_ENOUGHT.getMessage());
            throw new RuntimeException(ResultCodeEnum.ORDER_UNFILLEDAMOUNT_NOT_ENOUGHT.getMessage());
        }
        if (askUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()
                && bidUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error(ResultCodeEnum.ASK_AND_BID_ILLEGAL.getMessage());
            throw new RuntimeException(ResultCodeEnum.ASK_AND_BID_ILLEGAL.getMessage());
        }
        if (askUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error(ResultCodeEnum.ASK_ILLEGAL.getMessage());
            throw new RuntimeException(ResultCodeEnum.ASK_ILLEGAL.getMessage());
        }
        if (bidUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error(ResultCodeEnum.BID_ILLEGAL.getMessage());
            throw new RuntimeException(ResultCodeEnum.BID_ILLEGAL.getMessage());
        }
        BigDecimal filledAmount = new BigDecimal(usdkMatchedOrderDTO.getFilledAmount());
        BigDecimal filledPrice = new BigDecimal(usdkMatchedOrderDTO.getFilledPrice());
        askUsdkOrder.setStatus(usdkMatchedOrderDTO.getAskOrderStatus());
        int updateAskOrderRet = updateSingleOrderByFilledAmount(askUsdkOrder, filledAmount, usdkMatchedOrderDTO.getFilledPrice());
        log.info("updateAskOrderRet----------------------------"+updateAskOrderRet);
        if (updateAskOrderRet <= 0){
            log.error("update ask order failed");
            throw new RuntimeException(ResultCodeEnum.UPDATE_USDK_ORDER_FAILED.getMessage());
        }
        bidUsdkOrder.setStatus(usdkMatchedOrderDTO.getBidOrderStatus());
        int updateBIdOrderRet = updateSingleOrderByFilledAmount(bidUsdkOrder, filledAmount, usdkMatchedOrderDTO.getFilledPrice());
        log.info("updateBIdOrderRet----------------------------"+updateBIdOrderRet);
        if (updateBIdOrderRet <= 0){
            log.error("update bid order failed");
            throw new RuntimeException(ResultCodeEnum.UPDATE_USDK_ORDER_FAILED.getMessage());
        }
        // todo 买币 bid +totalAsset = filledAmount - filledAmount * feeRate
        // todo 买币 bid -totalUsdk = filledAmount * filledPrice
        // todo 买币 bid -lockedUsdk = filledAmount * bidOrderPrice
        // todo 卖币 ask +totalUsdk = filledAmount * filledPrice - filledAmount * filledPrice * feeRate
        // todo 卖币 ask -lockedAsset = filledAmount
        // todo 卖币 ask -totalAsset = filledAmount
        BigDecimal addBidTotalAsset = filledAmount.subtract(BigDecimal.ZERO);
        BigDecimal addTotalUsdk = filledAmount.multiply(filledPrice);
        BigDecimal addLockedUsdk = filledAmount.multiply(new BigDecimal(usdkMatchedOrderDTO.getBidOrderPrice()));
        BigDecimal addAskTotalUsdk = filledAmount.multiply(filledPrice);
        BigDecimal addLockedAsset = filledAmount;
        BigDecimal addTotalAsset = filledAmount;
        BalanceTransferDTO balanceTransferDTO = new BalanceTransferDTO();
        balanceTransferDTO.setBidTotalAsset(addBidTotalAsset.toString());
        balanceTransferDTO.setBidTotalUsdk(addTotalUsdk.toString());
        balanceTransferDTO.setBidLockedUsdk(addLockedUsdk.toString());
        balanceTransferDTO.setAskTotalUsdk(addAskTotalUsdk.toString());
        balanceTransferDTO.setAskLockedAsset(addLockedAsset.toString());
        balanceTransferDTO.setAskTotalAsset(addTotalAsset.toString());
        balanceTransferDTO.setAssetId(usdkMatchedOrderDTO.getAssetId());
        balanceTransferDTO.setAskUserId(askUsdkOrder.getUserId());
        balanceTransferDTO.setBidUserId(bidUsdkOrder.getUserId());

        boolean updateRet = false;
        updateRet = getCapitalService().updateBalance(balanceTransferDTO);
        if (!updateRet) {
            log.error(ResultCodeEnum.ORDER_MATCH_UPDATE_BALANCE_FAILED.getMessage());
            throw new RuntimeException(ResultCodeEnum.ORDER_MATCH_UPDATE_BALANCE_FAILED.getMessage());
        }

        UsdkMatchedOrderDO usdkMatchedOrderDO = com.fota.trade.common.BeanUtils.copy(usdkMatchedOrderDTO);
        usdkMatchedOrderDO.setAskUserId(askUsdkOrder.getUserId());
        usdkMatchedOrderDO.setBidUserId(bidUsdkOrder.getUserId());
        usdkMatchedOrderDO.setAskCloseType(new Byte("0"));
        usdkMatchedOrderDO.setBidCloseType(new Byte("0"));
        usdkMatchedOrderDO.setGmtCreate(new Date());
        // 保存订单数据到数据库
        try {
            int ret = usdkMatchedOrder.insert(usdkMatchedOrderDO);
            if (ret < 1){
                log.error("保存usdk订单数据到数据库失败({})", usdkMatchedOrderDO);
                throw new RuntimeException(ResultCodeEnum.UPDATE_MATCH_ORDER_FAILED.getMessage());
            }
        } catch (Exception e) {
            log.error("保存USDK订单数据到数据库失败({})", usdkMatchedOrderDO, e);
        }
        //存Redis
        org.springframework.beans.BeanUtils.copyProperties(askUsdkOrder, askUsdkOrderDTO);
        org.springframework.beans.BeanUtils.copyProperties(bidUsdkOrder, bidUsdkOrderDTO);
        askUsdkOrderDTO.setCompleteAmount(usdkMatchedOrderDTO.getFilledAmount());
        bidUsdkOrderDTO.setCompleteAmount(usdkMatchedOrderDTO.getFilledAmount());
        askUsdkOrderDTO.setStatus(usdkMatchedOrderDTO.getAskOrderStatus());
        bidUsdkOrderDTO.setStatus(usdkMatchedOrderDTO.getAskOrderStatus());
        redisManager.usdkOrderSave(askUsdkOrderDTO);
        redisManager.usdkOrderSave(bidUsdkOrderDTO);
        // 向MQ推送消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setSubjectId(usdkMatchedOrderDTO.getAssetId().longValue());
        orderMessage.setSubjectName(usdkMatchedOrderDTO.getAssetName());
        orderMessage.setTransferTime(System.currentTimeMillis());
        orderMessage.setPrice(new BigDecimal(usdkMatchedOrderDTO.getFilledPrice()));
        orderMessage.setAmount(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()));
        orderMessage.setEvent(OrderOperateTypeEnum.DEAL_ORDER.getCode());
        orderMessage.setAskOrderId(usdkMatchedOrderDTO.getAskOrderId());
        orderMessage.setBidOrderId(usdkMatchedOrderDTO.getBidOrderId());
        orderMessage.setAskUserId(askUsdkOrder.getUserId());
        orderMessage.setBidUserId(bidUsdkOrder.getUserId());
        Boolean sendRet = rocketMqManager.sendMessage("order", "UsdkOrder", orderMessage);
        if (!sendRet){
            log.error("Send RocketMQ Message Failed ");
        }
        log.info("========完成撮合({})=======", System.currentTimeMillis());
        return ResultCode.success();
    }


    private int updateSingleOrderByFilledAmount(UsdkOrderDO usdkOrderDO, BigDecimal filledAmount, String filledPrice) {
        int ret = -1;
        //todo update 均价
        try {
            log.info("打印的内容----------------------"+usdkOrderDO);
            BigDecimal averagePrice = PriceUtil.getAveragePrice(usdkOrderDO.getAveragePrice(),
                    usdkOrderDO.getTotalAmount().subtract(usdkOrderDO.getUnfilledAmount()),
                    filledAmount,
                    new BigDecimal(filledPrice));
            ret  = usdkOrderMapper.updateByFilledAmount(usdkOrderDO.getId(), usdkOrderDO.getStatus(), filledAmount, averagePrice);
            if (ret >0){
                UsdkOrderDO usdkOrderDO2 = usdkOrderMapper.selectByPrimaryKey(usdkOrderDO.getId());
                log.info("更新后的记录"+usdkOrderDO.getId()+":"+usdkOrderDO2);
                if (usdkOrderDO2.getUnfilledAmount().compareTo(BigDecimal.ZERO) == 0){
                    usdkOrderDO2.setStatus(OrderStatusEnum.MATCH.getCode());
                    usdkOrderMapper.updateStatus(usdkOrderDO2);
                }
            }
        }catch (Exception e){
            log.error(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
            throw new RuntimeException(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
        }
        return ret;
    }

}



