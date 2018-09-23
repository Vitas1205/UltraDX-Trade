package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.asset.domain.ContractDealer;
import com.fota.asset.service.ContractService;
import com.fota.common.utils.CommonUtils;
import com.fota.trade.client.PostDealMessage;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserContractLeverMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.ContractCategoryService;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ContractUtils;
import com.fota.trade.util.Profiler;
import com.fota.trade.util.ThreadContextUtil;
import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static com.fota.trade.client.constants.Constants.DEFAULT_TAG;
import static com.fota.trade.client.constants.Constants.POST_DEAL_TOPIC;
import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.common.ResultCodeEnum.BIZ_ERROR;
import static com.fota.trade.common.ResultCodeEnum.CONCURRENT_PROBLEM;
import static com.fota.trade.domain.enums.ContractStatusEnum.PROCESSING;
import static com.fota.trade.util.ContractUtils.computeAveragePrice;
import static org.springframework.transaction.annotation.Propagation.REQUIRED;

/**
 * Created by Swifree on 2018/9/22.
 * Code is the law
 */
@Component
@Slf4j
public class DealManager {

    private static final Logger tradeLog = LoggerFactory.getLogger("trade");


    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Autowired
    private ContractLeverManager contractLeverManager;

    @Resource
    private UserContractLeverMapper userContractLeverMapper;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private ContractCategoryService contractCategoryService;

    @Autowired
    private RocketMqManager rocketMqManager;

    @Autowired
    private ContractService contractService;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Resource
    private ConcurrentMap<String, String> failedBalanceMap;

    /**
     * 禁止通过内部非Transactional方法调用此方法，否则@Transactional注解会失效
     * @param contractMatchedOrderDTO
     * @return
     */
    @Transactional(rollbackFor = {Throwable.class}, isolation = Isolation.REPEATABLE_READ, propagation = REQUIRED)
    public ResultCode deal(ContractMatchedOrderDTO contractMatchedOrderDTO) {

        long contractId = contractMatchedOrderDTO.getContractId();
        ContractCategoryDTO contractCategoryDO = contractCategoryService.getContractById(contractId);
        if (null == contractCategoryDO){
            return ResultCode.error(BIZ_ERROR.getCode(), "null contractCategoryDO, id="+contractId);
        }
        if(!Objects.equals(PROCESSING.getCode(), contractCategoryDO.getStatus())) {
            return ResultCode.error(BIZ_ERROR.getCode(), "illegal contract status, id="+contractId + ", status="+contractCategoryDO.getStatus());
        }

        ContractOrderDO askContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getAskOrderId());
        ContractOrderDO bidContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getBidOrderId());

        ResultCode checkResult = checkParam(askContractOrder, bidContractOrder, contractMatchedOrderDTO);
        if (!checkResult.isSuccess()) {
            return checkResult;
        }

        //排序，防止死锁
        List<ContractOrderDO> contractOrderDOS = new ArrayList<>();
        contractOrderDOS.add(askContractOrder);
        contractOrderDOS.add(bidContractOrder);
        Collections.sort(contractOrderDOS, (a, b) -> {
            int c = a.getUserId().compareTo(b.getUserId());
            if (c!=0) {
                return c;
            }
            return a.getId().compareTo(b.getId());
        });
        Profiler profiler = null == ThreadContextUtil.getPrifiler()
                ? new Profiler("ContractOrderManager.updateOrderByMatch"):ThreadContextUtil.getPrifiler();

        ResultCode resultCode = new ResultCode();

        BigDecimal filledAmount = contractMatchedOrderDTO.getFilledAmount();
        BigDecimal filledPrice = new BigDecimal(contractMatchedOrderDTO.getFilledPrice());
        Long transferTime = System.currentTimeMillis();

        //更新委托
        contractOrderDOS.forEach(x -> {
            int lever = contractLeverManager.getLeverByContractId(x.getUserId(), x.getContractId());
            x.setLever(lever);
            x.fillAmount(filledAmount);
            updateContractOrder(x.getId(), filledAmount, filledPrice, new Date(transferTime));
        });
        profiler.complelete("update contract order");

        ContractMatchedOrderDO contractMatchedOrderDO = com.fota.trade.common.BeanUtils.copy(contractMatchedOrderDTO);
        BigDecimal fee = contractMatchedOrderDO.getFilledAmount().
                multiply(contractMatchedOrderDO.getFilledPrice()).
                multiply(Constant.FEE_RATE).
                setScale(CommonUtils.scale, BigDecimal.ROUND_UP);
        contractMatchedOrderDO.setFee(fee);
        contractMatchedOrderDO.setAskUserId(askContractOrder.getUserId());
        contractMatchedOrderDO.setBidUserId(bidContractOrder.getUserId());
        contractMatchedOrderDO.setAskCloseType(askContractOrder.getCloseType().byteValue());
        contractMatchedOrderDO.setBidCloseType(bidContractOrder.getCloseType().byteValue());
        contractMatchedOrderDO.setStatus(VALID);
        contractMatchedOrderDO.setGmtCreate(new Date());
        contractMatchedOrderDO.setGmtModified(contractMatchedOrderDO.getGmtCreate());
        try {
            int ret = contractMatchedOrderMapper.insert(contractMatchedOrderDO);
            if (ret < 1) {
                log.error("保存Contract订单数据到数据库失败({})", contractMatchedOrderDO);
                throw new RuntimeException("contractMatchedOrderMapper.insert failed{}");
            }
        } catch (Exception e) {
            log.error("保存Contract订单数据到数据库失败({})", contractMatchedOrderDO, e);
            throw new RuntimeException("contractMatchedOrderMapper.insert exception{}", e);
        }
        profiler.complelete("persistMatch");

        contractOrderDOS.stream().forEach(x -> {
             sendDealMessage(contractMatchedOrderDO.getId(), x, filledAmount, filledPrice);
            //后台交易监控日志打在里面 注释需谨慎
             saveToLog(x, filledAmount, contractMatchedOrderDO.getId());
        });

        resultCode.setCode(ResultCodeEnum.SUCCESS.getCode());
        return resultCode;
    }


    public void updateContractOrder(long id, BigDecimal filledAmount, BigDecimal filledPrice, Date gmtModified) {
        int aff;
        try{
            aff = contractOrderMapper.updateAmountAndStatus(id, filledAmount, filledPrice, gmtModified);
        }catch (Throwable t) {
            throw new RuntimeException(String.format("update contract order failed, orderId=%d, filledAmount=%s, filledPrice=%s, gmtModified=%s",
                    id, filledAmount, filledPrice, gmtModified),t);
        }
        if (0 == aff) {
            throw new RuntimeException(String.format("update contract order failed, orderId=%d, filledAmount=%s, filledPrice=%s, gmtModified=%s",
                    id, filledAmount, filledPrice, gmtModified));
        }
    }

    public void postDeal(List<PostDealMessage> postDealMessages){
        if (CollectionUtils.isEmpty(postDealMessages)) {
            log.error("empty postDealMessages in postDeal");
            return;
        }
        log.info("postDeal, size={}, keys={}", postDealMessages.size(), postDealMessages.stream().map(PostDealMessage::getMsgKey).collect(Collectors.toList()));
        PostDealMessage postDealMessage = postDealMessages.get(0);
        ContractOrderDO x = postDealMessage.getContractOrderDO();
        BigDecimal filledAmount = postDealMessage.getFilledAmount(), filledPrice = postDealMessage.getFilledPrice();
        //更新持仓
        UpdatePositionResult updatePositionResult = updatePosition(x, postDealMessages);

        //更新账户余额
        ContractDealer dealer = calBalanceChange(x, filledAmount, filledPrice, updatePositionResult);
        if (null != dealer && dealer.getAddedTotalAmount().compareTo(BigDecimal.ZERO) != 0) {
            com.fota.common.Result result = contractService.updateBalances(dealer);
            if (!result.isSuccess()) {
                log.error("update balance failed, params={}", dealer);
                failedBalanceMap.put(postDealMessage.getMsgKey(), JSON.toJSONString(dealer));
            }
        }
        updateTotalPosition(x.getContractId(), updatePositionResult);
    }

    public UpdatePositionResult updatePosition(ContractOrderDO contractOrderDO, List<PostDealMessage> postDealMessages){
        long userId = contractOrderDO.getUserId();
        long contractId = contractOrderDO.getContractId();

        UpdatePositionResult result = new UpdatePositionResult();
        UserPositionDO userPositionDO = userPositionMapper.selectByUserIdAndContractId(userId, contractId);
        for (PostDealMessage postDealMessage : postDealMessages) {
            BigDecimal filledAmount =  postDealMessage.getFilledAmount(), filledPrice = postDealMessage.getFilledPrice();
            if (userPositionDO == null) {
                // 建仓
                userPositionDO = ContractUtils.buildPosition(contractOrderDO, contractOrderDO.getLever(),filledAmount, filledPrice);
                BigDecimal direction = ContractUtils.toDir(userPositionDO.getPositionType());
                result.setOldOpenAveragePrice(userPositionDO.getAveragePrice());
                result.setOldAmount(postDealMessage.getFilledAmount().multiply(direction));
                continue;
            }

            BigDecimal direction = ContractUtils.toDir(userPositionDO.getPositionType());
            //如果第一次
            if (null == result.getOldOpenAveragePrice()) {
                result.setOldOpenAveragePrice(userPositionDO.getAveragePrice());
                result.setOldAmount(postDealMessage.getFilledAmount().multiply(direction));
            }

            BigDecimal newTotalAmount;
            int newPositionType=userPositionDO.getPositionType();
            BigDecimal newAveragePrice = null;

            if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
                //成交单和持仓是同方向
                newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount);
                newTotalAmount = userPositionDO.getUnfilledAmount().add(filledAmount);
            }
            //成交单和持仓是反方向 （平仓）
            else if (filledAmount.compareTo(userPositionDO.getUnfilledAmount()) <= 0) {
                //不改变仓位方向
                newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount);
                newTotalAmount = userPositionDO.getUnfilledAmount().subtract(filledAmount);
            } else {
                //改变仓位方向
                newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount);
                newPositionType = contractOrderDO.getOrderDirection();
                newTotalAmount = filledAmount.subtract(userPositionDO.getUnfilledAmount());
            }
            userPositionDO.setAveragePrice(newAveragePrice);
            userPositionDO.setUnfilledAmount(newTotalAmount);
            userPositionDO.setPositionType(newPositionType);

        }
        BigDecimal direction = ContractUtils.toDir(userPositionDO.getPositionType());
        result.setNewAmount(userPositionDO.getUnfilledAmount().multiply(direction));
        result.setNewOpenAveragePrice(userPositionDO.getAveragePrice());
        return result;
    }

//    public UpdatePositionResult updatePosition(ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice){
//        return internalUpdatePosition(contractOrderDO, filledAmount, filledPrice);
//    }


//    public UpdatePositionResult internalUpdatePosition(ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice){
//        UserPositionDO userPositionDO;
//        long userId = contractOrderDO.getUserId();
//        long contractId = contractOrderDO.getContractId();
//
//        UpdatePositionResult result = new UpdatePositionResult();
//
//        userPositionDO = userPositionMapper.selectByUserIdAndContractId(userId, contractId);
//        if (userPositionDO == null) {
//            // 建仓
//            userPositionDO = ContractUtils.buildPosition(contractOrderDO, contractOrderDO.getLever(), filledAmount, filledPrice);
//            userPositionMapper.insert(userPositionDO);
//            result.setNewPositionDirection(userPositionDO.getPositionType());
//            result.setNewTotalAmount(userPositionDO.getUnfilledAmount());
//            return result;
//        }
//
//        BigDecimal newTotalAmount;
//        int newPositionType=userPositionDO.getPositionType();
//        BigDecimal newAveragePrice = null;
//
//        result.setOldOpenAveragePrice(userPositionDO.getAveragePrice());
//        result.setOldOpenPositionDirection(ContractUtils.toDirection(userPositionDO.getPositionType()));
//
//        if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
//            //成交单和持仓是同方向
//            newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount);
//            newTotalAmount = userPositionDO.getUnfilledAmount().add(filledAmount);
//        }
//        //成交单和持仓是反方向 （平仓）
//        else if (filledAmount.compareTo(userPositionDO.getUnfilledAmount()) <= 0) {
//            //不改变仓位方向
//            newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount);
//            newTotalAmount = userPositionDO.getUnfilledAmount().subtract(filledAmount);
//            result.setCloseAmount(filledAmount.min(userPositionDO.getUnfilledAmount()));
//        } else {
//            //改变仓位方向
//            newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount);
//            newPositionType = contractOrderDO.getOrderDirection();
//            result.setCloseAmount(filledAmount.min(userPositionDO.getUnfilledAmount()));
//            newTotalAmount = filledAmount.subtract(userPositionDO.getUnfilledAmount());
//        }
//        result.setNewPositionDirection(newPositionType);
//        result.setNewTotalAmount(newTotalAmount);
//        boolean suc =  doUpdatePosition(userPositionDO, newAveragePrice, newTotalAmount, newPositionType);
//        if (!suc) {
//            throw new BizException(CONCURRENT_PROBLEM.getCode(), "doUpdate position failed");
//        }
//        return result;
//    }
    /**
     * @param userPositionDO  旧的持仓
     * @param newAvaeragePrice 新的开仓均价
     * @param newTotalAmount  新的持仓数量
     * @return
     */
    private boolean doUpdatePosition(UserPositionDO userPositionDO, BigDecimal newAvaeragePrice, BigDecimal newTotalAmount, int positionType) {
        userPositionDO.setAveragePrice(newAvaeragePrice);
        userPositionDO.setUnfilledAmount(newTotalAmount);
        userPositionDO.setPositionType(positionType);
        try{
            int aff = userPositionMapper.updatePositionById(userPositionDO);
            return aff == 1;
        }catch (Throwable t) {
            log.error("update position exception", t);
            return false;
        }
    }

    /**
     * 打印后台交易撮合监控日志
     * @param contractOrderDO
     * @param completeAmount
     * @param matchId
     */
    private void saveToLog(ContractOrderDO contractOrderDO,  BigDecimal completeAmount, long matchId){

        Map<String, Object> context = new HashMap<>();
        if (contractOrderDO.getOrderContext() != null){
            context  = JSON.parseObject(contractOrderDO.getOrderContext());
        }

        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO);
        contractOrderDTO.setCompleteAmount(completeAmount);
        contractOrderDTO.setOrderContext(context);
        String userName = "";
        if (context != null){
            userName = context.get("username") == null ? "": String.valueOf(context.get("username"));
        }
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, contractOrderDTO.getContractName(), userName, contractOrderDTO.getCompleteAmount(),
                System.currentTimeMillis(), 4, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(),matchId);
    }

    public ContractDealer calBalanceChange(ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice,
                                           UpdatePositionResult positionResult){
        long userId = contractOrderDO.getUserId();
        BigDecimal rate = contractOrderDO.getFee();

        //没有平仓，不用计算
        if (null == positionResult.getCloseAmount()) {
            return null;
        }
        //手续费
        BigDecimal actualFee = filledPrice.multiply(filledAmount).multiply(rate);

        //计算平仓盈亏
        // (filledPrice-oldOpenAveragePrice)*closeAmount*contractSize*oldOpenPositionDirection - actualFee
        BigDecimal addAmount = filledPrice.subtract(positionResult.getOldOpenAveragePrice())
                .multiply(positionResult.getCloseAmount())
                .multiply(positionResult.getOldOpenPositionDirection())
                .subtract(actualFee);

        ContractDealer dealer = new ContractDealer()
                .setUserId(userId)
                .setAddedTotalAmount(addAmount)
                .setTotalLockAmount(BigDecimal.ZERO)
                ;
        dealer.setDealType(ContractDealer.DealType.FORCE);
        return dealer;
    }

    public void updateTotalPosition(long contractId, UpdatePositionResult positionResult){
        BigDecimal increase;

        BigDecimal positionAmount = BigDecimal.ZERO;

        positionAmount = positionResult.getNewAmount();
        BigDecimal formerPositionAmount = positionResult.getOldAmount();
        increase = positionAmount.abs().subtract(formerPositionAmount.abs());

        Double currentPosition = redisManager.counter(Constant.CONTRACT_TOTAL_POSITION + contractId, increase);
        if (BigDecimal.valueOf(currentPosition).compareTo(increase) == 0) {
            BigDecimal position = userPositionMapper.countTotalPosition(contractId).multiply(BigDecimal.valueOf(2));
            currentPosition = redisManager.counter(Constant.CONTRACT_TOTAL_POSITION + contractId, position.subtract(increase));
        }
        log.info("update total position------contractId :{}   currentPosition :{}", contractId, currentPosition);

    }

    private void sendDealMessage(long matchId, ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice){
        PostDealMessage postMatchMessage = new PostDealMessage().setContractOrderDO(contractOrderDO)
                .setFilledAmount(filledAmount)
                .setFilledPrice(filledPrice)
                .setMatchId(matchId);

        MessageQueueSelector queueSelector = (final List<MessageQueue> mqs, final Message msg, final Object arg)->{
            int key = arg.hashCode();
            return mqs.get(key % mqs.size());
        };
        rocketMqManager.sendMessage(POST_DEAL_TOPIC, DEFAULT_TAG, matchId+"_"+contractOrderDO.getId(),postMatchMessage, queueSelector,
                contractOrderDO.getUserId()+contractOrderDO.getContractId());
    }

    private  ResultCode checkParam(ContractOrderDO askContractOrder, ContractOrderDO bidContractOrder, ContractMatchedOrderDTO contractMatchedOrderDTO) {
        if (askContractOrder == null){
            log.error("askContractOrder not exist, matchOrder={}",  contractMatchedOrderDTO);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        if (bidContractOrder == null){
            log.error("bidOrderContext not exist, matchOrder={}", contractMatchedOrderDTO);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }

        String messageKey = Joiner.on("-").join(contractMatchedOrderDTO.getAskOrderId().toString(),
                contractMatchedOrderDTO.getAskOrderStatus(), contractMatchedOrderDTO.getBidOrderId(),
                contractMatchedOrderDTO.getBidOrderStatus());

        BigDecimal filledAmount = contractMatchedOrderDTO.getFilledAmount();
        if (BasicUtils.gt(filledAmount, askContractOrder.getUnfilledAmount())) {
            log.error("ask unfilledAmount not enough.order={}, messageKey={}", askContractOrder, messageKey);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        if (BasicUtils.gt(filledAmount, bidContractOrder.getUnfilledAmount())) {
            log.error("bid unfilledAmount not enough.order={}, messageKey={}",bidContractOrder, messageKey);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }

        return ResultCode.success();
    }

}
