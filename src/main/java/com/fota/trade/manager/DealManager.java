package com.fota.trade.manager;

import com.alibaba.dubbo.remoting.TimeoutException;
import com.alibaba.fastjson.JSON;
import com.fota.asset.domain.ContractAccountAddAmountDTO;
import com.fota.asset.domain.enums.AssetOperationTypeEnum;
import com.fota.asset.service.AssetWriteService;
import com.fota.asset.service.ContractService;
import com.fota.common.Result;
import com.fota.common.utils.LogUtil;
import com.fota.trade.UpdateOrderItem;
import com.fota.trade.client.FailedRecord;
import com.fota.trade.client.PostDealPhaseEnum;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.dto.ProcessNoEnforceResult;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserContractLeverMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.msg.ContractDealedMessage;
import com.fota.trade.msg.TopicConstants;
import com.fota.trade.service.ContractCategoryService;
import com.fota.trade.util.*;
import com.google.common.base.Predicates;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.fota.trade.client.FailedRecord.NOT_SURE;
import static com.fota.trade.client.FailedRecord.RETRY;
import static com.fota.trade.client.PostDealPhaseEnum.UPDATE_BALANCE;
import static com.fota.trade.client.PostDealPhaseEnum.UPDATE_POSITION;
import static com.fota.trade.common.ResultCodeEnum.BIZ_ERROR;
import static com.fota.trade.common.ResultCodeEnum.ILLEGAL_PARAM;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;
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
    private ContractOrderManager contractOrderManager;

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

    @Autowired
    private AssetWriteService assetWriteService;


    private static final Logger UPDATE_POSITION_FAILED_LOGGER = LoggerFactory.getLogger("updatePositionFailed");
    private static final Logger UPDATE_POSITION_EXTRA_LOGGER = LoggerFactory.getLogger("updatePositionExtraInfo");

    private static final ExecutorService executorService = new ThreadPoolExecutor(4, 10, 3, TimeUnit.MINUTES, new LinkedBlockingDeque<>());


    /**
     * 禁止通过内部非Transactional方法调用此方法，否则@Transactional注解会失效
     *
     * @param contractMatchedOrderDTO
     * @return
     */
    @Transactional(rollbackFor = {Throwable.class}, isolation = Isolation.REPEATABLE_READ, propagation = REQUIRED)
    public ResultCode deal(ContractMatchedOrderDTO contractMatchedOrderDTO) {

        long contractId = contractMatchedOrderDTO.getContractId();
//        ContractCategoryDTO contractCategoryDO = contractCategoryService.getContractById(contractId);
//        if (null == contractCategoryDO) {
//            return ResultCode.error(ILLEGAL_PARAM.getCode(), "null contractCategoryDO, id=" + contractId);
//        }
//        if (!Objects.equals(PROCESSING.getCode(), contractCategoryDO.getStatus())) {
//            return ResultCode.error(ILLEGAL_PARAM.getCode(), "illegal contract status, id=" + contractId + ", status=" + contractCategoryDO.getStatus());
//        }

        ContractOrderDO askContractOrder = contractOrderMapper.selectByIdAndUserId(contractMatchedOrderDTO.getAskUserId(), contractMatchedOrderDTO.getAskOrderId());
        ContractOrderDO bidContractOrder = contractOrderMapper.selectByIdAndUserId(contractMatchedOrderDTO.getBidUserId(), contractMatchedOrderDTO.getBidOrderId());

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
        long matchId = contractMatchedOrderDTO.getId();
        Long transferTime = System.currentTimeMillis();

        //更新委托
        contractOrderDOS.forEach(x -> {
            x.fillAmount(filledAmount);
            updateContractOrder(x.getUserId(), x.getId(), filledAmount, filledPrice, new Date(transferTime));
        });
        profiler.complelete("update contract order");


        BigDecimal askFee = filledAmount.
                multiply(filledPrice).
                multiply(askContractOrder.getFee());
        BigDecimal bidFee = filledAmount.
                multiply(filledPrice).
                multiply(bidContractOrder.getFee());
        ContractMatchedOrderDO askMatchedRecord = com.fota.trade.common.BeanUtils.extractContractMatchedRecord(contractMatchedOrderDTO, ASK.getCode(),
                askFee, askContractOrder.getOrderType());

        ContractMatchedOrderDO bidMatchedRecord = com.fota.trade.common.BeanUtils.extractContractMatchedRecord(contractMatchedOrderDTO, BID.getCode(),
                bidFee, bidContractOrder.getOrderType());
        int ret;
        try {
            ret = contractMatchedOrderMapper.insert(Arrays.asList(askMatchedRecord, bidMatchedRecord));
        } catch (Exception e) {
            throw new RuntimeException("contractMatchedOrderMapper.insert exception{}", e);
        }
        if (ret != 2) {
            throw new RuntimeException("contractMatchedOrderMapper.insert failed{}");
        }
        profiler.complelete("persistMatch");

        contractOrderDOS.stream().forEach(x -> {
            sendDealMessage(matchId, x, filledAmount, filledPrice);
            //后台交易监控日志打在里面 注释需谨慎
            saveToLog(x, filledAmount, matchId);
        });

        resultCode.setCode(ResultCodeEnum.SUCCESS.getCode());
        return resultCode;
    }

    public ProcessNoEnforceResult processNoEnforceMatchedOrders(ContractADLMatchDTO contractADLMatchDTO) {
        if (CollectionUtils.isEmpty(contractADLMatchDTO.getMatchedList())) {
            log.error("empty contractMatchedOrderDTOList");
            return new ProcessNoEnforceResult();
        }
        long matchId = contractADLMatchDTO.getId();
        List<ADLMatchedDTO> adlMatchedDTOList = contractADLMatchDTO.getMatchedList();

        Date gmtModified = new Date();

        //排序，防止死锁
        adlMatchedDTOList.sort((a, b) -> {
            int c = a.getUserId().compareTo(b.getUserId());
            if (c != 0) {
                return c;
            }
            return a.getId().compareTo(b.getId());
        });

        List<ContractMatchedOrderDO> contractMatchedOrderDOS = adlMatchedDTOList.stream()
                .map( matchedOrderDTO ->  ConvertUtils.toMatchedOrderDO( matchedOrderDTO, contractADLMatchDTO.getDirection() , contractADLMatchDTO.getUserId(),
                        contractADLMatchDTO.getContractId(), contractADLMatchDTO.getContractName()))
                .collect(Collectors.toList());

        List<UpdateOrderItem> updateOrderItems = adlMatchedDTOList.stream().map(x -> ConvertUtils.toUpdateOrderItem(x))
                .collect(Collectors.toList());
        // 不可以并行去做，有可能死锁，
        // sharding-jdbc不支持批量更新
        updateOrderItems.stream().forEach(x -> {
            updateContractOrder(x.getUserId(), x.getId(), x.getFilledAmount(), x.getFilledPrice(), gmtModified);
        });

        ProcessNoEnforceResult processNoEnforceResult = new ProcessNoEnforceResult();
        processNoEnforceResult.setContractMatchedOrderDOS(contractMatchedOrderDOS);

        contractMatchedOrderDOS.stream().forEach(contractMatchedOrderDO -> {
            ContractOrderDO x = contractOrderMapper.selectByIdAndUserId(contractMatchedOrderDO.getUserId(), contractMatchedOrderDO.getOrderId());
            BigDecimal filledAmount = contractMatchedOrderDO.getFilledAmount();
            BigDecimal filledPrice = contractMatchedOrderDO.getFilledPrice();
            //后台交易监控日志打在里面 注释需谨慎
            saveToLog(x, filledAmount, matchId);
            sendDealMessage(matchId, x, filledAmount, filledPrice);

        });

        return processNoEnforceResult;

    }


//    public void sendMatchMessage(ContractMatchedOrderDTO contractMatchedOrderDTO, BigDecimal fee){
//        long matchId = contractMatchedOrderDTO.getId();
//        OrderMessage orderMessage = new OrderMessage();
//        orderMessage.setSubjectId(contractMatchedOrderDTO.getContractId());
//        orderMessage.setSubjectName(contractMatchedOrderDTO.getContractName());
//        orderMessage.setTransferTime(contractMatchedOrderDTO.getGmtCreate().getTime());
//        if (null != contractMatchedOrderDTO.getFilledPrice()) {
//            orderMessage.setPrice(new BigDecimal(contractMatchedOrderDTO.getFilledPrice()));
//        }
//        orderMessage.setAmount(contractMatchedOrderDTO.getFilledAmount());
//        orderMessage.setEvent(OrderOperateTypeEnum.DEAL_ORDER.getCode());
//        orderMessage.setAskOrderId(contractMatchedOrderDTO.getAskOrderId());
//        orderMessage.setBidOrderId(contractMatchedOrderDTO.getBidOrderId());
//        orderMessage.setAskUserId(contractMatchedOrderDTO.getAskUserId());
//        orderMessage.setBidUserId(contractMatchedOrderDTO.getBidUserId());
//        orderMessage.setFee(fee);
//        orderMessage.setMatchOrderId(contractMatchedOrderDTO.getId());
//
//        orderMessage.setAskOrderUnfilledAmount(contractMatchedOrderDTO.getAskOrderUnfilledAmount());
//        orderMessage.setBidOrderUnfilledAmount(contractMatchedOrderDTO.getBidOrderUnfilledAmount());
//        orderMessage.setMatchType(contractMatchedOrderDTO.getMatchType());
//
//        orderMessage.setContractMatchAssetName(contractMatchedOrderDTO.getAssetName());
//        orderMessage.setContractType(contractMatchedOrderDTO.getContractType());
//        if (contractMatchedOrderDTO.getAskOrderPrice() != null){
//            orderMessage.setAskOrderEntrustPrice(new BigDecimal(contractMatchedOrderDTO.getAskOrderPrice()));
//        }
//        if (contractMatchedOrderDTO.getBidOrderPrice() != null){
//            orderMessage.setBidOrderEntrustPrice(new BigDecimal(contractMatchedOrderDTO.getBidOrderPrice()));
//        }
//        //TODO 临时兼容方案
//        Boolean sendRet = rocketMqManager.sendMessage("match", "contract", matchId+"", orderMessage);
//
//    }
    public void updateContractOrder(long userId, long id, BigDecimal filledAmount, BigDecimal filledPrice, Date gmtModified) {
        int aff;
        try {
            aff = contractOrderMapper.updateAmountAndStatus(userId, id, filledAmount, filledPrice, gmtModified);
        } catch (Throwable t) {
            throw new RuntimeException(String.format("update contract order failed, orderId=%d, filledAmount=%s, filledPrice=%s, gmtModified=%s",
                    id, filledAmount, filledPrice, gmtModified), t);
        }
        if (0 == aff) {
            throw new RuntimeException(String.format("update contract order failed, orderId=%d, filledAmount=%s, filledPrice=%s, gmtModified=%s",
                    id, filledAmount, filledPrice, gmtModified));
        }
    }

    /**
     *
     * @param postDealMessages
     * @return 更新同一用户，同一合约。只要更新完持仓就返回成功，后续失败打日志
     */
    public Result postDealOneUserOneContract(List<ContractDealedMessage> postDealMessages) {
        if (CollectionUtils.isEmpty(postDealMessages)) {
            LogUtil.error(TradeBizTypeEnum.CONTRACT_DEAL, null, postDealMessages, "empty postDealMessages");
            return Result.fail(ILLEGAL_PARAM.getCode(), "empty postDealMessages in postDeal");
        }
        ContractDealedMessage sample = postDealMessages.get(0);
        long userId =sample.getUserId();
        long contractId = sample.getSubjectId();

                //更新持仓
        UpdatePositionResult positionResult = updatePosition(userId, contractId, postDealMessages);
        if (null == positionResult) {
            //不回滚则打日志
            UPDATE_POSITION_FAILED_LOGGER.error("{}", new FailedRecord(RETRY, UPDATE_POSITION.name(), postDealMessages));
            return Result.fail(BIZ_ERROR.getCode(), "update position failed");
        }
        //更新账户余额，失败打日志 任然返回成功
        processAfterPositionUpdated(positionResult, postDealMessages);
        return Result.suc(null);
    }

    /**
     * must be same user
     * @param positionResult
     * @param postDealMessages
     * @return
     */
    public Result processAfterPositionUpdated(UpdatePositionResult positionResult, List<ContractDealedMessage> postDealMessages){
        Result result = internalProcessAfterPositionUpdated(positionResult, postDealMessages);
        UPDATE_POSITION_EXTRA_LOGGER.info("positionResult:{}", JSON.toJSONString(positionResult));
        return result;
    }

    public  Result internalProcessAfterPositionUpdated(UpdatePositionResult positionResult, List<ContractDealedMessage> postDealMessages){
        long userId = positionResult.getUserId();
        long contractId = positionResult.getContractId();
        if (CollectionUtils.isEmpty(postDealMessages)) {
            LogUtil.error(TradeBizTypeEnum.CONTRACT_DEAL, null, postDealMessages, "empty postDealMessages");
            return Result.fail(ILLEGAL_PARAM.getCode(), "empty postDealMessages in postDeal");
        }
        if (0 == positionResult.getClosePL().compareTo(ZERO)) {
            positionResult.setPostDealPhaseEnum(PostDealPhaseEnum.UPDATE_BALANCE);
            return Result.suc(null);
        }
        ContractAccountAddAmountDTO contractAccountAddAmountDTO = new ContractAccountAddAmountDTO();
        contractAccountAddAmountDTO.setAddAmount(positionResult.getClosePL());
        contractAccountAddAmountDTO.setUserId(userId);

        Result<Boolean> ret;
        try {
            ret = assetWriteService.addContractAmount(contractAccountAddAmountDTO,
                    positionResult.getRequestId(), AssetOperationTypeEnum.CONTRACT_DEAL.getCode());
        }catch (Throwable t){
            if (t.getCause() instanceof TimeoutException) {
                UPDATE_POSITION_FAILED_LOGGER.error("{}\037", new FailedRecord(NOT_SURE, UPDATE_BALANCE.name(), positionResult), t);
            }else{
                UPDATE_POSITION_FAILED_LOGGER.error("{}\037", new FailedRecord(RETRY, UPDATE_BALANCE.name(), positionResult), t);
            }
            return Result.fail(null);
        }
        if (!ret.isSuccess() || !ret.getData()) {
            UPDATE_POSITION_FAILED_LOGGER.error("{}\037", new FailedRecord(RETRY, UPDATE_BALANCE.name(), positionResult, String.valueOf(ret.getCode()), String.valueOf(ret.getMessage())));
            return Result.fail(null);
        }
        positionResult.setPostDealPhaseEnum(UPDATE_BALANCE);
        return Result.suc(null);
    }

    public void updateTodayFee(List<ContractDealedMessage> postDealMessages){
        if (CollectionUtils.isEmpty(postDealMessages)){
            return;
        }
        BigDecimal totalFee = postDealMessages.stream().filter(x->x.getTotalFee() != null)
                .map(ContractDealedMessage::getTotalFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalFee.compareTo(ZERO) > 0){
            String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
            Double currentFee = redisManager.counter(Constant.REDIS_TODAY_FEE + dateStr, totalFee);
            if (null == currentFee) {
                log.error("update total position amount failed, totalFee={}", totalFee);
            }
        }
    }

    public UpdatePositionResult updatePosition(long userId, long contractId, List<ContractDealedMessage> postDealMessages) {
        return BasicUtils.retryWhenFail(()-> internalUpdatePosition(userId, contractId, postDealMessages), x -> null!=x, Duration.ofMillis(10), 3);
    }
    private UpdatePositionResult internalUpdatePosition(long userId, long contractId, List<ContractDealedMessage> postDealMessages) {
        String requestId = postDealMessages.stream().map(ContractDealedMessage::msgKey).collect(Collectors.joining());

        ContractDealedMessage sample = postDealMessages.get(0);
        UpdatePositionResult result = new UpdatePositionResult();
        result.setUserId(userId)
                .setContractId(contractId)
                .setRequestId(requestId)
                .setPostDealPhaseEnum(PostDealPhaseEnum.UPDATE_POSITION);
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
        for (ContractDealedMessage postDealMessage : postDealMessages) {
            BigDecimal rate = postDealMessage.getFeeRate();
            BigDecimal filledAmount = postDealMessage.getFilledAmount(), filledPrice = postDealMessage.getFilledPrice();

            BigDecimal amount = ContractUtils.computeSignAmount(filledAmount, postDealMessage.getOrderDirection());
            int positionType = preAmount.compareTo(ZERO) > 0 ? OVER.getCode() : EMPTY.getCode();

            //新的持仓量，带符号
            BigDecimal newAmount = preAmount.add(amount);
            //新的开仓均价
            BigDecimal newOpenAveragePrice = ContractUtils.computeAveragePrice(postDealMessage.getOrderDirection(), positionType, rate, preAmount.abs(),
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
                return null;
            }
        }else {
            boolean suc = doUpdatePosition(userPositionDO);
            if (!suc) {
                return null;
            }
        }
        executorService.submit(() -> {
            //防止异常抛出
            BasicUtils.exeWhitoutError(() -> updateTotalPosition(contractId, result));
            BasicUtils.exeWhitoutError(() -> updateTodayFee(postDealMessages));
        });
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

    private ContractDealedMessage toDealMessage(long matchId, ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice) {
        ContractDealedMessage postMatchMessage = new ContractDealedMessage(contractOrderDO.getContractId(), contractOrderDO.getUserId(),
                contractOrderDO.getOrderDirection(), contractOrderDO.getFee(), contractOrderDO.getLever(),  contractOrderDO.getContractName());
        postMatchMessage.setOrderId(contractOrderDO.getId());
        postMatchMessage.setFilledAmount(filledAmount);
        postMatchMessage.setFilledPrice(filledPrice);
        postMatchMessage.setMatchId(matchId);
        return postMatchMessage;
    }
    private void sendDealMessage(long matchId, ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice) {
        ContractDealedMessage contractDealedMessage = toDealMessage(matchId, contractOrderDO, filledAmount, filledPrice);
        sendDealMessage(contractDealedMessage);
    }
    public void sendDealMessage(ContractDealedMessage postDealMessage) {
        MessageQueueSelector queueSelector = (final List<MessageQueue> mqs, final Message msg, final Object arg) -> {
            int key = arg.hashCode();
            return mqs.get(key % mqs.size());
        };
        rocketMqManager.sendMessage(TopicConstants.TRD_CONTRACT_DEAL, postDealMessage.getSubjectId()+"", postDealMessage.msgKey(), postDealMessage, queueSelector,
                postDealMessage.getGroup());
    }

    private ResultCode checkParam(ContractOrderDO askContractOrder, ContractOrderDO bidContractOrder, ContractMatchedOrderDTO contractMatchedOrderDTO) {
        if (askContractOrder == null) {
            LogUtil.error(TradeBizTypeEnum.CONTRACT_DEAL,  contractMatchedOrderDTO.getId()+"", contractMatchedOrderDTO, "null askContractOrder");
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        if (bidContractOrder == null) {
            LogUtil.error(TradeBizTypeEnum.CONTRACT_DEAL,  contractMatchedOrderDTO.getId()+"", contractMatchedOrderDTO, "null bidContractOrder");
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }

        Integer problemDirection = null;
        BigDecimal filledAmount = contractMatchedOrderDTO.getFilledAmount();
        if (BasicUtils.gt(filledAmount, askContractOrder.getUnfilledAmount())) {
            problemDirection = askContractOrder.getOrderDirection();
        }
        if (BasicUtils.gt(filledAmount, bidContractOrder.getUnfilledAmount())) {
            problemDirection = bidContractOrder.getOrderDirection();
        }
        if (null != problemDirection) {
            LogUtil.error(TradeBizTypeEnum.CONTRACT_DEAL,  contractMatchedOrderDTO.getId()+"", contractMatchedOrderDTO, "unfilledAmount not enough, problemDirection="+problemDirection);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        return ResultCode.success();
    }

}
