package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.BalanceTransferDTO;
import com.fota.asset.domain.UserCapitalDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.CapitalService;
import com.fota.match.domain.TradeUsdkOrder;
import com.fota.match.domain.UsdkMatchedOrderTradeDTO;
import com.fota.match.service.UsdkMatchedOrderService;
import com.fota.trade.common.BusinessException;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.*;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.UsdkMatchedOrderMapper;
import com.fota.trade.mapper.UsdkOrderMapper;
import com.fota.trade.util.CommonUtils;
import com.fota.trade.util.PriceUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;


/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/7
 * @Modified:
 */

@Component
@Slf4j
public class UsdkOrderManager {

    private static final Logger tradeLog = LoggerFactory.getLogger("trade");

    private static BigDecimal usdkFee = BigDecimal.valueOf(0);

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Resource
    private UsdkMatchedOrderMapper usdkMatchedOrderMapper;

    @Resource
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

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
    public com.fota.common.Result<Long> placeOrder(UsdkOrderDTO usdkOrderDTO, Map<String, String> userInfoMap)throws Exception {
        String username = StringUtils.isEmpty(userInfoMap.get("username")) ? "" : userInfoMap.get("username");
        String ipAddress = StringUtils.isEmpty(userInfoMap.get("ipAddress")) ? "" : userInfoMap.get("ipAddress");
        com.fota.common.Result<Long> result = new com.fota.common.Result<Long>();
        UsdkOrderDO usdkOrderDO = com.fota.trade.common.BeanUtils.copy(usdkOrderDTO);
        Map<String, Object> newMap = new HashMap<>();
        if (usdkOrderDTO.getOrderContext() !=null){
            newMap = usdkOrderDTO.getOrderContext();
        }
        newMap.put("username", username);
        usdkOrderDTO.setOrderContext(newMap);
        usdkOrderDO.setOrderContext(JSONObject.toJSONString(usdkOrderDTO.getOrderContext()));
        Long orderId = usdkOrderDO.getId();
        ResultCode resultCode = new ResultCode();
        Integer assetId = usdkOrderDO.getAssetId();
        Long userId = usdkOrderDO.getUserId();
        Integer orderDirection = usdkOrderDO.getOrderDirection();
        List<UserCapitalDTO> list = getAssetService().getUserCapital(userId);
        usdkOrderDO.setFee(usdkFee);
        usdkOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        usdkOrderDO.setUnfilledAmount(usdkOrderDO.getTotalAmount());
        Long transferTime = System.currentTimeMillis();
        usdkOrderDO.setGmtModified(new Date(transferTime));
        if(usdkOrderDO.getOrderType() == null){
            usdkOrderDO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        }
        if (usdkOrderDO.getOrderType() != OrderTypeEnum.ENFORCE.getCode()){
            BigDecimal price = usdkOrderDO.getPrice();
            BigDecimal totalAmount = usdkOrderDO.getTotalAmount();
            BigDecimal orderValue = totalAmount.multiply(price);
            usdkOrderDO.setOrderType(OrderTypeEnum.LIMIT.getCode());
            //插入委托订单记录
            int ret = insertUsdkOrder(usdkOrderDO);
            if (ret <= 0){
                log.error("insert contractOrder failed");
                throw new RuntimeException("insert contractOrder failed");
            }
            orderId = usdkOrderDO.getId();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                //查询usdk账户可用余额
                for(UserCapitalDTO userCapitalDTO : list){
                    if (userCapitalDTO.getAssetId() == AssetTypeEnum.USDT.getCode()){
                        BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                        BigDecimal lockedAmount = new BigDecimal(userCapitalDTO.getLockedAmount());
                        BigDecimal availableAmount = amount.subtract(lockedAmount);
                        //判断账户可用余额是否大于orderValue
                        if (availableAmount.compareTo(orderValue) >= 0){
                            Date gmtModified = userCapitalDTO.getGmtModified();
                            Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,
                                    userCapitalDTO.getAssetId(), String.valueOf(orderValue), gmtModified.getTime());
                            if (!updateLockedAmountRet){
                                log.error("getCapitalService().updateLockedAmount failed{}", usdkOrderDO);
                                throw new RuntimeException("getCapitalService().updateLockedAmount failed");
                            }
                        }else {
                            throw new BusinessException(ResultCodeEnum.USDT_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(), ResultCodeEnum.USDT_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
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
                                log.error("getCapitalService().updateLockedAmount failed{}", usdkOrderDO);
                                throw new RuntimeException("getCapitalService().updateLockedAmount failed");
                            }
                        }else {
                            throw new BusinessException(ResultCodeEnum.COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(), ResultCodeEnum.COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
                        }
                    }
                }
            }
        } else if (usdkOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()){
            //强平单处理
            int ret = insertUsdkOrder(usdkOrderDO);
            if (ret <= 0){
                log.error("insert contractOrder failed");
                throw new RuntimeException("insert contractOrder failed");
            }
            orderId = usdkOrderDO.getId();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
        }
        usdkOrderDTO.setCompleteAmount(BigDecimal.ZERO);
        redisManager.usdkOrderSave(usdkOrderDTO);
        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                1, usdkOrderDTO.getAssetName(), username, ipAddress, usdkOrderDTO.getTotalAmount(), transferTime, 1, usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getUserId(), 1);
        //todo 发送RocketMQ
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setOrderId(usdkOrderDO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
        orderMessage.setUserId(usdkOrderDTO.getUserId());
        orderMessage.setSubjectId(usdkOrderDTO.getAssetId().longValue());
        orderMessage.setSubjectName(usdkOrderDTO.getAssetName());
        orderMessage.setAmount(usdkOrderDO.getTotalAmount());
        orderMessage.setOrderDirection(usdkOrderDO.getOrderDirection());
        orderMessage.setOrderType(usdkOrderDO.getOrderType());
        if (!usdkOrderDO.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode())){
            orderMessage.setPrice(usdkOrderDO.getPrice());
        }
        orderMessage.setTransferTime(transferTime);
        Boolean sendRet = rocketMqManager.sendMessage("order", "UsdkOrder", String.valueOf(usdkOrderDO.getId()), orderMessage);
        if (!sendRet){
            log.error("Send RocketMQ Message Failed ");
        }
        result.setCode(0);
        result.setMessage("success");
        result.setData(orderId);
        return result;
    }

    private int insertUsdkOrder(UsdkOrderDO usdkOrderDO) {
        usdkOrderDO.setId(CommonUtils.generateId());
        return usdkOrderMapper.insert(usdkOrderDO);
    }

    @Transactional(rollbackFor={RuntimeException.class, Exception.class, BusinessException.class})
    public ResultCode cancelOrder(Long userId, Long orderId, Map<String, String> userInfoMap) throws Exception{
        //需要调用match
        ResultCode resultCode = new ResultCode();
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByIdAndUserId(orderId, userId);
        UsdkOrderDO usdkOrderDOUpdate = usdkOrderDO;
        Integer status = usdkOrderDOUpdate.getStatus();
        boolean judegRet = getJudegRet(usdkOrderDOUpdate);
        if (!judegRet){
            resultCode = ResultCode.error(ResultCodeEnum.ORDER_CAN_NOT_CANCLE.getCode(),ResultCodeEnum.ORDER_CAN_NOT_CANCLE.getMessage());
            return resultCode;
        }
        if (status == OrderStatusEnum.COMMIT.getCode()){
            usdkOrderDOUpdate.setStatus(OrderStatusEnum.CANCEL.getCode());
        }else if (status == OrderStatusEnum.PART_MATCH.getCode()){
            usdkOrderDOUpdate.setStatus(OrderStatusEnum.PART_CANCEL.getCode());
        }else if (status == OrderStatusEnum.MATCH.getCode() || status == OrderStatusEnum.PART_CANCEL.getCode()  | status == OrderStatusEnum.CANCEL.getCode()){
            log.error("order has completed{}", usdkOrderDOUpdate);
            throw new RuntimeException("order has completed");
        }else {
            log.error("order status illegal{}", usdkOrderDOUpdate);
            throw new RuntimeException("order status illegal");
        }

        Long transferTime = System.currentTimeMillis();
        log.info("------------usdkCancelStartTimeStamp"+System.currentTimeMillis());
        //更新usdk委托表
        int ret = usdkOrderMapper.updateByOpLock(usdkOrderDOUpdate);
        log.info("------------usdkCancelEndTimeStamp"+System.currentTimeMillis());
        if (ret > 0){
            Integer orderDirection = usdkOrderDOUpdate.getOrderDirection();
            Integer assetId = 0;
            BigDecimal unlockAmount = BigDecimal.ZERO;
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                assetId = AssetTypeEnum.USDT.getCode();
                BigDecimal unfilledAmount = usdkOrderDOUpdate.getUnfilledAmount();
                BigDecimal price = usdkOrderDOUpdate.getPrice();
                unlockAmount = unfilledAmount.multiply(price);
                //解冻USDK钱包账户
                Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,AssetTypeEnum.USDT.getCode(),unlockAmount.negate().toString(), 0L);
                if (!updateLockedAmountRet){
                    log.error("getCapitalService().updateLockedAmount failed{}", usdkOrderDOUpdate);
                    throw new RuntimeException("getCapitalService().updateLockedAmount failed");
                }
            }else if (orderDirection == OrderDirectionEnum.ASK.getCode()){
                assetId = usdkOrderDOUpdate.getAssetId();
                unlockAmount = usdkOrderDOUpdate.getUnfilledAmount();
                //解冻Coin钱包账户
                Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,assetId,unlockAmount.negate().toString(), 0L);
                if (!updateLockedAmountRet){
                    log.error("getCapitalService().updateLockedAmount failed{}", usdkOrderDOUpdate);
                    throw new RuntimeException("getCapitalService().updateLockedAmount failed");
                }
            }
            UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
            BeanUtils.copyProperties(usdkOrderDOUpdate,usdkOrderDTO);
            BigDecimal matchAmount = usdkOrderDTO.getTotalAmount().subtract(usdkOrderDTO.getUnfilledAmount());
            usdkOrderDTO.setCompleteAmount(matchAmount);
            redisManager.usdkOrderSave(usdkOrderDTO);
            String username = StringUtils.isEmpty(userInfoMap.get("username")) ? "" : userInfoMap.get("username");
            String ipAddress = StringUtils.isEmpty(userInfoMap.get("ipAddress")) ? "" : userInfoMap.get("ipAddress");
            tradeLog.info("cancelorder@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    1, usdkOrderDTO.getAssetName(), username, ipAddress, usdkOrderDTO.getUnfilledAmount(), System.currentTimeMillis(), 2,  usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getUserId(), 1);
            OrderMessage orderMessage = new OrderMessage();
            orderMessage.setOrderId(usdkOrderDTO.getId());
            orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
            orderMessage.setUserId(usdkOrderDTO.getUserId());
            orderMessage.setSubjectId(usdkOrderDTO.getAssetId().longValue());
            orderMessage.setSubjectName(usdkOrderDTO.getAssetName());
            orderMessage.setAmount(usdkOrderDOUpdate.getTotalAmount());
            orderMessage.setPrice(usdkOrderDOUpdate.getPrice());
            orderMessage.setOrderType(usdkOrderDOUpdate.getOrderType());
            orderMessage.setTransferTime(transferTime);
            orderMessage.setOrderDirection(orderDirection);
            Boolean sendRet = rocketMqManager.sendMessage("order", "UsdkOrder", String.valueOf(usdkOrderDTO.getUserId())+usdkOrderDTO.getStatus(), orderMessage);
            if (!sendRet){
                log.error("Send RocketMQ Message Failed ");
            }
            resultCode = ResultCode.success();
        }else {
            log.error("usdkOrderMapper.updateByOpLock failed{}", usdkOrderDOUpdate);
            throw new RuntimeException("usdkOrderMapper.updateByOpLock failed");
        }
        return resultCode;
    }

    public boolean getJudegRet(UsdkOrderDO usdkOrderDO){
        TradeUsdkOrder tradeUsdkOrder = new TradeUsdkOrder();
        tradeUsdkOrder.setAssetId(usdkOrderDO.getAssetId());
        tradeUsdkOrder.setAssetName(usdkOrderDO.getAssetName());
        tradeUsdkOrder.setOrderDirection(usdkOrderDO.getOrderDirection());
        tradeUsdkOrder.setTotalAmount(usdkOrderDO.getTotalAmount());
        tradeUsdkOrder.setUnfilledAmount(usdkOrderDO.getUnfilledAmount());
        tradeUsdkOrder.setPrice(usdkOrderDO.getPrice());
        tradeUsdkOrder.setStatus(usdkOrderDO.getStatus());
        tradeUsdkOrder.setId(usdkOrderDO.getId());
        return usdkMatchedOrderService.cancelOrderUsdk(tradeUsdkOrder);
    }


    public ResultCode cancelAllOrder(Long userId, Map<String, String> userInfoMap) throws Exception{
        ResultCode resultCode = new ResultCode();
        List<UsdkOrderDO> list = usdkOrderMapper.selectUnfinishedOrderByUserId(userId);
        int i = 0;
        if (list != null){
            for(UsdkOrderDO usdkOrderDO : list){
                if (usdkOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode() || usdkOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode()) {
                    Long orderId = usdkOrderDO.getId();
                    try {
                        ResultCode resultCode2 =cancelOrder(userId, orderId, userInfoMap);
                        if (resultCode2.getCode() == 0){
                            i++;
                        }
                    }catch (Exception e){
                        log.error("cancelAllOrder has failed",usdkOrderDO,e);
                    }
                }
            }
        }
        if (i == 0){
            resultCode = ResultCode.error(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getCode(), ResultCodeEnum.NO_CANCELLABLE_ORDERS.getMessage());
            return resultCode;
        }
        if (i != list.size()){
            resultCode = ResultCode.error(ResultCodeEnum.PARTLY_COMPLETED.getCode(), ResultCodeEnum.PARTLY_COMPLETED.getMessage());
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
        Long transferTime = System.currentTimeMillis();
        UsdkOrderDTO bidUsdkOrderDTO = new UsdkOrderDTO();
        UsdkOrderDTO askUsdkOrderDTO = new UsdkOrderDTO();
        UsdkOrderDO askUsdkOrder = usdkOrderMapper.selectByPrimaryKey(usdkMatchedOrderDTO.getAskOrderId());
        UsdkOrderDO bidUsdkOrder = usdkOrderMapper.selectByPrimaryKey(usdkMatchedOrderDTO.getBidOrderId());
        Map<String, Object> askOrderContext = new HashMap<>();
        Map<String, Object> bidOrderContext = new HashMap<>();
        if (askUsdkOrder.getOrderContext() != null){
            askOrderContext  = JSON.parseObject(askUsdkOrder.getOrderContext());
        }
        if (bidUsdkOrder.getOrderContext() != null){
            bidOrderContext  = JSON.parseObject(bidUsdkOrder.getOrderContext());
        }
        if (askUsdkOrder.getUnfilledAmount().compareTo(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount())) < 0
                || bidUsdkOrder.getUnfilledAmount().compareTo(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount())) < 0){
            log.error("unfilledAmount not enough{}",usdkMatchedOrderDTO);
            throw new RuntimeException("unfilledAmount not enough");
        }
        if (askUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error("ask order status illegal{}", askUsdkOrder);
            throw new RuntimeException("ask order status illegal");
        }
        if (bidUsdkOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidUsdkOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error("bid order status illegal{}", bidUsdkOrder);
            throw new RuntimeException("bid order status illegal");
        }
        BigDecimal filledAmount = new BigDecimal(usdkMatchedOrderDTO.getFilledAmount());
        BigDecimal filledPrice = new BigDecimal(usdkMatchedOrderDTO.getFilledPrice());
        askUsdkOrder.setStatus(usdkMatchedOrderDTO.getAskOrderStatus());
        int updateAskOrderRet = updateSingleOrderByFilledAmount(askUsdkOrder, filledAmount, usdkMatchedOrderDTO.getFilledPrice(), new Date(transferTime));
        if (updateAskOrderRet <= 0){
            log.error("update ask order failed{}", askUsdkOrder);
            throw new RuntimeException("update ask order failed");
        }
        bidUsdkOrder.setStatus(usdkMatchedOrderDTO.getBidOrderStatus());
        int updateBIdOrderRet = updateSingleOrderByFilledAmount(bidUsdkOrder, filledAmount, usdkMatchedOrderDTO.getFilledPrice(), new Date(transferTime));
        if (updateBIdOrderRet <= 0){
            log.error("update bid order failed{}", bidUsdkOrder);
            throw new RuntimeException("update bid order failed");
        }
        // 买币 bid +totalAsset = filledAmount - filledAmount * feeRate
        // 买币 bid -totalUsdk = filledAmount * filledPrice
        // 买币 bid -lockedUsdk = filledAmount * bidOrderPrice
        // 卖币 ask +totalUsdk = filledAmount * filledPrice - filledAmount * filledPrice * feeRate
        // 卖币 ask -lockedAsset = filledAmount
        // 卖币 ask -totalAsset = filledAmount
        BigDecimal addBidTotalAsset = filledAmount.subtract(BigDecimal.ZERO);
        BigDecimal addTotalUsdk = filledAmount.multiply(filledPrice);
        BigDecimal addLockedUsdk = filledAmount.multiply(new BigDecimal(usdkMatchedOrderDTO.getBidOrderPrice()));
        BigDecimal addAskTotalUsdk = filledAmount.multiply(filledPrice);
        BigDecimal addLockedAsset = filledAmount;
        BigDecimal addTotalAsset = filledAmount;
        BalanceTransferDTO balanceTransferDTO = new BalanceTransferDTO();
        balanceTransferDTO.setAskUserId(askUsdkOrder.getUserId());
        balanceTransferDTO.setBidUserId(bidUsdkOrder.getUserId());
        if (askUsdkOrder.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode())){
            balanceTransferDTO.setAskTotalUsdk("0");
            balanceTransferDTO.setAskLockedAsset("0");
            balanceTransferDTO.setAskTotalAsset("0");
        }else {
            balanceTransferDTO.setAskTotalUsdk(addAskTotalUsdk.toString());
            balanceTransferDTO.setAskLockedAsset(addLockedAsset.toString());
            balanceTransferDTO.setAskTotalAsset(addTotalAsset.toString());
        }
        if (bidUsdkOrder.getOrderType().equals(OrderTypeEnum.ENFORCE.getCode())){
            balanceTransferDTO.setBidTotalAsset("0");
            balanceTransferDTO.setBidTotalUsdk("0");
            balanceTransferDTO.setBidLockedUsdk("0");
        }else {
            balanceTransferDTO.setBidTotalAsset(addBidTotalAsset.toString());
            balanceTransferDTO.setBidTotalUsdk(addTotalUsdk.toString());
            balanceTransferDTO.setBidLockedUsdk(addLockedUsdk.toString());
        }
        balanceTransferDTO.setAssetId(usdkMatchedOrderDTO.getAssetId());
        boolean updateRet = false;
        updateRet = getCapitalService().updateBalance(balanceTransferDTO);
        if (!updateRet) {
            log.error("getCapitalService().updateBalance failed{}", balanceTransferDTO);
            throw new RuntimeException("getCapitalService().updateBalance failed{}");
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
                throw new RuntimeException("usdkMatchedOrder.insert failed{}");
            }
        } catch (Exception e) {
            log.error("保存USDK订单数据到数据库失败({})", usdkMatchedOrderDO, e);
            throw new RuntimeException("usdkMatchedOrder.insert exception{}",e);
        }
        //存Redis
        org.springframework.beans.BeanUtils.copyProperties(askUsdkOrder, askUsdkOrderDTO);
        org.springframework.beans.BeanUtils.copyProperties(bidUsdkOrder, bidUsdkOrderDTO);
        askUsdkOrderDTO.setOrderContext(askOrderContext);
        bidUsdkOrderDTO.setOrderContext(bidOrderContext);
        askUsdkOrderDTO.setCompleteAmount(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()));
        bidUsdkOrderDTO.setCompleteAmount(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()));
        askUsdkOrderDTO.setStatus(usdkMatchedOrderDTO.getAskOrderStatus());
        bidUsdkOrderDTO.setStatus(usdkMatchedOrderDTO.getAskOrderStatus());
        redisManager.usdkOrderSave(askUsdkOrderDTO);
        String askUsername = "";
        String bidUsername = "";
        if (askOrderContext != null){
            askUsername = askOrderContext.get("username") == null ? "": String.valueOf(askOrderContext.get("username"));
        }
        if (bidOrderContext != null){
            bidUsername = bidOrderContext.get("username") == null ? "": String.valueOf(bidOrderContext.get("username"));
        }
        // TODO add get username
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                1, askUsdkOrderDTO.getAssetName(), askUsername, askUsdkOrderDTO.getMatchAmount(), System.currentTimeMillis(), 4, askUsdkOrderDTO.getOrderDirection(), askUsdkOrderDTO.getUserId(), 1);
        redisManager.usdkOrderSave(bidUsdkOrderDTO);
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                1, bidUsdkOrderDTO.getAssetName(), bidUsername, bidUsdkOrderDTO.getMatchAmount(), System.currentTimeMillis(), 4,  bidUsdkOrderDTO.getOrderDirection(), bidUsdkOrderDTO.getUserId(), 1);
        // 向MQ推送消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setSubjectId(usdkMatchedOrderDTO.getAssetId().longValue());
        orderMessage.setSubjectName(usdkMatchedOrderDTO.getAssetName());
        orderMessage.setTransferTime(transferTime);
        orderMessage.setPrice(new BigDecimal(usdkMatchedOrderDTO.getFilledPrice()));
        orderMessage.setAmount(new BigDecimal(usdkMatchedOrderDTO.getFilledAmount()));
        orderMessage.setEvent(OrderOperateTypeEnum.DEAL_ORDER.getCode());
        orderMessage.setAskOrderId(usdkMatchedOrderDTO.getAskOrderId());
        orderMessage.setBidOrderId(usdkMatchedOrderDTO.getBidOrderId());
        orderMessage.setAskOrderContext(askOrderContext);
        orderMessage.setBidOrderContext(bidOrderContext);
        orderMessage.setAskOrderType(askUsdkOrder.getOrderType());
        orderMessage.setBidOrderType(bidUsdkOrder.getOrderType());
        if (askUsdkOrder.getPrice() != null){
            orderMessage.setAskOrderEntrustPrice(askUsdkOrder.getPrice());
        }
        if (bidUsdkOrder.getPrice() != null){
            orderMessage.setBidOrderEntrustPrice(bidUsdkOrder.getPrice());
        }
        orderMessage.setAskUserId(askUsdkOrder.getUserId());
        orderMessage.setBidUserId(bidUsdkOrder.getUserId());
        orderMessage.setMatchOrderId(usdkMatchedOrderDO.getId());
        Boolean sendRet = rocketMqManager.sendMessage("match", "usdk", String.valueOf(usdkMatchedOrderDO.getId()), orderMessage);
        if (!sendRet){
            log.error("Send RocketMQ Message Failed ");
        }

        // 向Redis存储消息
        UsdkMatchedOrderTradeDTO usdkMatchedOrderTradeDTO = new UsdkMatchedOrderTradeDTO();
        usdkMatchedOrderTradeDTO.setUsdkMatchedOrderId(usdkMatchedOrderDO.getId());
        usdkMatchedOrderTradeDTO.setAskOrderId(usdkMatchedOrderDO.getAskOrderId());
        usdkMatchedOrderTradeDTO.setBidOrderId(usdkMatchedOrderDO.getBidOrderId());
        usdkMatchedOrderTradeDTO.setFilledPrice(usdkMatchedOrderDO.getFilledPrice());
        usdkMatchedOrderTradeDTO.setFilledAmount(usdkMatchedOrderDO.getFilledAmount());
        usdkMatchedOrderTradeDTO.setFilledDate(usdkMatchedOrderDO.getGmtCreate());
        usdkMatchedOrderTradeDTO.setMatchType(usdkMatchedOrderDO.getMatchType().intValue());
        usdkMatchedOrderTradeDTO.setAssetId(bidUsdkOrder.getAssetId().longValue());
        usdkMatchedOrderTradeDTO.setAssetName(usdkMatchedOrderDO.getAssetName());
        try {
            String key = Constant.CACHE_KEY_MATCH_USDK + usdkMatchedOrderDO.getId();
            Object value = JSONObject.toJSONString(usdkMatchedOrderTradeDTO);
            log.info("向Redis存储消息,key:{},value:{}", key, value);
            boolean re = redisManager.set(key,value);
            if (!re){
                log.error("向Redis存储消息失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("向Redis存储USDK撮合订单信息失败，订单id为 {}", usdkMatchedOrderDO.getId());
        }
        return ResultCode.success();
    }


    private int updateSingleOrderByFilledAmount(UsdkOrderDO usdkOrderDO, BigDecimal filledAmount, String filledPrice, Date gmtModified) {
        int ret = -1;
        //todo update 均价
        try {
            log.info("---------------------usdkOrderDO"+usdkOrderDO);
            log.info("---------------------"+filledAmount);
            log.info("---------------------"+filledPrice);
            BigDecimal averagePrice = PriceUtil.getAveragePrice(usdkOrderDO.getAveragePrice(),
                    usdkOrderDO.getTotalAmount().subtract(usdkOrderDO.getUnfilledAmount()),
                    filledAmount,
                    new BigDecimal(filledPrice));
            ret  = usdkOrderMapper.updateByFilledAmount(usdkOrderDO.getId(), usdkOrderDO.getStatus(), filledAmount, averagePrice, gmtModified);
            if (ret >0){
                UsdkOrderDO usdkOrderDO2 = usdkOrderMapper.selectByPrimaryKey(usdkOrderDO.getId());
                if (usdkOrderDO2.getUnfilledAmount().compareTo(BigDecimal.ZERO) == 0){
                    usdkOrderDO2.setStatus(OrderStatusEnum.MATCH.getCode());
                    usdkOrderMapper.updateStatus(usdkOrderDO2);
                }else if (!usdkOrderDO2.getUnfilledAmount().equals(0L) && usdkOrderDO2.getStatus() == OrderStatusEnum.MATCH.getCode()) {
                    usdkOrderDO2.setStatus(OrderStatusEnum.PART_MATCH.getCode());
                    usdkOrderMapper.updateStatus(usdkOrderDO2);
                }
            }
        }catch (Exception e){
            log.error(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
            throw new RuntimeException(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
        }
        return ret;
    }

    public Long getLatestUsdkMatched (Integer type) {
        try {
            if (type == 1) {
                UsdkMatchedOrderDO latestUsdkMatched = usdkMatchedOrderMapper.getLatestUsdkMatched();
                if (latestUsdkMatched != null) {
                    return latestUsdkMatched.getId();
                }
            }else if (type ==2) {
                ContractMatchedOrderDO latestContractMatched = contractMatchedOrderMapper.getLatestContractMatched();
                if (latestContractMatched != null) {
                    return latestContractMatched.getId();
                }
            }
        } catch (Exception e) {
            log.error("usdkMatchedOrderMapper.getLatestUsdkMatched error" ,e);
        }
        return null;
    }
}



