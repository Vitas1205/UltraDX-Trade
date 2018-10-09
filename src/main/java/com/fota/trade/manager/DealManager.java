package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.asset.domain.ContractDealer;
import com.fota.asset.service.ContractService;
import com.fota.common.utils.CommonUtils;
import com.fota.trade.client.PostDealMessage;
import com.fota.trade.client.constants.DealedMessage;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.enums.OrderOperateTypeEnum;
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
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static com.fota.trade.client.constants.Constants.*;
import static com.fota.trade.client.constants.DealedMessage.CONTRACT_TYPE;
import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.common.ResultCodeEnum.BIZ_ERROR;
import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;
import static com.fota.trade.domain.enums.ContractStatusEnum.PROCESSING;
import static com.fota.trade.domain.enums.PositionTypeEnum.EMPTY;
import static com.fota.trade.domain.enums.PositionTypeEnum.OVER;
import static java.math.BigDecimal.ZERO;
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
     *
     * @param contractMatchedOrderDTO
     * @return
     */
    @Transactional(rollbackFor = {Throwable.class}, isolation = Isolation.REPEATABLE_READ, propagation = REQUIRED)
    public ResultCode deal(ContractMatchedOrderDTO contractMatchedOrderDTO) {

        long contractId = contractMatchedOrderDTO.getContractId();
        ContractCategoryDTO contractCategoryDO = contractCategoryService.getContractById(contractId);
        if (null == contractCategoryDO) {
            return ResultCode.error(BIZ_ERROR.getCode(), "null contractCategoryDO, id=" + contractId);
        }
        if (!Objects.equals(PROCESSING.getCode(), contractCategoryDO.getStatus())) {
            return ResultCode.error(BIZ_ERROR.getCode(), "illegal contract status, id=" + contractId + ", status=" + contractCategoryDO.getStatus());
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
            if (c != 0) {
                return c;
            }
            return a.getId().compareTo(b.getId());
        });
        Profiler profiler = null == ThreadContextUtil.getPrifiler()
                ? new Profiler("ContractOrderManager.updateOrderByMatch") : ThreadContextUtil.getPrifiler();

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
        BigDecimal askFee = contractMatchedOrderDO.getFilledAmount().
                multiply(contractMatchedOrderDO.getFilledPrice()).
                multiply(askContractOrder.getFee()).
                setScale(CommonUtils.scale, BigDecimal.ROUND_UP);
        BigDecimal bidFee = contractMatchedOrderDO.getFilledAmount().
                multiply(contractMatchedOrderDO.getFilledPrice()).
                multiply(bidContractOrder.getFee()).
                setScale(CommonUtils.scale, BigDecimal.ROUND_UP);
        contractMatchedOrderDO.setAskFee(askFee);
        contractMatchedOrderDO.setBidFee(bidFee);
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

        sendMatchMessage(contractMatchedOrderDO.getId(), contractMatchedOrderDTO, askFee.add(bidFee));
        contractOrderDOS.stream().forEach(x -> {
            sendDealMessage(contractMatchedOrderDO.getId(), x, filledAmount, filledPrice, askFee.add(bidFee));
            //后台交易监控日志打在里面 注释需谨慎
            saveToLog(x, filledAmount, contractMatchedOrderDO.getId());
        });

        resultCode.setCode(ResultCodeEnum.SUCCESS.getCode());
        return resultCode;
    }


    public void sendMatchMessage(long matchId, ContractMatchedOrderDTO contractMatchedOrderDTO, BigDecimal fee){
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setSubjectId(contractMatchedOrderDTO.getContractId());
        orderMessage.setSubjectName(contractMatchedOrderDTO.getContractName());
        orderMessage.setTransferTime(contractMatchedOrderDTO.getGmtCreate().getTime());
        if (null != contractMatchedOrderDTO.getFilledPrice()) {
            orderMessage.setPrice(new BigDecimal(contractMatchedOrderDTO.getFilledPrice()));
        }
        orderMessage.setAmount(contractMatchedOrderDTO.getFilledAmount());
        orderMessage.setEvent(OrderOperateTypeEnum.DEAL_ORDER.getCode());
        orderMessage.setAskOrderId(contractMatchedOrderDTO.getAskOrderId());
        orderMessage.setBidOrderId(contractMatchedOrderDTO.getBidOrderId());
        orderMessage.setAskUserId(contractMatchedOrderDTO.getAskUserId());
        orderMessage.setBidUserId(contractMatchedOrderDTO.getBidUserId());
        orderMessage.setFee(fee);
        orderMessage.setMatchOrderId(contractMatchedOrderDTO.getId());

        orderMessage.setAskOrderUnfilledAmount(contractMatchedOrderDTO.getAskOrderUnfilledAmount());
        orderMessage.setBidOrderUnfilledAmount(contractMatchedOrderDTO.getBidOrderUnfilledAmount());
        orderMessage.setMatchType(contractMatchedOrderDTO.getMatchType());

        orderMessage.setContractMatchAssetName(contractMatchedOrderDTO.getAssetName());
        orderMessage.setContractType(contractMatchedOrderDTO.getContractType());
        if (contractMatchedOrderDTO.getAskOrderPrice() != null){
            orderMessage.setAskOrderEntrustPrice(new BigDecimal(contractMatchedOrderDTO.getAskOrderPrice()));
        }
        if (contractMatchedOrderDTO.getBidOrderPrice() != null){
            orderMessage.setBidOrderEntrustPrice(new BigDecimal(contractMatchedOrderDTO.getBidOrderPrice()));
        }
        //TODO 临时兼容方案
        Boolean sendRet = rocketMqManager.sendMessage("match", "contract", matchId+"", orderMessage);

    }
    public void updateContractOrder(long id, BigDecimal filledAmount, BigDecimal filledPrice, Date gmtModified) {
        int aff;
        try {
            aff = contractOrderMapper.updateAmountAndStatus(id, filledAmount, filledPrice, gmtModified);
        } catch (Throwable t) {
            throw new RuntimeException(String.format("update contract order failed, orderId=%d, filledAmount=%s, filledPrice=%s, gmtModified=%s",
                    id, filledAmount, filledPrice, gmtModified), t);
        }
        if (0 == aff) {
            throw new RuntimeException(String.format("update contract order failed, orderId=%d, filledAmount=%s, filledPrice=%s, gmtModified=%s",
                    id, filledAmount, filledPrice, gmtModified));
        }
    }

    public ResultCode postDeal(List<PostDealMessage> postDealMessages) {
        if (CollectionUtils.isEmpty(postDealMessages)) {
            log.error("empty postDealMessages in postDeal");
            return ResultCode.error(ILLEGAL_PARAM.getCode(), "empty postDealMessages in postDeal");
        }
        log.info("postDeal, size={}, keys={}", postDealMessages.size(), postDealMessages.stream().map(PostDealMessage::getMsgKey).collect(Collectors.toList()));
        PostDealMessage postDealMessage = postDealMessages.get(0);
        BigDecimal filledAmount = postDealMessage.getFilledAmount(), filledPrice = postDealMessage.getFilledPrice();
        //更新持仓
        UpdatePositionResult positionResult = updatePosition(postDealMessages);
        if (null == positionResult) {
            return ResultCode.error(BIZ_ERROR.getCode(), "update position failed");
        }
        DealedMessage dealedMessage = new DealedMessage()
                .setSubjectId(postDealMessage.getContractOrderDO().getContractId())
                .setSubjectType(CONTRACT_TYPE)
                .setUserId(postDealMessage.getContractOrderDO().getUserId());

        rocketMqManager.sendMessage(DEALED_TOPIC, DEALED_CONTRACT_TAG, postDealMessage.getMsgKey() , dealedMessage);

        if (positionResult.getClosePL().compareTo(ZERO) != 0) {
            ContractDealer dealer = new ContractDealer()
                    .setUserId(postDealMessage.getContractOrderDO().getUserId())
                    .setAddedTotalAmount(positionResult.getClosePL())
                    .setTotalLockAmount(ZERO);
            dealer.setDealType(ContractDealer.DealType.FORCE);
            try {
                com.fota.common.Result result = contractService.updateBalances(dealer);
                if (!result.isSuccess()) {
                    log.error("update balance failed, params={}", dealer);
                    failedBalanceMap.put(postDealMessage.getMsgKey(), JSON.toJSONString(dealer));
                }
            }catch (Exception e){
                log.error("update balance exception, params={}", dealer, e);
                failedBalanceMap.put(postDealMessage.getMsgKey(), JSON.toJSONString(dealer));
            }
        }
        //防止异常抛出
        BasicUtils.exeWhitoutError(() -> updateTotalPosition(postDealMessage.getContractOrderDO().getContractId(), positionResult));
        BasicUtils.exeWhitoutError(() -> updateTodayFee(postDealMessages));
        return ResultCode.success();
    }

    public void updateTodayFee(List<PostDealMessage> postDealMessages){
        BigDecimal totalFee = postDealMessages.stream().map(PostDealMessage::getTotalFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalFee.compareTo(ZERO) > 0){
            Date date = new Date();
            SimpleDateFormat sdf1 =new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat sdf2 =new SimpleDateFormat("H");
            int hours = Integer.valueOf(sdf2.format(date));
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.add(Calendar.DATE, 1);
            String dateStr = hours < 18 ? sdf1.format(date) : sdf1.format(calendar.getTime());
            Double currentFee = redisManager.counterWithExpire(Constant.REDIS_TODAY_FEE + dateStr, totalFee, Duration.ofDays(1L));
            if (null == currentFee) {
                log.error("update total position amount failed, totalFee={}", totalFee);
                return;
            }
        }
    }

    public UpdatePositionResult updatePosition(List<PostDealMessage> postDealMessages) {

        ContractOrderDO sample = postDealMessages.get(0).getContractOrderDO();
        long userId = sample.getUserId();
        long contractId = sample.getContractId();

        UpdatePositionResult result = new UpdatePositionResult();
        UserPositionDO userPositionDO = userPositionMapper.selectByUserIdAndContractId(userId, contractId);
        boolean shouldInsert = false;
        // db没有持仓记录，新建
        if (userPositionDO == null) {
            shouldInsert = true;
            userPositionDO = ContractUtils.buildPosition(sample);
        }

        BigDecimal preOpenAveragePrice = userPositionDO.getAveragePrice();
        BigDecimal preAmount = userPositionDO.computeSignAmount();
        BigDecimal totalClosePL = ZERO;
        //记录开始持仓量
        result.setOldAmount(preAmount);
        for (PostDealMessage postDealMessage : postDealMessages) {
            userPositionDO.setFeeRate(sample.getFee());
            ContractOrderDO contractOrderDO = postDealMessage.getContractOrderDO();
            BigDecimal rate = contractOrderDO.getFee();
            BigDecimal filledAmount = postDealMessage.getFilledAmount(), filledPrice = postDealMessage.getFilledPrice();

            BigDecimal amount = ContractUtils.computeSignAmount(filledAmount, contractOrderDO.getOrderDirection());
            int positionType = preAmount.compareTo(ZERO) > 0 ? OVER.getCode() : EMPTY.getCode();

            //新的持仓量，带符号
            BigDecimal newAmount = preAmount.add(amount);
            //新的开仓均价
            BigDecimal newOpenAveragePrice = ContractUtils.computeAveragePrice(contractOrderDO.getOrderDirection(), positionType, rate, preAmount.abs(),
                    preOpenAveragePrice, filledAmount, filledPrice);

            //加平仓盈亏
            totalClosePL = totalClosePL.add(ContractUtils.computeClosePL(rate, filledAmount, filledPrice, preAmount, newAmount, preOpenAveragePrice));

            //更新以前持仓量和开仓均价
            preAmount = newAmount;
            preOpenAveragePrice = newOpenAveragePrice;
        }
        //记录结束持仓量和总平仓盈亏
        result.setNewAmount(preAmount);
        result.setClosePL(totalClosePL);

        //更新持仓记录
        int positionType = preAmount.compareTo(ZERO) > 0 ? OVER.getCode() : EMPTY.getCode();
        userPositionDO.setPositionType(positionType);
        userPositionDO.setUnfilledAmount(preAmount.abs());
        userPositionDO.setAveragePrice(preOpenAveragePrice);

        if (null == userPositionDO.getAveragePrice()) {
            userPositionDO.setAveragePrice(ZERO);
        }
        if (shouldInsert) {
            int aff = userPositionMapper.insert(userPositionDO);
            if (1 != aff) {
                log.error("insert userPositionDO failed, userPositionDO={}", userPositionDO);
            }
        }else {
            boolean suc = doUpdatePosition(userPositionDO);
            if (!suc) {
                return null;
            }
        }
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
     * @param userPositionDO   旧的持仓
     * @return
     */
    private boolean doUpdatePosition(UserPositionDO userPositionDO) {
        try {
            int aff = userPositionMapper.updatePositionById(userPositionDO);
            if (1 != aff) {
                log.error("update position failed, userPositionDO={}", userPositionDO);
            }
            return aff == 1;
        } catch (Throwable t) {
            log.error("update position exception, userPositionDO={}", userPositionDO, t);
            return false;
        }
    }

    /**
     * 打印后台交易撮合监控日志
     *
     * @param contractOrderDO
     * @param completeAmount
     * @param matchId
     */
    private void saveToLog(ContractOrderDO contractOrderDO, BigDecimal completeAmount, long matchId) {

        Map<String, Object> context = new HashMap<>();
        if (contractOrderDO.getOrderContext() != null) {
            context = JSON.parseObject(contractOrderDO.getOrderContext());
        }

        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO);
        contractOrderDTO.setCompleteAmount(completeAmount);
        contractOrderDTO.setOrderContext(context);
        String userName = "";
        if (context != null) {
            userName = context.get("username") == null ? "" : String.valueOf(context.get("username"));
        }
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, contractOrderDTO.getContractName(), userName, contractOrderDTO.getCompleteAmount(),
                System.currentTimeMillis(), 4, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), matchId);
    }

    public void updateTotalPosition(long contractId, UpdatePositionResult positionResult) {
        if (null == positionResult || null == positionResult.getNewAmount() || null == positionResult.getOldAmount()) {
            log.error("illegal param, positionResult={}", positionResult);
        }

        BigDecimal increase;

        BigDecimal positionAmount = ZERO;

        positionAmount = positionResult.getNewAmount();
        BigDecimal formerPositionAmount = positionResult.getOldAmount();
        increase = positionAmount.abs().subtract(formerPositionAmount.abs());

        Double currentPosition = redisManager.counter(Constant.CONTRACT_TOTAL_POSITION + contractId, increase);
        //redis异常，直接返回
        if (null == currentPosition) {
            log.error("update total position amount failed, contractId={}", contractId);
            return;
        }
        //初始值为0，从db读取
        if (BasicUtils.equal(new BigDecimal(currentPosition), increase)) {
            BigDecimal totalPosition = userPositionMapper.countTotalPosition(contractId);
            if (null == totalPosition) {
                redisManager.counter(Constant.CONTRACT_TOTAL_POSITION + contractId, new BigDecimal(currentPosition).negate());
                return;
            }
            BigDecimal position = totalPosition.multiply(BigDecimal.valueOf(2));
            redisManager.counter(Constant.CONTRACT_TOTAL_POSITION + contractId, position.subtract(increase));
        }

    }

    private void sendDealMessage(long matchId, ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice, BigDecimal totalFee) {
        PostDealMessage postMatchMessage = new PostDealMessage().setContractOrderDO(contractOrderDO)
                .setFilledAmount(filledAmount)
                .setFilledPrice(filledPrice)
                .setMatchId(matchId)
                .setTotalFee(totalFee);

        MessageQueueSelector queueSelector = (final List<MessageQueue> mqs, final Message msg, final Object arg) -> {
            int key = arg.hashCode();
            return mqs.get(key % mqs.size());
        };
        rocketMqManager.sendMessage(CONTRACT_POSITION_UPDATE_TOPIC, DEFAULT_TAG, matchId + "_" + contractOrderDO.getId(), postMatchMessage, queueSelector,
                contractOrderDO.getUserId() + contractOrderDO.getContractId());
    }

    private ResultCode checkParam(ContractOrderDO askContractOrder, ContractOrderDO bidContractOrder, ContractMatchedOrderDTO contractMatchedOrderDTO) {
        if (askContractOrder == null) {
            log.error("askContractOrder not exist, matchOrder={}", contractMatchedOrderDTO);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        if (bidContractOrder == null) {
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
            log.error("bid unfilledAmount not enough.order={}, messageKey={}", bidContractOrder, messageKey);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }

        return ResultCode.success();
    }

}
