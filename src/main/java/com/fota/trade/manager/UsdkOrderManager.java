package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.BalanceTransferDTO;
import com.fota.asset.domain.UserCapitalDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.CapitalService;
import com.fota.match.service.UsdkMatchedOrderService;
import com.fota.trade.client.constants.Constants;
import com.fota.trade.client.constants.DealedMessage;
import com.fota.trade.common.BizException;
import com.fota.trade.common.BusinessException;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.*;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.UsdkMatchedOrderMapper;
import com.fota.trade.mapper.UsdkOrderMapper;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ContractUtils;
import com.fota.trade.util.ThreadContextUtil;
import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
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

import static com.fota.trade.client.constants.Constants.DEALED_TOPIC;
import static com.fota.trade.client.constants.Constants.DEALED_USDT_TAG;
import static com.fota.trade.common.ResultCodeEnum.BIZ_ERROR;
import static com.fota.trade.domain.enums.OrderStatusEnum.*;
import static java.util.stream.Collectors.toList;


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
                    COMMIT.getCode(), PART_MATCH.getCode(), contractOrderIndex, orderDirection);
        } catch (Exception e) {
            log.error("usdkOrderMapper.notMatchOrderList error", e);
        }
        if (notMatchOrderList == null) {
            notMatchOrderList = new ArrayList<>();
        }
        return notMatchOrderList;
    }

    //TODO 优化: 先更新账户，再insert订单，而不是先insert订单再更新账户
    @Transactional(rollbackFor={Throwable.class})
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
        usdkOrderDO.setStatus(COMMIT.getCode());
        usdkOrderDO.setUnfilledAmount(usdkOrderDO.getTotalAmount());
        long transferTime = System.currentTimeMillis();
        usdkOrderDO.setGmtCreate(new Date(transferTime));
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
            int assetTypeId = 0;
            BigDecimal entrustValue = BigDecimal.ZERO;
            int errorCode = 0;
            String errorMsg;
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                assetTypeId = AssetTypeEnum.USDT.getCode();
                entrustValue = orderValue;
                errorCode = ResultCodeEnum.USDT_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode();
                errorMsg = ResultCodeEnum.USDT_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage();
            }else {
                assetTypeId = assetId;
                entrustValue = usdkOrderDO.getTotalAmount();
                errorCode = ResultCodeEnum.COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode();
                errorMsg = ResultCodeEnum.COIN_CAPITAL_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage();
            }
            //查询账户可用余额
            for(UserCapitalDTO userCapitalDTO : list){
                if (userCapitalDTO.getAssetId() == assetTypeId){
                    BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                    BigDecimal lockedAmount = new BigDecimal(userCapitalDTO.getLockedAmount());
                    BigDecimal availableAmount = amount.subtract(lockedAmount);
                    //判断账户可用余额是否大于orderValue
                    if (availableAmount.compareTo(entrustValue) >= 0){
                        Date gmtModified = userCapitalDTO.getGmtModified();
                        try{
                            Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,
                                    userCapitalDTO.getAssetId(), String.valueOf(entrustValue), gmtModified.getTime());
                            if (!updateLockedAmountRet){
                                log.error("placeOrder getCapitalService().updateLockedAmount failed usdkOrderDO:{}", usdkOrderDO);
                                throw new BusinessException(errorCode, errorMsg);
                            }
                        }catch (Exception e){
                            log.error("Asset RPC Error!, placeOrder getCapitalService().updateLockedAmount exception usdkOrderDO:{}", usdkOrderDO, e);
                            throw new RuntimeException("placeOrder getCapitalService().updateLockedAmount exception");
                        }
                    }else {
                        throw new BusinessException(errorCode, errorMsg);
                    }
                }
            }
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    1, usdkOrderDTO.getAssetName(), username, ipAddress, usdkOrderDTO.getTotalAmount(), transferTime, 2, usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getUserId(), 1);
        } else if (usdkOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()){
            //强平单处理
            int ret = insertUsdkOrder(usdkOrderDO);
            if (ret <= 0){
                log.error("insert contractOrder failed");
                throw new RuntimeException("insert contractOrder failed");
            }
            orderId = usdkOrderDO.getId();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    1, usdkOrderDTO.getAssetName(), username, ipAddress, usdkOrderDTO.getTotalAmount(), transferTime, 3, usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getUserId(), 2);
        }
        usdkOrderDTO.setCompleteAmount(BigDecimal.ZERO);
        //消息一定要在事务外发，不然会出现收到下单消息，db还没有这个订单
       Runnable postTask = () -> {
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
       };
       ThreadContextUtil.setPostTask(postTask);
        result.setCode(0);
        result.setMessage("success");
        result.setData(orderId);
        return result;
    }

    private int insertUsdkOrder(UsdkOrderDO usdkOrderDO) {
        usdkOrderDO.setId(BasicUtils.generateId());
        return usdkOrderMapper.insert(usdkOrderDO);
    }

    public ResultCode cancelOrder(Long userId, Long orderId, Map<String, String> userInfoMap) throws Exception{
        ResultCode resultCode = ResultCode.success();
        if (Objects.isNull(userId) || Objects.isNull(orderId)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByPrimaryKey(orderId);
        if (Objects.isNull(usdkOrderDO)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        if (usdkOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            return ResultCode.error(ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getCode(),
                    ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getMessage());
        }
        List<Long> orderIdList = new ArrayList<Long>();
        orderIdList.add(orderId);
        sendCancelMessage(orderIdList, userId);
        return resultCode;
    }

    /**
     * 根据撮合发出的MQ消息撤单
     * @param orderId 委托单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultCode cancelOrderByMessage(Long orderId, BigDecimal unfilledAmount) {
        ResultCode resultCode;

        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByPrimaryKey(orderId);
        Integer status = usdkOrderDO.getStatus();

        if (Objects.isNull(usdkOrderDO)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "usdk order does not exist, id={}"+orderId);
        }
        Integer toStatus;
        if (status == COMMIT.getCode()){
            toStatus = CANCEL.getCode();
        }else if (status == PART_MATCH.getCode()){
            toStatus = PART_CANCEL.getCode();
        }else {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "order has completed, can't be cancel, id="+usdkOrderDO.getId() + "status="+status);
        }
        //更新usdk委托表
        int ret = usdkOrderMapper.cancelByOpLock(usdkOrderDO.getId(), toStatus, usdkOrderDO.getGmtModified());
        Long transferTime = System.currentTimeMillis();
        if (ret > 0){
            Integer orderDirection = usdkOrderDO.getOrderDirection();
            Integer assetId = 0;
            BigDecimal unlockAmount ;
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                assetId = AssetTypeEnum.USDT.getCode();
                BigDecimal price = usdkOrderDO.getPrice();
                unlockAmount = unfilledAmount.multiply(price);
            }else {
                assetId = usdkOrderDO.getAssetId();
                unlockAmount = unfilledAmount;
            }
            //解冻Coin钱包账户
            try{
                Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(usdkOrderDO.getUserId(),assetId,unlockAmount.negate().toString(), 0L);
                if (!updateLockedAmountRet){
                    log.error("cancelOrder getCapitalService().updateLockedAmount failed usdkOrderDO:{}", usdkOrderDO);
                    throw new BizException(BIZ_ERROR.getCode(),"cancelOrder getCapitalService().updateLockedAmount failed");
                }
            }catch (Exception e){
                log.error("Asset RPC Error!, cancelOrder getCapitalService().updateLockedAmount exception usdkOrderDO:{}", usdkOrderDO, e);
                throw new BizException(BIZ_ERROR.getCode(),"cancelOrder getCapitalService().updateLockedAmount exception");
            }
            UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
            BigDecimal matchAmount = usdkOrderDTO.getTotalAmount().subtract(unfilledAmount);
            usdkOrderDTO.setCompleteAmount(matchAmount);
            JSONObject jsonObject = JSONObject.parseObject(usdkOrderDO.getOrderContext());
            String username = "";
            if (jsonObject != null && !jsonObject.isEmpty()) {
                username = jsonObject.get("username") == null ? "" : jsonObject.get("username").toString();
            }
            String ipAddress = "";
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    1, usdkOrderDTO.getAssetName(), username, ipAddress, unfilledAmount, System.currentTimeMillis(), 1,  usdkOrderDTO.getOrderDirection(), usdkOrderDTO.getUserId(), 1);
            OrderMessage orderMessage = new OrderMessage();
            orderMessage.setOrderId(usdkOrderDTO.getId());
            orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
            orderMessage.setUserId(usdkOrderDTO.getUserId());
            orderMessage.setSubjectId(usdkOrderDTO.getAssetId().longValue());
            orderMessage.setSubjectName(usdkOrderDTO.getAssetName());
            orderMessage.setAmount(unfilledAmount);
            orderMessage.setPrice(usdkOrderDO.getPrice());
            orderMessage.setOrderType(usdkOrderDO.getOrderType());
            orderMessage.setTransferTime(transferTime);
            orderMessage.setOrderDirection(orderDirection);
            Boolean sendRet = rocketMqManager.sendMessage("order", "UsdkOrder", "usdk_doCanceled_"+ orderId, orderMessage);
            if (!sendRet){
                log.error("Send RocketMQ Message Failed ");
            }
            resultCode = ResultCode.success();
        }else {
            return ResultCode.error(ResultCodeEnum.BIZ_ERROR.getCode(), "usdkOrderMapper.updateByOpLock failed" + usdkOrderDO.getId());
        }
        return resultCode;
    }

    public void sendCancelMessage(List<Long> orderIdList, Long userId) {
        //发送MQ消息到match
        Map<String, Object> map = new HashMap<>();
        map.putIfAbsent("userId", userId);
        map.putIfAbsent("idList", orderIdList);
        Boolean sendRet = rocketMqManager.sendMessage("order", "UsdkCancel",
                "to_cancel_usdt_"+Joiner.on("_").join(orderIdList), map);
        if (BooleanUtils.isNotTrue(sendRet)){
            log.error("failed to send cancel usdk mq, {}", userId);
        }
    }


    public ResultCode cancelAllOrder(Long userId, Map<String, String> userInfoMap) throws Exception{
        if (Objects.isNull(userId)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }

        List<UsdkOrderDO> list = usdkOrderMapper.selectUnfinishedOrderByUserId(userId);
        if (list != null) {
            List<Long> orderIdList = list.stream()
                    .filter(usdkOrderDO -> usdkOrderDO.getOrderType() != OrderTypeEnum.ENFORCE.getCode())
                    .map(UsdkOrderDO::getId)
                    .collect(toList());
            sendCancelMessage(orderIdList, userId);
        }

        return ResultCode.success();
    }

    @Transactional(rollbackFor = Throwable.class)
    public ResultCode updateOrderByMatch(UsdkMatchedOrderDTO usdkMatchedOrderDTO) throws Exception {
        if (usdkMatchedOrderDTO == null) {
            log.error(ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "illegal usdkMatchedOrderDTO" + usdkMatchedOrderDTO);
        }
        Long transferTime = System.currentTimeMillis();

        UsdkOrderDO askUsdkOrder = usdkOrderMapper.selectByPrimaryKey(usdkMatchedOrderDTO.getAskOrderId());
        UsdkOrderDO bidUsdkOrder = usdkOrderMapper.selectByPrimaryKey(usdkMatchedOrderDTO.getBidOrderId());

        BigDecimal filledAmount = new BigDecimal(usdkMatchedOrderDTO.getFilledAmount());
        if (BasicUtils.gt(filledAmount, askUsdkOrder.getUnfilledAmount())){
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "askOrder unfilledAmount not enough. order="+askUsdkOrder);
        }
        if (BasicUtils.gt(filledAmount, bidUsdkOrder.getUnfilledAmount())){
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), "bidOrder unfilledAmount not enough. order="+bidUsdkOrder);
        }

        BigDecimal filledPrice = new BigDecimal(usdkMatchedOrderDTO.getFilledPrice());

        int updateAskOrderRet = doUpdateUsdkOrder(askUsdkOrder.getId(),  filledAmount, filledPrice, new Date(transferTime));
        if (updateAskOrderRet <= 0){
            throw new BizException(ResultCodeEnum.BIZ_ERROR.getCode(), "update askOrder failed, order=" + askUsdkOrder);
        }
        int updateBIdOrderRet = doUpdateUsdkOrder(bidUsdkOrder.getId(), filledAmount, filledPrice, new Date(transferTime));
        if (updateBIdOrderRet <= 0){
            throw new BizException(ResultCodeEnum.BIZ_ERROR.getCode(), "update bidOrder failed, order=" + bidUsdkOrder);
        }



        // 买币 bid +totalAsset = filledAmount - filledAmount * feeRate
        // 买币 bid -totalUsdk = filledAmount * filledPrice
        // 买币 bid -lockedUsdk = filledAmount * bidOrderPrice
        // 卖币 ask +totalUsdk = filledAmount * filledPrice - filledAmount * filledPrice * feeRate
        // 卖币 ask -lockedAsset = filledAmount
        // 卖币 ask -totalAsset = filledAmount
        BigDecimal addLockedUsdk = BigDecimal.ZERO;
        BigDecimal addBidTotalAsset = filledAmount.subtract(BigDecimal.ZERO);
        BigDecimal addTotalUsdk = filledAmount.multiply(filledPrice);
        if (usdkMatchedOrderDTO.getBidOrderPrice() != null){
            addLockedUsdk = filledAmount.multiply(new BigDecimal(usdkMatchedOrderDTO.getBidOrderPrice()));
        }
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
        try {
            updateRet = getCapitalService().updateBalance(balanceTransferDTO);
        }catch (Exception e){
            log.error("Asset RPC Error!, getCapitalService().updateBalance exception, balanceTransferDTO:{}", balanceTransferDTO, e);
            throw new BizException(BIZ_ERROR.getCode(), "getCapitalService().updateBalance exception, balanceTransferDTO:{}" + balanceTransferDTO);
        }
        if (!updateRet) {
            log.error("getCapitalService().updateBalance failed, balanceTransferDTO:{}", balanceTransferDTO);
            throw new BizException(BIZ_ERROR.getCode(), "getCapitalService().updateBalance failed, balanceTransferDTO:{}" + balanceTransferDTO);
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


        Runnable runnable = () -> {
            Map<String, Object> askOrderContext = new HashMap<>();
            Map<String, Object> bidOrderContext = new HashMap<>();
            if (askUsdkOrder.getOrderContext() != null){
                askOrderContext  = JSON.parseObject(askUsdkOrder.getOrderContext());
            }
            if (bidUsdkOrder.getOrderContext() != null){
                bidOrderContext  = JSON.parseObject(bidUsdkOrder.getOrderContext());
            }

            postProcessOrder(askUsdkOrder, filledAmount, usdkMatchedOrderDO.getId());
            postProcessOrder(bidUsdkOrder, filledAmount, usdkMatchedOrderDO.getId());

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
            orderMessage.setAskOrderUnfilledAmount(usdkMatchedOrderDTO.getAskOrderUnfilledAmount());
            orderMessage.setBidOrderUnfilledAmount(usdkMatchedOrderDTO.getBidOrderUnfilledAmount());
            orderMessage.setMatchType(usdkMatchedOrderDTO.getMatchType());
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

        };
        ThreadContextUtil.setPostTask(runnable);

        return ResultCode.success();
    }

    private void postProcessOrder(UsdkOrderDO usdkOrderDO, BigDecimal filledAmount, long matchId) {
        Map<String, Object> context = BasicUtils.exeWhitoutError(() -> JSON.parseObject(usdkOrderDO.getOrderContext()));
        String userName = null == context || null == context.get("username") ? "": String.valueOf(context.get("username"));
        int dir = ContractUtils.toDirection(usdkOrderDO.getOrderDirection());
        usdkOrderDO.fillAmount(filledAmount);
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                1, usdkOrderDO.getAssetName(), userName,filledAmount, System.currentTimeMillis(), 4,  usdkOrderDO.getOrderDirection(), usdkOrderDO.getUserId(), 1);

        DealedMessage dealedMessage = new DealedMessage();
        dealedMessage.setSubjectId(usdkOrderDO.getAssetId())
                .setSubjectType(DealedMessage.USDT_TYPE)
                .setUserId(usdkOrderDO.getUserId());
        rocketMqManager.sendMessage(DEALED_TOPIC, DEALED_USDT_TAG, matchId + "_" + usdkOrderDO.getId(), dealedMessage);
    }


    private int doUpdateUsdkOrder(long id, BigDecimal filledAmount, BigDecimal filledPrice, Date gmtModified) {
        return usdkOrderMapper.updateByFilledAmount(id, filledAmount, filledPrice, gmtModified);
    }

    public Long getLatestUsdkMatched(Integer type) {
        try {
            if (type == 1) {
                Long latestUsdkMatched = usdkMatchedOrderMapper.getLatestUsdkMatched();
                if (latestUsdkMatched != null) {
                    return latestUsdkMatched;
                }
            }else if (type ==2) {
                Long latestContractMatched = contractMatchedOrderMapper.getLatestContractMatched();
                if (latestContractMatched != null) {
                    return latestContractMatched;
                }
            }
        } catch (Exception e) {
            log.error("usdkMatchedOrderMapper.getLatestUsdkMatched error" ,e);
        }
        return null;
    }
}



