package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.ContractDealer;
import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.common.utils.CommonUtils;
import com.fota.match.domain.ContractMatchedOrderTradeDTO;
import com.fota.match.service.ContractMatchedOrderService;
import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.enums.*;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.ContractAccountService;
import com.fota.trade.util.ContractUtils;
import com.fota.trade.util.Profiler;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.common.ResultCodeEnum.BALANCE_NOT_ENOUGH;
import static com.fota.trade.domain.enums.OrderTypeEnum.ENFORCE;
import static com.fota.trade.util.ContractUtils.computeAveragePrice;
import static java.util.stream.Collectors.*;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Component
public class ContractOrderManager {
    private static final Logger log = LoggerFactory.getLogger(ContractOrderManager.class);
    private static final Logger tradeLog = LoggerFactory.getLogger("trade");


    private static BigDecimal contractFee = BigDecimal.valueOf(0.001);

    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Autowired
    private ContractLeverManager contractLeverManager;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private ContractCategoryMapper contractCategoryMapper;

    @Autowired
    private RocketMqManager rocketMqManager;

    @Autowired
    private AssetService assetService;

    @Autowired
    private ContractService contractService;

    @Autowired
    private ContractMatchedOrderService contractMatchedOrderService;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Autowired
    private RealTimeEntrust realTimeEntrust;

    @Autowired
    private ContractAccountService contractAccountService;

    Random random = new Random();

    private static final int MAX_UPDATE_RETRIES = 50;

    private ContractService getContractService() {
        return contractService;
    }

    private AssetService getAssetService() {
        return assetService;
    }

    ExecutorService executorService = Executors.newFixedThreadPool(5);

    public List<ContractOrderDO> listNotMatchOrder(Long contractOrderIndex, Integer orderDirection) {
        List<ContractOrderDO> notMatchOrderList = null;
        try {
            notMatchOrderList = contractOrderMapper.notMatchOrderList(
                    OrderStatusEnum.COMMIT.getCode(), OrderStatusEnum.PART_MATCH.getCode(), contractOrderIndex, orderDirection);
        } catch (Exception e) {
            log.error("contractOrderMapper.notMatchOrderList error", e);
        }
        if (notMatchOrderList == null) {
            notMatchOrderList = new ArrayList<>();
        }
        return notMatchOrderList;
    }


    public ResultCode cancelOrderByContractId(Long contractId, Map<String, String> userInfoMap) throws Exception {
        if (Objects.isNull(contractId)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }

        ResultCode resultCode = ResultCode.success();
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByContractId(contractId);
        if (!CollectionUtils.isEmpty(list)) {
            Predicate<ContractOrderDO> isNotEnforce = contractOrderDO -> contractOrderDO.getOrderType() != OrderTypeEnum.ENFORCE.getCode();
            Predicate<ContractOrderDO> isCommit = contractOrderDO -> contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode();
            Predicate<ContractOrderDO> isPartMatch = contractOrderDO -> contractOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode();
            Map<Long, List<Long>> orderMap = list.stream()
                    .filter(isCommit.or(isPartMatch).and(isNotEnforce))
                    .collect(groupingBy(ContractOrderDO::getUserId, mapping(ContractOrderDO::getId, toList())));

            for (Map.Entry<Long, List<Long>> entry : orderMap.entrySet()) {
                sendCancelMessage(entry.getValue(), entry.getKey());
            }
        }

        return resultCode;
    }

    public ResultCode cancelOrderByOrderType(long userId, List<Integer> orderTypes, Map<String, String> userInfoMap) throws Exception {
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.listByUserIdAndOrderType(userId, orderTypes);
        if (!CollectionUtils.isEmpty(list)) {
            Predicate<ContractOrderDO> isNotEnforce = contractOrderDO -> contractOrderDO.getOrderType() != OrderTypeEnum.ENFORCE.getCode();
            Predicate<ContractOrderDO> isCommit = contractOrderDO -> contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode();
            Predicate<ContractOrderDO> isPartMatch = contractOrderDO -> contractOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode();
            List<Long> orderDOList = list.stream()
                    .filter(isCommit.or(isPartMatch).and(isNotEnforce))
                    .map(ContractOrderDO::getId)
                    .collect(toList());

            sendCancelMessage(orderDOList, userId);
        }

        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }


    @Transactional(rollbackFor = Exception.class)
    public com.fota.common.Result<Long> placeOrder(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap) throws Exception{
        Profiler profiler = new Profiler("ContractOrderManager.placeOrder");
        ContractOrderDO contractOrderDO = com.fota.trade.common.BeanUtils.copy(contractOrderDTO);
        String username = StringUtils.isEmpty(userInfoMap.get("username")) ? "" : userInfoMap.get("username");
        String ipAddress = StringUtils.isEmpty(userInfoMap.get("ip")) ? "" : userInfoMap.get("ip");
        com.fota.common.Result<Long> result = new com.fota.common.Result<Long>();
        Map<String, Object> newMap = new HashMap<>();
        if (contractOrderDTO.getOrderContext() !=null){
            newMap = contractOrderDTO.getOrderContext();
        }
        newMap.put("username", username);
        contractOrderDTO.setOrderContext(newMap);
        contractOrderDO.setOrderContext(JSONObject.toJSONString(contractOrderDTO.getOrderContext()));
        Long orderId = 0L;
        long transferTime = System.currentTimeMillis();
        contractOrderDO.setGmtCreate(new Date(transferTime));
        contractOrderDO.setGmtModified(new Date(transferTime));
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null) {
            log.error("Contract Is Null");
            throw new RuntimeException("Contract Is Null");
        }
        if (contractCategoryDO.getStatus() == ContractStatusEnum.ROOLING_BACK.getCode()){
            result.setCode(ResultCodeEnum.CONTRACT_IS_ROLLING_BACK.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_IS_ROLLING_BACK.getMessage());
            result.setData(orderId);
            return result;
        }else if(contractCategoryDO.getStatus() == ContractStatusEnum.DELIVERYING.getCode()){
            result.setCode(ResultCodeEnum.CONTRACT_IS_DELIVERYING.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_IS_DELIVERYING.getMessage());
            result.setData(orderId);
            return result;
        }else if(contractCategoryDO.getStatus() == ContractStatusEnum.DELIVERED.getCode()){
            result.setCode(ResultCodeEnum.CONTRACT_HAS_DELIVERIED.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_HAS_DELIVERIED.getMessage());
            result.setData(orderId);
            return result;
        }else if(contractCategoryDO.getStatus() == ContractStatusEnum.PROCESSING.getCode()){
        }else {
            result.setCode(ResultCodeEnum.CONTRACT_STATUS_ILLEGAL.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_STATUS_ILLEGAL.getMessage());
            result.setData(orderId);
            return result;
        }
        if (contractOrderDO.getCloseType() == null){
            contractOrderDO.setCloseType(OrderCloseTypeEnum.MANUAL.getCode());
        }
        contractOrderDO.setContractName(contractCategoryDO.getContractName());
        if (contractOrderDO.getOrderType() == null){
            contractOrderDO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        }
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            insertOrderRecord(contractOrderDO);
        } else {
            insertOrderRecord(contractOrderDO);
            profiler.complelete("select and insert record");
            orderId = contractOrderDO.getId();
            Map<String,BigDecimal> msg  = getAccountMsg(contractOrderDO.getUserId());
            log.info("AccountDetailMsg:"+msg.toString());
            BigDecimal entrustLock = getEntrustMargin(contractOrderDO.getUserId());
            if (entrustLock == null){
                log.error("get CurrentPrice failed");
                throw new RuntimeException("get CurrentPrice failed");
            }
            BigDecimal positionMargin = msg.get(Constant.POSITION_MARGIN);
            BigDecimal floatPL = msg.get(Constant.FLOATING_PL);
            //查询用户合约冻结金额 判断可用是否大于委托冻结
            UserContractDTO userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
            BigDecimal useableAmount = new BigDecimal(userContractDTO.getAmount()).add(floatPL).subtract(positionMargin);
            profiler.complelete("compute account status");
            if (useableAmount.compareTo(entrustLock) < 0) {
                log.error("ContractAccount USDK Not Enough");
                profiler.log();
                throw new BusinessException(ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(), ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
            }
        }
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(0L);
        contractOrderDTO.setContractId(contractOrderDO.getContractId());
        redisManager.contractOrderSave(contractOrderDTO);
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            // 强平单
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    2, contractOrderDTO.getContractName(), username, ipAddress, contractOrderDTO.getTotalAmount(),
                    System.currentTimeMillis(), 3, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 2);
        } else {
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    2, contractOrderDTO.getContractName(), username, ipAddress, contractOrderDTO.getTotalAmount(),
                    System.currentTimeMillis(), 1, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 1);
        }
        //推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setAmount(new BigDecimal(contractOrderDTO.getUnfilledAmount()));
        if (contractOrderDTO.getPrice() != null){
            orderMessage.setPrice(contractOrderDTO.getPrice());
        }
        orderMessage.setOrderDirection(contractOrderDTO.getOrderDirection());
        orderMessage.setOrderType(contractOrderDTO.getOrderType());
        orderMessage.setTransferTime(transferTime);
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId());
        orderMessage.setSubjectName(contractOrderDO.getContractName());
        orderMessage.setContractType(contractCategoryDO.getContractType());
        orderMessage.setContractMatchAssetName(contractCategoryDO.getAssetName());
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder",String.valueOf(contractOrderDTO.getId()), orderMessage);
        if (!sendRet) {
            log.error("Send RocketMQ Message Failed ");
        }
        profiler.complelete("send MQ message");
        profiler.log();
        result.setCode(0);
        result.setMessage("success");
        result.setData(orderId);
        return result;
    }

    public ResultCode cancelOrder(Long userId, Long orderId, Map<String, String> userInfoMap) throws Exception {
        if (Objects.isNull(userId) || Objects.isNull(orderId)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByPrimaryKey(orderId);
        if (Objects.isNull(contractOrderDO)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            return ResultCode.error(ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getCode(),
                    ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getMessage());
        }
        ResultCode resultCode = ResultCode.success();
        List<Long> orderIdList = Collections.singletonList(orderId);
        sendCancelMessage(orderIdList, userId);
        return resultCode;
    }

    /**
     * 根据撮合发出的MQ消息撤单
     * @param orderId 委托单ID
     * @param status 撮合队列撤单结果 1-成功 0-失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByMessage(String orderId, int status) {
        if (status == 1) {
            ContractOrderDO contractOrderDO = contractOrderMapper.selectByPrimaryKey(Long.parseLong(orderId));
            if (Objects.isNull(contractOrderDO)) {
                log.error("contract order does not exist, {}", orderId);
                return;
            }
            cancelOrderImpl(contractOrderDO, Collections.emptyMap());
        } else {
            log.warn("failed to cancel order {}", orderId);
        }
    }

    public ResultCode cancelOrderImpl(ContractOrderDO contractOrderDO, Map<String, String> userInfoMap) {
        if (OrderTypeEnum.ENFORCE.getCode() == contractOrderDO.getOrderType()) {
            log.error("enforce order can't be canceled, {}", contractOrderDO.getId());
            throw new RuntimeException("enforce order can't be canceled");
        }
        String username = StringUtils.isEmpty(userInfoMap.get("username")) ? "" : userInfoMap.get("username");
        String ipAddress = StringUtils.isEmpty(userInfoMap.get("ip")) ? "" : userInfoMap.get("ip");
        ResultCode resultCode = new ResultCode();
        Integer status = contractOrderDO.getStatus();
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
            log.error("Contract Is Null");
            throw new RuntimeException("Contract Is Null");
        }
        if (contractCategoryDO.getStatus() != ContractStatusEnum.PROCESSING.getCode() && contractCategoryDO.getStatus() != ContractStatusEnum.DELIVERYING.getCode()
                && contractCategoryDO.getStatus() != ContractStatusEnum.DELIVERED.getCode()){
            log.error("contract status illegal,can not cancel{}", contractCategoryDO);
            throw new RuntimeException("contractCategoryDO");
        }
        if (status == OrderStatusEnum.COMMIT.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.CANCEL.getCode());
        } else if (status == OrderStatusEnum.PART_MATCH.getCode()) {
            contractOrderDO.setStatus(OrderStatusEnum.PART_CANCEL.getCode());
        } else if (status == OrderStatusEnum.MATCH.getCode() || status == OrderStatusEnum.PART_CANCEL.getCode() || status == OrderStatusEnum.CANCEL.getCode()) {
            log.error("order has completed{}", contractOrderDO);
            throw new RuntimeException("order has completed");
        } else {
            log.error("order status illegal{}", contractOrderDO);
            throw new RuntimeException("order status illegal");
        }
        Long transferTime = System.currentTimeMillis();
        log.info("------------contractCancelStartTimeStamp"+System.currentTimeMillis());
        int ret = contractOrderMapper.updateByOpLock(contractOrderDO);
        log.info("------------contractCancelEndTimeStamp"+System.currentTimeMillis());
        if (ret > 0) {
            /*UserContractDTO userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
            BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
            BigDecimal totalLockAmount = getAccountDetailMsg(contractOrderDO.getUserId()).get(Constant.ENTRUST_MARGIN);
            if (lockedAmount.compareTo(totalLockAmount) != 0) {
                lockContractAccount(userContractDTO, totalLockAmount);
            }*/
        } else {
            log.error("update contractOrder Failed{}", contractOrderDO);
            throw new RuntimeException("update contractOrder Failed");
        }
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO);
        contractOrderDTO.setCompleteAmount(contractOrderDTO.getTotalAmount() - contractOrderDTO.getUnfilledAmount());
        contractOrderDTO.setContractId(contractOrderDO.getContractId());
        redisManager.contractOrderSave(contractOrderDTO);
        tradeLog.info("cancelorder@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, contractOrderDTO.getContractName(), username, ipAddress, contractOrderDTO.getUnfilledAmount(),
                System.currentTimeMillis(), 2, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 1);
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setAmount(new BigDecimal(contractOrderDTO.getUnfilledAmount()));
        orderMessage.setPrice(contractOrderDTO.getPrice());
        orderMessage.setTransferTime(transferTime);
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId());
        orderMessage.setSubjectName(contractOrderDO.getContractName());
        orderMessage.setOrderDirection(contractOrderDO.getOrderDirection());
        orderMessage.setContractType(contractCategoryDO.getContractType());
        orderMessage.setContractMatchAssetName(contractCategoryDO.getAssetName());
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder", String.valueOf(contractOrderDTO.getId())+contractOrderDTO.getStatus(), orderMessage);
        if (!sendRet) {
            log.error("Send RocketMQ Message Failed ");
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }

    public void sendCancelMessage(List<Long> orderIdList, Long userId) {
        //发送MQ消息到match
        Map<String, Object> map = new HashMap<>();
        map.putIfAbsent("userId", userId);
        map.putIfAbsent("idList", orderIdList);
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractCancel",
                userId + String.valueOf(System.currentTimeMillis()), map);
        if (BooleanUtils.isNotTrue(sendRet)){
            log.error("failed to send cancel contract mq, {}", userId);
        }
    }

    public ResultCode cancelAllOrder(Long userId, Map<String, String> userInfoMap) throws Exception {
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByUserId(userId);
        if (list != null){
            List<Long> orderIdList = list.stream()
                    .map(ContractOrderDO::getId)
                    .collect(toList());

            sendCancelMessage(orderIdList, userId);
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }

    //获取实时持仓保证金、实时浮盈亏金
    public Map<String, BigDecimal> getAccountMsg(long userId) {
        Map<String, BigDecimal> resultMap = new HashMap<String, BigDecimal>();
        //获取所有合约类型列表
        BigDecimal positionMargin = BigDecimal.ZERO;
        BigDecimal floatingPL = BigDecimal.ZERO;
        List<ContractCategoryDO> queryList = contractCategoryMapper.getAllContractCategory();
        List<UserPositionDO> positionlist = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        if (queryList != null && queryList.size() != 0 && positionlist != null && positionlist.size() != 0) {
            for (ContractCategoryDO contractCategoryDO : queryList) {
                long contractId = contractCategoryDO.getId();
                List<UserPositionDO> userPositionDOlist = new ArrayList<>();
                if (positionlist != null && positionlist.size() != 0) {
                    userPositionDOlist = positionlist.stream().filter(userPosition -> userPosition.getContractId().equals(contractCategoryDO.getId()))
                            .limit(1).collect(toList());
                    if (userPositionDOlist != null && userPositionDOlist.size() != 0) {
                        UserPositionDO userPositionDO = userPositionDOlist.get(0);
                        //获取买一卖一价
                        BigDecimal askCurrentPrice = BigDecimal.ZERO;
                        BigDecimal bidCurrentPrice = BigDecimal.ZERO;
                        try{
                            List<CompetitorsPriceDTO> competitorsPriceList = realTimeEntrust.getContractCompetitorsPrice();
                            askCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.ASK.getCode() &&
                                    competitorsPrice.getId() == contractId).findFirst().get().getPrice();
                            bidCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.BID.getCode() &&
                                    competitorsPrice.getId() == contractId).findFirst().get().getPrice();
                            log.info("askCurrentPrice:{}",askCurrentPrice);
                            log.info("bidCurrentPrice:{}",bidCurrentPrice);
                        }catch (Exception e){
                            log.error("getContractBuyPriceSellPriceDTO failed{}",e);
                        }
                        BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId, contractId));
                        Integer positionType = userPositionDO.getPositionType();
                        BigDecimal positionUnfilledAmount = new BigDecimal(userPositionDO.getUnfilledAmount());
                        BigDecimal contractSize = contractCategoryDO.getContractSize();
                        BigDecimal positionAveragePrice = userPositionDO.getAveragePrice();
                        if (positionType == PositionTypeEnum.OVER.getCode()) {
                            BigDecimal bidPositionEntrustAmount = positionUnfilledAmount.multiply(contractSize).multiply(bidCurrentPrice).divide(lever, 8, BigDecimal.ROUND_DOWN);
                            positionMargin = positionMargin.add(bidPositionEntrustAmount);
                            floatingPL = floatingPL.add((bidCurrentPrice.subtract(positionAveragePrice)).multiply(positionUnfilledAmount).multiply(contractSize)).setScale(8, BigDecimal.ROUND_DOWN);
                        } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
                            BigDecimal askPositionEntrustAmount = positionUnfilledAmount.multiply(contractSize).multiply(askCurrentPrice).divide(lever, 8, BigDecimal.ROUND_DOWN);
                            positionMargin = positionMargin.add(askPositionEntrustAmount);
                            floatingPL = floatingPL.add((positionAveragePrice.subtract(askCurrentPrice)).multiply(positionUnfilledAmount).multiply(contractSize)).setScale(8, BigDecimal.ROUND_DOWN);
                        }
                    }
                }
            }
        }
        resultMap.put(Constant.POSITION_MARGIN, positionMargin);
        resultMap.put(Constant.FLOATING_PL, floatingPL);
        return resultMap;
    }

    //获取实时委托冻结
    public BigDecimal getEntrustMargin(long userId) {
        Map<String, BigDecimal> resultMap = new HashMap<String, BigDecimal>();
        //获取所有合约类型列表
        BigDecimal entrustMargin = BigDecimal.ZERO;
        //TODO 过期就不在这个表了？
        List<ContractCategoryDO> queryList = contractCategoryMapper.getAllContractCategory();
        List<UserPositionDO> positionlist = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        List<ContractOrderDO> contractOrderlist = contractOrderMapper.selectNotEnforceOrderByUserId(userId);

        if (queryList != null && queryList.size() != 0 && contractOrderlist != null && contractOrderlist.size() != 0) {
            log.info("selectUnfinishedOrderByUserId {} contractOrderlist size {}, contractOrderlist {}", userId, contractOrderlist.size(), contractOrderlist.get(0).toString());

            for (ContractCategoryDO contractCategoryDO : queryList) {
                BigDecimal entrustLockAmount = BigDecimal.ZERO;
                long contractId = contractCategoryDO.getId();
                List<ContractOrderDO> orderList = contractOrderlist.stream().filter(contractOrder -> contractOrder.getContractId().equals(contractCategoryDO.getId()))
                        .collect(toList());
                if (orderList != null && orderList.size() != 0) {
                    List<ContractOrderDO> bidList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.BID.getCode()).collect(toList());
                    List<ContractOrderDO> askList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.ASK.getCode()).collect(toList());
                    List<UserPositionDO> userPositionDOlist = new ArrayList<>();
                    if (positionlist != null && positionlist.size() != 0) {
                        userPositionDOlist = positionlist.stream().filter(userPosition -> userPosition.getContractId().equals(contractCategoryDO.getId()))
                                .limit(1).collect(toList());
                        if (userPositionDOlist != null && userPositionDOlist.size() != 0) {
                            UserPositionDO userPositionDO = userPositionDOlist.get(0);
                            BigDecimal totalAskExtraEntrustAmount = BigDecimal.ZERO;
                            BigDecimal totalBidExtraEntrustAmount = BigDecimal.ZERO;
                            //获取买一卖一价
                            BigDecimal askCurrentPrice = BigDecimal.ZERO;
                            BigDecimal bidCurrentPrice = BigDecimal.ZERO;
                            try{
                                List<CompetitorsPriceDTO> competitorsPriceList = realTimeEntrust.getContractCompetitorsPrice();
                                askCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.ASK.getCode() &&
                                        competitorsPrice.getId() == contractId).findFirst().get().getPrice();
                                bidCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.BID.getCode() &&
                                        competitorsPrice.getId() == contractId).findFirst().get().getPrice();
                                log.info("askCurrentPrice:{}",askCurrentPrice);
                                log.info("bidCurrentPrice:{}",bidCurrentPrice);
                            }catch (Exception e){
                                log.error("getContractBuyPriceSellPriceDTO failed{}",e);
                                return null;
                            }
                            BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId, contractId));
                            Integer positionType = userPositionDO.getPositionType();
                            BigDecimal positionUnfilledAmount = new BigDecimal(userPositionDO.getUnfilledAmount());
                            BigDecimal contractSize = contractCategoryDO.getContractSize();
                            BigDecimal positionAveragePrice = userPositionDO.getAveragePrice();
                            if (positionType == PositionTypeEnum.OVER.getCode()) {
                                BigDecimal bidPositionEntrustAmount = positionUnfilledAmount.multiply(contractSize).multiply(bidCurrentPrice).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                totalAskExtraEntrustAmount = totalAskExtraEntrustAmount.add(getExtraEntrustAmount(bidList, askList, positionType, positionUnfilledAmount, bidPositionEntrustAmount, lever, contractSize));
                            } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
                                BigDecimal askPositionEntrustAmount = positionUnfilledAmount.multiply(contractSize).multiply(askCurrentPrice).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                totalBidExtraEntrustAmount = totalBidExtraEntrustAmount.add(getExtraEntrustAmount(bidList, askList, positionType, positionUnfilledAmount, askPositionEntrustAmount, lever, contractSize));
                            }
                            entrustLockAmount = entrustLockAmount.add(totalBidExtraEntrustAmount.add(totalAskExtraEntrustAmount));
                        }
                    }
                    if (positionlist == null || positionlist.size() == 0 || userPositionDOlist == null || userPositionDOlist.size() == 0) {
                        BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId, contractId));
                        BigDecimal orderValue;
                        BigDecimal orderFee;
                        BigDecimal toltalBidEntrustAmount = BigDecimal.ZERO;
                        BigDecimal toltalAskEntrustAmount = BigDecimal.ZERO;
                        if (bidList != null && bidList.size() != 0) {
                            for (ContractOrderDO bidOrder : bidList) {
                                orderValue = bidOrder.getPrice().multiply(contractCategoryDO.getContractSize()).multiply(new BigDecimal(bidOrder.getUnfilledAmount())).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                orderFee = orderValue.multiply(lever).multiply(Constant.FEE_RATE);
                                toltalBidEntrustAmount = toltalBidEntrustAmount.add(orderValue).add(orderFee);
                            }
                        }
                        if (askList != null && askList.size() != 0) {
                            for (ContractOrderDO askOrder : askList) {
                                orderValue = askOrder.getPrice().multiply(contractCategoryDO.getContractSize()).multiply(new BigDecimal(askOrder.getUnfilledAmount())).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                orderFee = orderValue.multiply(lever).multiply(Constant.FEE_RATE);
                                toltalAskEntrustAmount = toltalAskEntrustAmount.add(orderValue).add(orderFee);
                            }
                        }
                        if (toltalBidEntrustAmount.compareTo(toltalAskEntrustAmount) > 0) {
                            entrustLockAmount = toltalBidEntrustAmount;
                        } else {
                            entrustLockAmount = toltalAskEntrustAmount;
                        }
                    }
                }
                entrustMargin = entrustMargin.add(entrustLockAmount);
            }
        }
        return entrustMargin;
    }

    //获取多空仓额外保证金
    public BigDecimal getExtraEntrustAmount(List<ContractOrderDO> bidList, List<ContractOrderDO> askList, Integer positionType,
                                            BigDecimal positionUnfilledAmount, BigDecimal positionEntrustAmount, BigDecimal lever, BigDecimal contractSize) {
        BigDecimal max1 = BigDecimal.ZERO;
        BigDecimal max2 = BigDecimal.ZERO;
        BigDecimal totalAskEntrustAmount = BigDecimal.ZERO;
        BigDecimal totalBidEntrustAmount = BigDecimal.ZERO;
        BigDecimal askEntrustAmount = BigDecimal.ZERO;
        BigDecimal bidEntrustAmount = BigDecimal.ZERO;
        if (positionType == PositionTypeEnum.OVER.getCode()) {
            if (askList != null && askList.size() != 0) {
                List<ContractOrderDO> sortedAskList = sortListEsc(askList);
                for (int i = 0; i < sortedAskList.size(); i++) {
                    positionUnfilledAmount = positionUnfilledAmount.subtract(new BigDecimal(sortedAskList.get(i).getUnfilledAmount()));
                    if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0) {
                        BigDecimal restAmount = positionUnfilledAmount.negate().multiply(contractSize).multiply(sortedAskList.get(i).getPrice()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                        BigDecimal restFee = restAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        BigDecimal totalRest = restAmount.add(restFee);
                        for (int j = i + 1; j < sortedAskList.size(); j++) {
                            BigDecimal orderAmount = sortedAskList.get(j).getPrice().multiply(contractSize).multiply(new BigDecimal(sortedAskList.get(j).getUnfilledAmount())).divide(lever, 8, BigDecimal.ROUND_DOWN);
                            BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                            askEntrustAmount = askEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalAskEntrustAmount = totalRest.add(askEntrustAmount);
                        break;
                    }
                }
                if (totalAskEntrustAmount.compareTo(positionEntrustAmount) > 0) {
                    max1 = totalAskEntrustAmount.subtract(positionEntrustAmount);
                }
            }
            if (bidList != null && bidList.size() != 0) {
                for (int i = 0; i < bidList.size(); i++) {
                    BigDecimal orderAmount = bidList.get(i).getPrice().multiply(contractSize).multiply(new BigDecimal(bidList.get(i).getUnfilledAmount())).divide(lever, 8, BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                    totalBidEntrustAmount = totalBidEntrustAmount.add(orderAmount.add(orderFee));
                }
                if (totalBidEntrustAmount.compareTo(max1) > 0) {
                    max2 = totalBidEntrustAmount;
                    return max2;
                }
            }
            return max1;
        } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
            if (bidList != null && bidList.size() != 0) {
                List<ContractOrderDO> sortedBidList = sortListDesc(bidList);
                for (int i = 0; i < sortedBidList.size(); i++) {
                    positionUnfilledAmount = positionUnfilledAmount.subtract(new BigDecimal(sortedBidList.get(i).getUnfilledAmount()));
                    if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0) {
                        BigDecimal restAmount = positionUnfilledAmount.negate().multiply(contractSize).multiply(sortedBidList.get(i).getPrice()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                        BigDecimal restFee = restAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        BigDecimal totalRest = restAmount.add(restFee);
                        for (int j = i + 1; j < sortedBidList.size(); j++) {
                            BigDecimal orderAmount = sortedBidList.get(j).getPrice().multiply(contractSize).multiply(new BigDecimal(sortedBidList.get(j).getUnfilledAmount())).divide(lever, 8, BigDecimal.ROUND_DOWN);
                            BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                            bidEntrustAmount = bidEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalBidEntrustAmount = totalRest.add(bidEntrustAmount);
                        break;
                    }
                }
                if (totalBidEntrustAmount.compareTo(positionEntrustAmount) > 0) {
                    max1 = totalBidEntrustAmount.subtract(positionEntrustAmount);
                }
            }
            if (askList != null && askList.size() != 0) {
                for (int i = 0; i < askList.size(); i++) {
                    BigDecimal orderAmount = askList.get(i).getPrice().multiply(contractSize).multiply(new BigDecimal(askList.get(i).getUnfilledAmount())).divide(lever, 8, BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                    totalAskEntrustAmount = totalAskEntrustAmount.add(orderAmount.add(orderFee));
                }
                if (totalAskEntrustAmount.compareTo(max1) > 0) {
                    max2 = totalAskEntrustAmount;
                    return max2;
                }
            }
            return max1;
        } else {
            throw new RuntimeException("positionType illegal");
        }
    }


    public void insertOrderRecord(ContractOrderDO contractOrderDO){
        contractOrderDO.setStatus(8);
        contractOrderDO.setFee(Constant.FEE_RATE);
        contractOrderDO.setId(com.fota.trade.util.CommonUtils.generateId());
        contractOrderDO.setUnfilledAmount(contractOrderDO.getTotalAmount());
        int insertContractOrderRet = contractOrderMapper.insert(contractOrderDO);
        if (insertContractOrderRet <= 0) {
            log.error("insert contractOrder failed");
            throw new RuntimeException("insert contractOrder failed");
        }
    }

    //冻结合约账户金额
    public void lockContractAccount(UserContractDTO userContractDTO, BigDecimal totalLockAmount) throws Exception {
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        if (amount.compareTo(totalLockAmount) < 0) {
            log.error("ContractAccount USDK Not Enough");
            throw new BusinessException(ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(), ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
        }
        //调用RPC接口冻结合约账户（加锁）
        BigDecimal addLockAmount = totalLockAmount.subtract(new BigDecimal(userContractDTO.getLockedAmount()));
        long gmtModified = userContractDTO.getGmtModified().getTime();
        Boolean lockContractAmountRet = getContractService().lockContractAmount(userContractDTO.getUserId(), addLockAmount.toString(), gmtModified);
        if (!lockContractAmountRet) {
            log.error("update contract account lockedAmount failed");
            throw new RuntimeException("update contract account lockedAmount failed");
        }
    }

    //升序排列
    public List<ContractOrderDO> sortListEsc(List<ContractOrderDO> list) {
        List<ContractOrderDO> sortedList = list.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice))
                .collect(toList());
        return sortedList;
    }

    //降序排列
    public List<ContractOrderDO> sortListDesc(List<ContractOrderDO> list) {
        List<ContractOrderDO> sortedList = list.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice).reversed())
                .collect(toList());
        return sortedList;
    }

    public BigDecimal getContractSize(long contractId) {
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.getContractCategoryById(contractId);
        BigDecimal contractSize = contractCategoryDO.getContractSize();
        return contractSize;
    }

    public BigDecimal getTotalLockAmount(ContractOrderDO contractOrderDO) {
        Integer lever = contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(), contractOrderDO.getContractId());
        BigDecimal totalValue = contractOrderDO.getPrice().multiply(new BigDecimal(contractOrderDO.getTotalAmount()))
                .multiply(new BigDecimal(0.01)).divide(new BigDecimal(lever), 8, BigDecimal.ROUND_DOWN);
        BigDecimal fee = totalValue.multiply(Constant.FEE_RATE).multiply(new BigDecimal(lever));
        return totalValue.add(fee);
    }


    //成交
    @Transactional(rollbackFor = {Exception.class})
    public ResultCode updateOrderByMatch(ContractMatchedOrderDTO contractMatchedOrderDTO) throws InterruptedException {
        Profiler profiler = new Profiler("ContractOrderManager.updateOrderByMatch");
        ResultCode resultCode = new ResultCode();
        if (contractMatchedOrderDTO == null) {
            log.error(ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        ContractOrderDO askContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getAskOrderId());
        ContractOrderDO bidContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getBidOrderId());

        log.info("-------------------askContractOrder:"+askContractOrder);
        log.info("-------------------bidContractOrder:"+bidContractOrder);
        if (askContractOrder == null){
            log.error("askContractOrder not exist");
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        if (bidContractOrder == null){
            log.error("bidOrderContext not exist");
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }

        if (askContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0
                || bidContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0) {
            log.error("unfilledAmount not enough{}",contractMatchedOrderDTO);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        if (askContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()) {
            log.error("ask order status illegal{}", askContractOrder);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        if (bidContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()) {
            log.error("bid order status illegal{}", bidContractOrder);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        long filledAmount = contractMatchedOrderDTO.getFilledAmount();
        BigDecimal filledPrice = new BigDecimal(contractMatchedOrderDTO.getFilledPrice());
        Integer askLever = contractLeverManager.getLeverByContractId(askContractOrder.getUserId(), askContractOrder.getContractId());
        askContractOrder.setLever(askLever.intValue());

        Integer bidLever = contractLeverManager.getLeverByContractId(bidContractOrder.getUserId(), bidContractOrder.getContractId());
        bidContractOrder.setLever(bidLever);

        askContractOrder.fillAmount(filledAmount);
        bidContractOrder.fillAmount(filledAmount);
        Long transferTime = System.currentTimeMillis();
        //更新委托
        updateContractOrder(contractMatchedOrderDTO.getAskOrderId(), filledAmount, filledPrice, new Date(transferTime));
        updateContractOrder(contractMatchedOrderDTO.getBidOrderId(), filledAmount, filledPrice, new Date(transferTime));
        profiler.complelete("update contract order");

        BigDecimal contractSize = getContractSize(contractMatchedOrderDTO.getContractId());
        //更新持仓
        UpdatePositionResult askResult = updatePosition(askContractOrder, contractSize, filledAmount, filledPrice);
        UpdatePositionResult bidResult = updatePosition(bidContractOrder, contractSize, filledAmount, filledPrice);
        profiler.complelete("update position");

        ContractDealer dealer1 = calBalanceChange(askContractOrder, filledAmount, filledPrice, contractSize, askResult, askLever);
        ContractDealer dealer2 = calBalanceChange(bidContractOrder, filledAmount, filledPrice, contractSize, bidResult, bidLever);
        com.fota.common.Result result = contractService.updateBalances(dealer1, dealer2);
        profiler.complelete("update balance");
        if (!result.isSuccess()) {
            throw new RuntimeException("update balance failed");
        }

        ContractMatchedOrderDO contractMatchedOrderDO = com.fota.trade.common.BeanUtils.copy(contractMatchedOrderDTO);
        BigDecimal fee = contractMatchedOrderDO.getFilledAmount().multiply(contractMatchedOrderDO.getFilledPrice()).multiply(contractSize).multiply(Constant.FEE_RATE).setScale(CommonUtils.scale, BigDecimal.ROUND_UP);
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
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                postProcessAfterMatch(askContractOrder, bidContractOrder, contractMatchedOrderDO, transferTime, contractMatchedOrderDTO);
            }
        });
        profiler.complelete("persistMatch");
        profiler.log();
        resultCode.setCode(ResultCodeEnum.SUCCESS.getCode());
        return resultCode;
    }

    private void saveToRedis(ContractOrderDO contractOrderDO,  long completeAmount,  Map<String, Object> orderContext, long matchId){

        //TODO 并发考虑
        // 状态为9
        if (contractOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode()) {
            redisManager.contractOrderSaveForMatch(com.fota.trade.common.BeanUtils.copy(contractOrderDO));
        } else if (contractOrderDO.getStatus() == OrderStatusEnum.MATCH.getCode()) {
            redisManager.hdel(Constant.REDIS_CONTRACT_ORDER_FOR_MATCH_HASH, String.valueOf(contractOrderDO.getId()));
        }

        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO);
        contractOrderDTO.setCompleteAmount(completeAmount);
        contractOrderDTO.setOrderContext(orderContext);
        redisManager.contractOrderSave(contractOrderDTO);
        String userName = "";
        if (orderContext != null){
            userName = orderContext.get("username") == null ? "": String.valueOf(orderContext.get("username"));
        }
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, contractOrderDTO.getContractName(), userName, contractOrderDTO.getMatchAmount(), System.currentTimeMillis(), 4, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(),matchId);


    }

    public void postProcessAfterMatch(ContractOrderDO contractOrderDO1, ContractOrderDO contractOrderDO2, ContractMatchedOrderDO contractMatchedOrderDO,
                                     long updateOrderTime, ContractMatchedOrderDTO contractMatchedOrderDTO){

        ContractOrderDO askContractOrder = contractOrderMapper.selectByPrimaryKey(contractOrderDO1.getId());
        ContractOrderDO bidContractOrder = contractOrderMapper.selectByPrimaryKey(contractOrderDO2.getId());


        Map<String, Object> askOrderContext = new HashMap<>();
        Map<String, Object> bidOrderContext = new HashMap<>();
        if (askContractOrder.getOrderContext() != null){
            askOrderContext  = JSON.parseObject(askContractOrder.getOrderContext());
        }
        if (bidContractOrder.getOrderContext() != null){
            bidOrderContext  = JSON.parseObject(bidContractOrder.getOrderContext());
        }

        long filledAmount = contractMatchedOrderDO.getFilledAmount().longValue();
        //存入Redis缓存 有相关撮合
        saveToRedis(askContractOrder, filledAmount, askOrderContext, contractMatchedOrderDO.getId());
        saveToRedis(bidContractOrder, filledAmount, bidOrderContext, contractMatchedOrderDO.getId());

        // 向MQ推送消息
        // 通过contractId去trade_contract_category表里面获取asset_name和contract_type
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.getContractCategoryById(askContractOrder.getContractId());
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setSubjectId(contractMatchedOrderDO.getContractId());
        orderMessage.setSubjectName(contractMatchedOrderDO.getContractName());
        orderMessage.setTransferTime(updateOrderTime);
        orderMessage.setPrice(contractMatchedOrderDO.getFilledPrice());
        orderMessage.setAmount(new BigDecimal(filledAmount));
        orderMessage.setEvent(OrderOperateTypeEnum.DEAL_ORDER.getCode());
        orderMessage.setAskOrderId(askContractOrder.getId());
        orderMessage.setBidOrderId(bidContractOrder.getId());
        orderMessage.setAskUserId(askContractOrder.getUserId());
        orderMessage.setBidUserId(bidContractOrder.getUserId());
        orderMessage.setFee(contractMatchedOrderDO.getFee());
        orderMessage.setMatchOrderId(contractMatchedOrderDO.getId());
        orderMessage.setContractMatchAssetName(contractCategoryDO.getAssetName());
        orderMessage.setContractType(contractCategoryDO.getContractType());
        orderMessage.setAskOrderUnfilledAmount(contractMatchedOrderDTO.getAskOrderUnfilledAmount());
        orderMessage.setBidOrderUnfilledAmount(contractMatchedOrderDTO.getBidOrderUnfilledAmount());
        orderMessage.setMatchType(contractMatchedOrderDTO.getMatchType());
        //orderMessage.setAskOrderType(askContractOrderDTO.getOrderType());
        //orderMessage.setBidOrderType(bidContractOrderDTO.getOrderType());
        if (askContractOrder.getPrice() != null){
            orderMessage.setAskOrderEntrustPrice(askContractOrder.getPrice());
        }
        if (bidContractOrder.getPrice() != null){
            orderMessage.setBidOrderEntrustPrice(bidContractOrder.getPrice());
        }
        Boolean sendRet = rocketMqManager.sendMessage("match", "contract", String.valueOf(contractMatchedOrderDO.getId()), orderMessage);
        if (!sendRet) {
            log.error("Send RocketMQ Message Failed ");
        }

        // 向Redis存储消息
        ContractMatchedOrderTradeDTO contractMatchedOrderTradeDTO = new ContractMatchedOrderTradeDTO();
        contractMatchedOrderTradeDTO.setContractMatchedOrderId(contractMatchedOrderDO.getId());
        contractMatchedOrderTradeDTO.setAskOrderId(contractMatchedOrderDO.getAskOrderId());
        contractMatchedOrderTradeDTO.setBidOrderId(contractMatchedOrderDO.getBidOrderId());
        contractMatchedOrderTradeDTO.setFilledAmount(contractMatchedOrderDO.getFilledAmount());
        contractMatchedOrderTradeDTO.setFilledPrice(contractMatchedOrderDO.getFilledPrice());
        contractMatchedOrderTradeDTO.setFilledDate(contractMatchedOrderDO.getGmtCreate());
        contractMatchedOrderTradeDTO.setMatchType((int) contractMatchedOrderDO.getMatchType());
        contractMatchedOrderTradeDTO.setContractId(askContractOrder.getContractId());
        contractMatchedOrderTradeDTO.setContractName(contractMatchedOrderDO.getContractName());
        try {
            String key = Constant.CACHE_KEY_MATCH_CONTRACT + contractMatchedOrderDO.getId();
            Object value = JSONObject.toJSONString(contractMatchedOrderTradeDTO);
            log.info("向Redis存储消息,key:{},value:{}", key, value);
            boolean re = redisManager.set(key, value);
            if (!re) {
                log.error("向Redis存储消息失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("向Redis存储USDK撮合订单信息失败，订单id为 {}", contractMatchedOrderDO.getId());
        }
    }

    public UpdatePositionResult updatePosition(ContractOrderDO contractOrderDO, BigDecimal contractSize, long filledAmount, BigDecimal filledPrice){
        long userId = contractOrderDO.getUserId();
        long contractId = contractOrderDO.getContractId();
        UpdatePositionResult result = new UpdatePositionResult();
        boolean suc = false;
        for (int i=0;i<MAX_UPDATE_RETRIES;i++)
        {
            UserPositionDO userPositionDO;
            userPositionDO = userPositionMapper.selectForUpdateByUserId(userId, contractId);
            if (userPositionDO == null) {
                // 建仓
                userPositionDO = ContractUtils.buildPosition(contractOrderDO, contractSize, contractOrderDO.getLever(), filledAmount, filledPrice);

                suc = insertPosition(userPositionDO);
                if (!suc) {
                    randomSleep();
                    continue;
                }
                return result;
            }

            long newTotalAmount;
            BigDecimal newAveragePrice = null;

            result.setOpenAveragePrice(userPositionDO.getAveragePrice());
            result.setOpenPositionDirection(ContractUtils.toDirection(userPositionDO.getPositionType()));

            if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
                //成交单和持仓是同方向
                newTotalAmount = userPositionDO.getUnfilledAmount() + filledAmount;
                newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount, contractSize);
            }
            //成交单和持仓是反方向 （平仓）
            else if (filledAmount - userPositionDO.getUnfilledAmount() <= 0) {
                //不改变仓位方向
                newTotalAmount = userPositionDO.getUnfilledAmount() - filledAmount;
                newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount, contractSize);
                result.setCloseAmount(Math.min(filledAmount, userPositionDO.getUnfilledAmount()));
            } else {
                //改变仓位方向
                userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
                newTotalAmount = filledAmount - userPositionDO.getUnfilledAmount();
                newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount, contractSize);
                result.setCloseAmount(Math.min(filledAmount, userPositionDO.getUnfilledAmount()));
            }

            suc = doUpdatePosition(userPositionDO, newAveragePrice, newTotalAmount);
            if (!suc) {
                randomSleep();
                continue;
            }
            return result;
        }
        throw new RuntimeException("insert or update position failed");
    }
    private int randomRetries(){
        return random.nextInt(MAX_UPDATE_RETRIES-10) + 10;
    }
    private boolean insertPosition(UserPositionDO userPositionDO){
        try {
            int aff = userPositionMapper.insert(userPositionDO);
            return 1 == aff;
        }catch (Throwable t) {
            log.error("insert position exception", t);
            return false;
        }
    }
    private void randomSleep(){
        try {
            Thread.sleep(random.nextInt(300));
        } catch (InterruptedException e) {
            new RuntimeException(e);
        }
    }



    public ContractDealer calBalanceChange(ContractOrderDO contractOrderDO, long filledAmount, BigDecimal filledPrice,
                                           BigDecimal contractSize, UpdatePositionResult positionResult, Integer lever){
        long userId = contractOrderDO.getUserId();
        BigDecimal rate = contractOrderDO.getFee();
        //手续费
        BigDecimal actualFee = filledPrice.multiply(new BigDecimal(filledAmount)).multiply(rate).multiply(contractSize);

        com.fota.common.Result<ContractAccount> result = contractAccountService.getContractAccount(userId);
        if (!result.isSuccess() || null==result.getData()) {
            throw new RuntimeException("get contractAccount failed");
        }
        ContractAccount account = result.getData();
        if (account.getAvailableAmount().compareTo(BigDecimal.ZERO) <= 0 && !(ENFORCE.getCode() == contractOrderDO.getOrderType())) {
            throw new BizException(BALANCE_NOT_ENOUGH);
        }
        BigDecimal addAmount = actualFee.negate();
        //计算平仓盈亏
        if (positionResult.getCloseAmount() != null) {
            // (filledPrice-openAveragePrice)*closeAmount*contractSize*openPositionDirection - actualFee
            addAmount = filledPrice.subtract(positionResult.getOpenAveragePrice())
                    .multiply(new BigDecimal(positionResult.getCloseAmount()))
                    .multiply(contractSize)
                    .multiply(new BigDecimal(positionResult.getOpenPositionDirection()))
                    .subtract(actualFee);
        }
        ContractDealer dealer = new ContractDealer()
                .setUserId(userId)
                .setAddedTotalAmount(addAmount)
                .setTotalLockAmount(account.getFrozenAmount().add(account.getMarginCallRequirement()))
                ;
        dealer.setDealType((null != contractOrderDO.getOrderType() && ENFORCE.getCode() == contractOrderDO.getOrderType()) ? ContractDealer.DealType.FORCE : ContractDealer.DealType.NORMAL);
        return dealer;
    }

//    private void updateContractAccount(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO contractMatchedOrderDTO) {
//        BigDecimal filledAmount = new BigDecimal(contractMatchedOrderDTO.getFilledAmount());
//        BigDecimal filledPrice = new BigDecimal(contractMatchedOrderDTO.getFilledPrice());
//        Long userId = contractOrderDO.getUserId();
//        Long contractId = contractOrderDO.getContractId();
//        UserPositionDO userPositionDO = userPositionMapper.selectByUserIdAndId(userId, contractId);
//
//        BigDecimal contractSize = getContractSize(contractOrderDO.getContractId());
//        Integer lever = new Integer(contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(), contractOrderDO.getContractId()));
//        if (userPositionDO == null) {
//            // 建仓
//            buildPosition(contractOrderDO, contractMatchedOrderDTO, contractSize, lever);
//            return;
//        }
//        long oldPositionAmount = userPositionDO.getUnfilledAmount();
//        if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
//            //成交单和持仓是同方向
//            long newTotalAmount = userPositionDO.getUnfilledAmount() + filledAmount.longValue();
//            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
//            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
//            BigDecimal newTotalPrice = oldTotalPrice.add(addedTotalPrice);
//            BigDecimal newAvaeragePrice = newTotalPrice.divide(new BigDecimal(newTotalAmount), 8, BigDecimal.ROUND_DOWN);
//            doUpdatePosition(userPositionDO, newAvaeragePrice, newTotalAmount);
//            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO, lever);
//            return;
//        }
//
//        //成交单和持仓是反方向 （平仓）
//        if (filledAmount.longValue() - userPositionDO.getUnfilledAmount() <= 0) {
//            //不改变仓位方向
//            long newTotalAmount = userPositionDO.getUnfilledAmount() - filledAmount.longValue();
//            BigDecimal newAvaeragePrice = null;
//            if (newTotalAmount != 0){
//                newAvaeragePrice = userPositionDO.getAveragePrice().setScale(8, BigDecimal.ROUND_DOWN);
//            }
//            doUpdatePosition(userPositionDO, newAvaeragePrice, newTotalAmount);
//            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO, lever);
//        } else {
//            //改变仓位方向
//            long newTotalAmount = filledAmount.longValue() - userPositionDO.getUnfilledAmount();
//            BigDecimal newAvaeragePrice = filledPrice.setScale(8, BigDecimal.ROUND_DOWN);;
//            userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
//            doUpdatePosition(userPositionDO, newAvaeragePrice, newTotalAmount);
//            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO, lever);
//        }
//    }

//    private void buildPosition(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO matchedOrderDTO,
//                               BigDecimal contractSize, Integer lever) {
//        UserPositionDO newUserPositionDO = new UserPositionDO();
//        newUserPositionDO.setPositionType(contractOrderDO.getOrderDirection());
//        newUserPositionDO.setAveragePrice(new BigDecimal(matchedOrderDTO.getFilledPrice()));
//        newUserPositionDO.setUnfilledAmount(matchedOrderDTO.getFilledAmount());
//        newUserPositionDO.setStatus(1);
//        newUserPositionDO.setUserId(contractOrderDO.getUserId());
//        newUserPositionDO.setContractName(contractOrderDO.getContractName());
//        newUserPositionDO.setContractId(contractOrderDO.getContractId());
//        newUserPositionDO.setContractSize(contractSize);
//        newUserPositionDO.setLever(lever);
//        try {
//            int insertRet = userPositionMapper.insert(newUserPositionDO);
//            if (insertRet != 1) {
//                log.error("buildPosition failed");
//            }
//        } catch (Exception e) {
//            log.error("userPositionMapper.insert({})", newUserPositionDO, e);
//        }
//        int updateBalanceRet = updateBalance(contractOrderDO, 0L, matchedOrderDTO.getFilledAmount(), matchedOrderDTO, lever);
//        if (updateBalanceRet != 1) {
//            log.error("buildPosition updateBalance failed");
//        }
//    }

    /**
     * @param userPositionDO  旧的持仓
     * @param newAvaeragePrice 新的开仓均价
     * @param newTotalAmount  新的持仓数量
     * @return
     */
    public boolean doUpdatePosition(UserPositionDO userPositionDO, BigDecimal newAvaeragePrice, long newTotalAmount) {
        userPositionDO.setAveragePrice(newAvaeragePrice);
        userPositionDO.setUnfilledAmount(newTotalAmount);
        try{
            int aff = userPositionMapper.updateByOpLock(userPositionDO);
            return aff == 1;
        }catch (Throwable t) {
            log.error("update position exception", t);
            return false;
        }
    }


    //合约账户amoutn: + (oldPositionAmount - 当前持仓)*合约价格 - 手续费
    //合约账户冻结：解冻委托价*合约份数 + 手续费
//    private int updateBalance(ContractOrderDO contractOrderDO,
//                              long oldPositionAmount,
//                              long newPositionAmount,
//                              ContractMatchedOrderDTO matchedOrderDTO,
//                              Integer lever) {
//        long filledAmount = matchedOrderDTO.getFilledAmount();
//        BigDecimal filledPrice = new BigDecimal(matchedOrderDTO.getFilledPrice());
//        BigDecimal fee = contractOrderDO.getFee();
//        BigDecimal contractSize = getContractSize(contractOrderDO.getContractId());
//        BigDecimal actualFee = filledPrice.multiply(new BigDecimal(filledAmount)).multiply(fee).multiply(contractSize);
//        BigDecimal addedTotalAmount = new BigDecimal(oldPositionAmount - newPositionAmount)
//                .multiply(filledPrice)
//                .multiply(contractSize)
//                .divide(new BigDecimal(lever), 8, BigDecimal.ROUND_DOWN)
//                .subtract(actualFee);
//        UserContractDTO userContractDTO = new UserContractDTO();
//        try {
//            userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
//        } catch (Exception e) {
//            log.error(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
//            throw new RuntimeException(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
//        }
//        BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
//        BigDecimal totalLockAmount = null;
//        tradeLog.info("match id {}", matchedOrderDTO.getId());
//        try {
//            totalLockAmount = getTotalLockAmount(contractOrderDO.getUserId());
//        } catch (Exception e) {
//            log.error("get totalLockAmount failed", e);
//        }
//        //todo 更新余额
//        BigDecimal addedTotalLocked = totalLockAmount.subtract(lockedAmount);
//
//        ContractDealer dealer = new ContractDealer()
//                .setUserId(contractOrderDO.getUserId())
//                .setAddedTotalAmount(addedTotalAmount)
//                .setAddedLockAmount(addedTotalLocked);
//        dealer.setDealType((null != contractOrderDO.getOrderType() && ENFORCE.getCode() == contractOrderDO.getOrderType()) ? ContractDealer.DealType.FORCE : ContractDealer.DealType.NORMAL);
//
//        com.fota.common.Result result = getContractService().updateBalances(dealer);
//        if (!result.isSuccess()) {
//            log.error("update contract balance failed");
//            throw new RuntimeException("update balance failed");
//        }
//        return 1;
//    }


    public void updateContractOrder(long id, long filledAmount, BigDecimal filledPrice, Date gmtModified) {
        int aff;
        try{
            aff = contractOrderMapper.updateAmountAndStatus(id, filledAmount, filledPrice, gmtModified);
        }catch (Throwable t) {
            throw new RuntimeException(String.format("update contract order failed, orderId={}, filledAmount={}, filledPrice={}, gmtModified={}",
                    id, filledAmount, filledPrice, gmtModified),t);
        }
        if (0 == aff) {
            throw new RuntimeException(String.format("update contract order failed, orderId={}, filledAmount={}, filledPrice={}, gmtModified={}",
                    id, filledAmount, filledPrice, gmtModified));
        }
    }


}
