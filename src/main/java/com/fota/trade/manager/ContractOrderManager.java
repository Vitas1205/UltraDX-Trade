package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.match.domain.ContractMatchedOrderMarketDTO;
import com.fota.match.domain.ContractMatchedOrderTradeDTO;
import com.fota.match.service.ContractMatchedOrderService;
import com.fota.trade.common.BusinessException;
import com.fota.trade.common.Constant;
import com.fota.trade.common.Result;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.*;
import com.fota.trade.domain.dto.CompetitorsPriceDTO;
import com.fota.trade.domain.enums.*;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.util.PriceUtil;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

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

    private ContractService getContractService() {
        return contractService;
    }
    private AssetService getAssetService() {
        return assetService;
    }

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



    public ResultCode cancelAllOrder(Long userId, Map<String, String> userInfoMap) throws Exception{
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByUserId(userId);
        int i = 0;
        if (list != null){
            for(ContractOrderDO contractOrderDO : list){
                if (contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode() || contractOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode()){
                    Long orderId = contractOrderDO.getId();
                    try {
                        ResultCode resultCode2 =cancelOrder(userId, orderId, userInfoMap);
                        if (resultCode2.getCode() == 0){
                            i++;
                        }
                    }catch (Exception e){
                        log.error("cancelAllOrder has failed",contractOrderDO,e);
                    }
                }

            }
        }
        if (i == 0){
            resultCode.setCode(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getCode());
            resultCode.setMessage(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getMessage());
            return resultCode;
        }
        if (i != list.size()){
            resultCode = ResultCode.error(ResultCodeEnum.PARTLY_COMPLETED.getCode(), ResultCodeEnum.PARTLY_COMPLETED.getMessage());
            return resultCode;
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }

    public ResultCode cancelOrderByContractId(Long contractId, Map<String, String> userInfoMap) throws Exception{
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByContractId(contractId);
        int i = 0;
        if (list != null){
            for(ContractOrderDO contractOrderDO : list){
                if (contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode() || contractOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode()){
                    i++;
                    Long orderId = contractOrderDO.getId();
                    cancelOrder(contractOrderDO.getUserId(), orderId, userInfoMap);
                }
            }
        }
        if (i == 0){
            resultCode.setCode(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getCode());
            resultCode.setMessage(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getMessage());
            return resultCode;
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }

    public ResultCode cancelOrderByOrderType(long userId, int orderType, Map<String, String> userInfoMap) throws Exception{
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.listByUserIdAndOrderType(userId, orderType);
        int i = 0;
        if (list != null){
            for(ContractOrderDO contractOrderDO : list){
                if (contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode() || contractOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode()){
                    i++;
                    Long orderId = contractOrderDO.getId();
                    cancelOrder(contractOrderDO.getUserId(), orderId, userInfoMap);
                }
            }
        }
        if (i == 0){
            resultCode.setCode(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getCode());
            resultCode.setMessage(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getMessage());
            return resultCode;
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }

    @Transactional(rollbackFor = {Exception.class, RuntimeException.class, BusinessException.class})
    public com.fota.common.Result<Long> placeOrder(ContractOrderDO contractOrderDO, Map<String, String> userInfoMap) throws Exception{
        ResultCode resultCode = new ResultCode();
        com.fota.common.Result<Long> result = new com.fota.common.Result<Long>();
        Long orderId = 0L;
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
            log.error("Contract Is Null");
            throw new RuntimeException("Contract Is Null");
        }
        //todo 判单合约是否可以交易
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
        if (contractOrderDO.getOrderType() == null){
            contractOrderDO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        }
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()){
            insertOrderRecord(contractOrderDO);
        }else {
            contractOrderDO.setContractName(contractCategoryDO.getContractName());
            contractOrderDO.setCloseType(OrderCloseTypeEnum.MANUAL.getCode());
            orderId = insertOrderRecord(contractOrderDO);
            BigDecimal totalLockAmount = getTotalLockAmount(contractOrderDO.getUserId());
            //查询用户合约冻结金额
            UserContractDTO userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
            BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
            if (lockedAmount.compareTo(totalLockAmount) < 0 ){
                lockContractAccount(userContractDTO, totalLockAmount);
            }
        }
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(0L);
        contractOrderDTO.setContractId(contractOrderDO.getContractId());
        redisManager.contractOrderSave(contractOrderDTO);
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            // 强平单
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    2, contractOrderDTO.getContractName(), userInfoMap.get("username"), userInfoMap.get("ipAddress"), contractOrderDTO.getTotalAmount(),
                    new Date(), 3, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 2);
        } else {
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    2, contractOrderDTO.getContractName(), userInfoMap.get("username"), userInfoMap.get("ipAddress"), contractOrderDTO.getTotalAmount(),
                    new Date(), 1, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 1);
        }

        //推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setAmount(new BigDecimal(contractOrderDTO.getUnfilledAmount()));
        orderMessage.setPrice(contractOrderDTO.getPrice());
        orderMessage.setTransferTime((new Date()).getTime());
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId());
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder", orderMessage);
        if (!sendRet){
            log.error("Send RocketMQ Message Failed ");
        }
        result.setCode(0);
        result.setMessage("success");
        result.setData(orderId);
        return result;
    }



    public ResultCode cancelOrder(Long userId, Long orderId, Map<String, String> userInfoMap) throws Exception{
        ResultCode resultCode = new ResultCode();
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByIdAndUserId(orderId, userId);
        resultCode = cancelOrderImpl(contractOrderDO, userInfoMap);
        return resultCode;
    }

    @Transactional(rollbackFor = {Exception.class, RuntimeException.class, BusinessException.class})
    public ResultCode cancelOrderImpl(ContractOrderDO contractOrderDO, Map<String, String> userInfoMap) throws Exception{
        ResultCode resultCode = new ResultCode();
        Integer status = contractOrderDO.getStatus();
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
            log.error("Contract Is Null");
            throw new RuntimeException("Contract Is Null");
        }
        if (contractCategoryDO.getStatus() != ContractStatusEnum.PROCESSING.getCode()){
            log.error("contract status illegal,can not cancel{}", contractCategoryDO);
            throw new RuntimeException("contractCategoryDO");
        }
        /*boolean judegRet = getJudegRet(contractOrderDO.getId(),contractOrderDO.getOrderDirection(),new BigDecimal(contractOrderDO.getUnfilledAmount()));
        if (!judegRet){
            resultCode.setCode(ResultCodeEnum.ORDER_CAN_NOT_CANCLE.getCode());
            resultCode.setMessage(ResultCodeEnum.ORDER_CAN_NOT_CANCLE.getMessage());
            return resultCode;
        }*/
        if (status == OrderStatusEnum.COMMIT.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.CANCEL.getCode());
        }else if (status == OrderStatusEnum.PART_MATCH.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.PART_CANCEL.getCode());
        }else if (status == OrderStatusEnum.MATCH.getCode() || status == OrderStatusEnum.PART_CANCEL.getCode()  || status == OrderStatusEnum.CANCEL.getCode() ){
            log.error("order has completed{}", contractOrderDO);
            throw new RuntimeException("order has completed");
        }else {
            log.error("order status illegal{}", contractOrderDO);
            throw new RuntimeException("order status illegal");
        }
        int ret = contractOrderMapper.updateByOpLock(contractOrderDO);
        if (ret > 0){
            UserContractDTO userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
            BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
            BigDecimal totalLockAmount = getTotalLockAmount(contractOrderDO.getUserId());
            if (lockedAmount.compareTo(totalLockAmount) != 0 ){
                lockContractAccount(userContractDTO, totalLockAmount);
            }
        }else {
            log.error("update contractOrder Failed{}", contractOrderDO);
            throw new RuntimeException("update contractOrder Failed");
        }
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(contractOrderDTO.getTotalAmount()-contractOrderDTO.getUnfilledAmount());
        contractOrderDTO.setContractId(contractOrderDO.getContractId());
        redisManager.contractOrderSave(contractOrderDTO);
        tradeLog.info("tradeLog@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, contractOrderDTO.getContractName(), userInfoMap.get("username"), userInfoMap.get("ipAddress"), contractOrderDTO.getUnfilledAmount(),
                new Date(), 2, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 1);
        //todo 推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setAmount(new BigDecimal(contractOrderDTO.getUnfilledAmount()));
        orderMessage.setPrice(contractOrderDTO.getPrice());
        orderMessage.setTransferTime(System.currentTimeMillis());
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId());
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder", orderMessage);
        if (!sendRet){
            log.error("Send RocketMQ Message Failed ");
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }

    public boolean getJudegRet(Long orderId, Integer orderDeriction, BigDecimal unfilledAmount){
        return contractMatchedOrderService.cancelOrderContract(orderId, orderDeriction, unfilledAmount);
    }


    //获取追加冻结金额
    public BigDecimal getTotalLockAmount(long userId) throws Exception{
        Object competiorsPriceObj = redisManager.get(Constant.CONTRACT_COMPETITOR_PRICE_KEY);
        List<CompetitorsPriceDTO> competitorsPriceList = JSON.parseArray(competiorsPriceObj.toString(),CompetitorsPriceDTO.class);
        //获取所有合约类型列表
        BigDecimal totalLockedAmount = BigDecimal.ZERO;
        List<ContractCategoryDO> queryList = contractCategoryMapper.getAllContractCategory();
        List<UserPositionDO> positionlist = userPositionMapper.selectByUserId(userId);
        List<ContractOrderDO> contractOrderlist = contractOrderMapper.selectUnfinishedOrderByUserId(userId);
        //log.error("selectUnfinishedOrderByUserId {} contractOrderlist size {}, contractOrderlist {}", userId, contractOrderlist.size(), String.valueOf(contractOrderlist.get(0)));

        if (queryList != null && queryList.size() != 0 && contractOrderlist != null && contractOrderlist.size() != 0){
            log.error("selectUnfinishedOrderByUserId {} contractOrderlist size {}, contractOrderlist {}", userId, contractOrderlist.size(), contractOrderlist.get(0).toString());

            for (ContractCategoryDO contractCategoryDO : queryList){
                BigDecimal entrustLockAmount = BigDecimal.ZERO;
                long contractId = contractCategoryDO.getId();
                List<ContractOrderDO> orderList = contractOrderlist.stream().filter(contractOrder -> contractOrder.getContractId().equals(contractCategoryDO.getId()))
                        .collect(Collectors.toList());
                if (orderList != null && orderList.size() != 0){
                    List<ContractOrderDO> bidList = orderList.stream().filter(order-> order.getOrderDirection() == OrderDirectionEnum.BID.getCode()).collect(Collectors.toList());
                    List<ContractOrderDO> askList = orderList.stream().filter(order-> order.getOrderDirection() == OrderDirectionEnum.ASK.getCode()).collect(Collectors.toList());
                    List<UserPositionDO> userPositionDOlist = new ArrayList<>();
                    if (positionlist != null && positionlist.size() != 0){
                        log.info("positionlist.size()++++++++++++++++++"+positionlist.size());
                        log.info("contractOrderlist.size()++++++++++++++++++"+contractOrderlist.size());
                        userPositionDOlist = positionlist.stream().filter(userPosition-> userPosition.getContractId().equals(contractCategoryDO.getId()))
                                .limit(1).collect(Collectors.toList());
                        if (userPositionDOlist != null && userPositionDOlist.size() != 0){
                            UserPositionDO userPositionDO = userPositionDOlist.get(0);
                            BigDecimal totalAskExtraEntrustAmount = BigDecimal.ZERO;
                            BigDecimal totalBidExtraEntrustAmount = BigDecimal.ZERO;
                            //todo 获取买一卖一价
                            List<CompetitorsPriceDTO> askCurrentPriceList = competitorsPriceList.stream().filter(competitorsPrice-> competitorsPrice.getOrderDirection() == OrderDirectionEnum.ASK.getCode() &&
                                    competitorsPrice.getId() == contractId).limit(1).collect(Collectors.toList());
                            List<CompetitorsPriceDTO> bidCurrentPriceList = competitorsPriceList.stream().filter(competitorsPrice-> competitorsPrice.getOrderDirection() == OrderDirectionEnum.BID.getCode() &&
                                    competitorsPrice.getId() == contractId).limit(1).collect(Collectors.toList());
                            BigDecimal askCurrentPrice = BigDecimal.ZERO;
                            BigDecimal bidCurrentPrice = BigDecimal.ZERO;
                            if (askCurrentPriceList != null && askCurrentPriceList.size() != 0){
                                askCurrentPrice = askCurrentPriceList.get(0).getPrice();
                            }
                            if (bidCurrentPriceList != null && bidCurrentPriceList.size() != 0){
                                bidCurrentPrice = bidCurrentPriceList.get(0).getPrice();
                            }
                            BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId,contractId));
                            Integer positionType = userPositionDO.getPositionType();
                            BigDecimal positionUnfilledAmount = new BigDecimal(userPositionDO.getUnfilledAmount());
                            BigDecimal contractSize = contractCategoryDO.getContractSize();
                            if (positionType == PositionTypeEnum.OVER.getCode()){
                                BigDecimal bidPositionEntrustAmount = positionUnfilledAmount.multiply(contractSize).multiply(bidCurrentPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
                                totalAskExtraEntrustAmount = totalAskExtraEntrustAmount.add(getExtraEntrustAmount(bidList,askList,positionType,positionUnfilledAmount,bidPositionEntrustAmount,lever,contractSize));
                            }else if (positionType == PositionTypeEnum.EMPTY.getCode()){
                                BigDecimal askPositionEntrustAmount = positionUnfilledAmount.multiply(contractSize).multiply(askCurrentPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
                                totalBidExtraEntrustAmount = totalBidExtraEntrustAmount.add(getExtraEntrustAmount(bidList,askList,positionType,positionUnfilledAmount,askPositionEntrustAmount,lever,contractSize));
                            }
                            entrustLockAmount = entrustLockAmount.add(totalBidExtraEntrustAmount.add(totalAskExtraEntrustAmount));
                        }
                    }
                    if(positionlist == null || positionlist.size() == 0 || userPositionDOlist == null || userPositionDOlist.size() == 0){
                        BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId,contractId));
                        BigDecimal orderValue;
                        BigDecimal orderFee;
                        BigDecimal toltalBidEntrustAmount = BigDecimal.ZERO;
                        BigDecimal toltalAskEntrustAmount = BigDecimal.ZERO;
                        if (bidList != null && bidList.size() != 0){
                            for(ContractOrderDO bidOrder:bidList){
                                orderValue = bidOrder.getPrice().multiply(contractCategoryDO.getContractSize()).multiply(new BigDecimal(bidOrder.getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
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
                        if (toltalBidEntrustAmount.compareTo(toltalAskEntrustAmount) > 0){
                            entrustLockAmount = toltalBidEntrustAmount;
                        }else {
                            entrustLockAmount = toltalAskEntrustAmount;
                        }
                    }
                }
                totalLockedAmount = totalLockedAmount.add(entrustLockAmount);
            }
        }else {
            return BigDecimal.ZERO;
        }
        return totalLockedAmount;
    }

    //获取多空仓额外保证金
    public BigDecimal getExtraEntrustAmount(List<ContractOrderDO> bidList, List<ContractOrderDO> askList, Integer positionType,
                                               BigDecimal positionUnfilledAmount, BigDecimal positionEntrustAmount, BigDecimal lever, BigDecimal contractSize){
        BigDecimal max1 = BigDecimal.ZERO;
        BigDecimal max2 = BigDecimal.ZERO;
        BigDecimal totalAskEntrustAmount = BigDecimal.ZERO;
        BigDecimal totalBidEntrustAmount = BigDecimal.ZERO;
        BigDecimal askEntrustAmount = BigDecimal.ZERO;
        BigDecimal bidEntrustAmount = BigDecimal.ZERO;
        if (positionType == PositionTypeEnum.OVER.getCode()){
            if (askList != null && askList.size() != 0){
                List<ContractOrderDO> sortedAskList = sortListEsc(askList);
                for (int i = 0;i < sortedAskList.size();i++){
                    positionUnfilledAmount = positionUnfilledAmount.subtract(new BigDecimal(sortedAskList.get(i).getUnfilledAmount()));
                    if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0){
                        BigDecimal restAmount = positionUnfilledAmount.negate().multiply(contractSize).multiply(sortedAskList.get(i).getPrice()).divide(lever, 8,BigDecimal.ROUND_DOWN);
                        BigDecimal restFee = restAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        BigDecimal totalRest = restAmount.add(restFee);
                        for (int j = i + 1;j < sortedAskList.size();j++){
                            BigDecimal orderAmount = sortedAskList.get(j).getPrice().multiply(contractSize).multiply(new BigDecimal(sortedAskList.get(j).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                            BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                            askEntrustAmount = askEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalAskEntrustAmount = totalRest.add(askEntrustAmount);
                        break;
                    }
                }
                if (totalAskEntrustAmount.compareTo(positionEntrustAmount) > 0){
                    max1 = totalAskEntrustAmount.subtract(positionEntrustAmount);
                }
            }
            if (bidList != null && bidList.size() != 0){
                for (int i = 0;i < bidList.size();i++){
                    BigDecimal orderAmount = bidList.get(i).getPrice().multiply(contractSize).multiply(new BigDecimal(bidList.get(i).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                    totalBidEntrustAmount = totalBidEntrustAmount.add(orderAmount.add(orderFee));
                }
                if (totalBidEntrustAmount.compareTo(max1) > 0){
                    max2 = totalBidEntrustAmount;
                    return max2;
                }
            }
            return max1;
        }else if (positionType == PositionTypeEnum.EMPTY.getCode()){
            if (bidList != null && bidList.size() != 0){
                List<ContractOrderDO> sortedBidList = sortListDesc(bidList);
                for (int i = 0;i < sortedBidList.size();i++){
                    positionUnfilledAmount = positionUnfilledAmount.subtract(new BigDecimal(sortedBidList.get(i).getUnfilledAmount()));
                    if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0){
                        BigDecimal restAmount = positionUnfilledAmount.negate().multiply(contractSize).multiply(sortedBidList.get(i).getPrice()).divide(lever, 8,BigDecimal.ROUND_DOWN);
                        BigDecimal restFee = restAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        BigDecimal totalRest = restAmount.add(restFee);
                        for (int j = i + 1;j < sortedBidList.size();j++){
                            BigDecimal orderAmount = sortedBidList.get(j).getPrice().multiply(contractSize).multiply(new BigDecimal(sortedBidList.get(j).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                            BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                            bidEntrustAmount = bidEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalBidEntrustAmount = totalRest.add(bidEntrustAmount);
                        break;
                    }
                }
                if (totalBidEntrustAmount.compareTo(positionEntrustAmount) > 0){
                    max1 = totalBidEntrustAmount.subtract(positionEntrustAmount);
                }
            }
            if (askList != null && askList.size() != 0){
                for (int i = 0;i < askList.size();i++){
                    BigDecimal orderAmount = askList.get(i).getPrice().multiply(contractSize).multiply(new BigDecimal(askList.get(i).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                    totalAskEntrustAmount = totalAskEntrustAmount.add(orderAmount.add(orderFee));
                }
                if (totalAskEntrustAmount.compareTo(max1) > 0){
                    max2 = totalAskEntrustAmount;
                    return max2;
                }
            }
            return max1;
        }else {
            throw new RuntimeException("positionType illegal");
        }
    }

    public Long insertOrderRecord(ContractOrderDO contractOrderDO){
        contractOrderDO.setStatus(8);
        contractOrderDO.setFee(Constant.FEE_RATE);
        contractOrderDO.setUnfilledAmount(contractOrderDO.getTotalAmount());
        int insertContractOrderRet = contractOrderMapper.insertSelective(contractOrderDO);
        if (insertContractOrderRet <= 0){
            log.error("insert contractOrder failed");
            throw new RuntimeException("insert contractOrder failed");
        }
        return contractOrderDO.getId();
    }

    //冻结合约账户金额
    public void lockContractAccount(UserContractDTO userContractDTO, BigDecimal totalLockAmount) throws Exception{
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        if (amount.compareTo(totalLockAmount) < 0){
            log.error("ContractAccount USDK Not Enough");
            throw new BusinessException(ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(),ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
        }
        //调用RPC接口冻结合约账户（加锁）
        BigDecimal addLockAmount = totalLockAmount.subtract(new BigDecimal(userContractDTO.getLockedAmount()));
        long gmtModified =  userContractDTO.getGmtModified().getTime();
        Boolean lockContractAmountRet = getContractService().lockContractAmount(userContractDTO.getUserId(), addLockAmount.toString(), gmtModified);
        if (!lockContractAmountRet){
            log.error("update contract account lockedAmount failed");
            throw new RuntimeException("update contract account lockedAmount failed");
        }
    }

    //升序排列
    public List<ContractOrderDO> sortListEsc(List<ContractOrderDO> list){
        List<ContractOrderDO> sortedList = list.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice))
                .collect(Collectors.toList());
        return sortedList;
    }
    //降序排列
    public List<ContractOrderDO> sortListDesc(List<ContractOrderDO> list){
        List<ContractOrderDO> sortedList = list.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice).reversed())
                .collect(Collectors.toList());
        return sortedList;
    }

    public BigDecimal getContractSize(long contractId){
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.getContractCategoryById(contractId);
        BigDecimal contractSize = contractCategoryDO.getContractSize();
        return contractSize;
    }

    public BigDecimal getTotalLockAmount(ContractOrderDO contractOrderDO){
        Integer lever = contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(),contractOrderDO.getContractId());
        BigDecimal totalValue = contractOrderDO.getPrice().multiply(new BigDecimal(contractOrderDO.getTotalAmount()))
                .multiply(new BigDecimal(0.01)).divide(new BigDecimal(lever), 8,BigDecimal.ROUND_DOWN);
        BigDecimal fee = totalValue.multiply(Constant.FEE_RATE).multiply(new BigDecimal(lever));
        return totalValue.add(fee);
    }


    //成交
    @Transactional(rollbackFor = {Exception.class, RuntimeException.class, TException.class})
    public ResultCode updateOrderByMatch(ContractMatchedOrderDTO contractMatchedOrderDTO) {
        ResultCode resultCode = new ResultCode();
        if (contractMatchedOrderDTO == null) {
            log.error(ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            throw new RuntimeException(ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        ContractOrderDO askContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getAskOrderId());
        ContractOrderDO bidContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getBidOrderId());
        log.info("---------------{}", contractMatchedOrderDTO);
        log.info("---------------{}", askContractOrder);
        log.info("---------------{}", bidContractOrder);
        if (askContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0
                || bidContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0){
            log.error("unfilledAmount not enough");
            throw new RuntimeException("unfilledAmount not enough");
        }
        if (askContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error("ask order status illegal{}", askContractOrder);
            throw new RuntimeException("ask order status illegal");
        }
        if (bidContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error("bid order status illegal{}", bidContractOrder);
            throw new RuntimeException("bid order status illegal");
        }
        askContractOrder.setStatus(contractMatchedOrderDTO.getAskOrderStatus());
        bidContractOrder.setStatus(contractMatchedOrderDTO.getBidOrderStatus());
        updateContractAccount(askContractOrder, contractMatchedOrderDTO);
        updateContractAccount(bidContractOrder, contractMatchedOrderDTO);

        ContractMatchedOrderDO contractMatchedOrderDO = com.fota.trade.common.BeanUtils.copy(contractMatchedOrderDTO);
        BigDecimal fee = contractMatchedOrderDO.getFilledAmount().multiply(contractMatchedOrderDO.getFilledPrice()).multiply(Constant.FEE_RATE).setScale(2, BigDecimal.ROUND_UP);
        contractMatchedOrderDO.setFee(fee);
        contractMatchedOrderDO.setAskUserId(askContractOrder.getUserId());
        contractMatchedOrderDO.setBidUserId(bidContractOrder.getUserId());
        contractMatchedOrderDO.setAskCloseType(askContractOrder.getCloseType().byteValue());
        contractMatchedOrderDO.setBidCloseType(bidContractOrder.getCloseType().byteValue());
        try {
            int ret = contractMatchedOrderMapper.insert(contractMatchedOrderDO);
            if (ret < 1){
                log.error("保存Contract订单数据到数据库失败({})", contractMatchedOrderDO);
                throw new RuntimeException("contractMatchedOrderMapper.insert failed{}");
            }
        } catch (Exception e) {
            log.error("保存Contract订单数据到数据库失败({})", contractMatchedOrderDO, e);
            throw new RuntimeException("contractMatchedOrderMapper.insert exception{}", e);
        }

        //存入Redis缓存 有相关撮合
        ContractOrderDTO bidContractOrderDTO = new ContractOrderDTO();
        ContractOrderDTO askContractOrderDTO = new ContractOrderDTO();
        org.springframework.beans.BeanUtils.copyProperties(askContractOrder, askContractOrderDTO);
        askContractOrderDTO.setContractId(askContractOrder.getContractId());
        org.springframework.beans.BeanUtils.copyProperties(bidContractOrder, bidContractOrderDTO);
        bidContractOrderDTO.setContractId(bidContractOrder.getContractId());
        askContractOrderDTO.setCompleteAmount(contractMatchedOrderDTO.getFilledAmount());
        bidContractOrderDTO.setCompleteAmount(contractMatchedOrderDTO.getFilledAmount());
        bidContractOrderDTO.setStatus(contractMatchedOrderDTO.getBidOrderStatus());
        askContractOrderDTO.setStatus(contractMatchedOrderDTO.getAskOrderStatus());
        redisManager.contractOrderSave(askContractOrderDTO);
        // TODO 需要拿到matchID insert后返回
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, askContractOrderDTO.getContractName(), "username", askContractOrderDTO.getMatchAmount(), new Date(), 4, askContractOrderDTO.getOrderDirection(), askContractOrderDTO.getUserId(), 1);
        redisManager.contractOrderSave(bidContractOrderDTO);
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, askContractOrderDTO.getContractName(), "username", askContractOrderDTO.getMatchAmount(), new Date(), 4, askContractOrderDTO.getOrderDirection(), askContractOrderDTO.getUserId(), 1);
        // 向MQ推送消息
        // 通过contractId去trade_contract_category表里面获取asset_name和contract_type
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.getContractCategoryById(askContractOrder.getContractId());
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setSubjectId(contractMatchedOrderDTO.getContractId());
        orderMessage.setSubjectName(contractMatchedOrderDTO.getContractName());
        orderMessage.setTransferTime(contractMatchedOrderDO.getGmtCreate().getTime());
        orderMessage.setPrice(new BigDecimal(contractMatchedOrderDTO.getFilledPrice()));
        orderMessage.setAmount(new BigDecimal(contractMatchedOrderDTO.getFilledAmount()));
        orderMessage.setEvent(OrderOperateTypeEnum.DEAL_ORDER.getCode());
        orderMessage.setAskOrderId(contractMatchedOrderDTO.getAskOrderId());
        orderMessage.setBidOrderId(contractMatchedOrderDTO.getBidOrderId());
        orderMessage.setAskUserId(askContractOrder.getUserId());
        orderMessage.setBidUserId(bidContractOrder.getUserId());
        orderMessage.setFee(fee);
        orderMessage.setMatchOrderId(contractMatchedOrderDO.getId());
        orderMessage.setContractMatchAssetName(contractCategoryDO.getAssetName());
        orderMessage.setContractType(contractCategoryDO.getContractType());
        Boolean sendRet = rocketMqManager.sendMessage("match", "contract", orderMessage);
        if (!sendRet){
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
        contractMatchedOrderTradeDTO.setMatchType((int)contractMatchedOrderDO.getMatchType());
        contractMatchedOrderTradeDTO.setContractId(askContractOrder.getContractId());
        contractMatchedOrderTradeDTO.setContractName(contractMatchedOrderDO.getContractName());
        try {
            String key = Constant.CACHE_KEY_MATCH_CONTRACT + contractMatchedOrderDO.getId();
            Object value = JSONObject.toJSONString(contractMatchedOrderTradeDTO);
            log.info("向Redis存储消息,key:{},value:{}", key, value);
            boolean re = redisManager.set(key,value);
            if (!re) {
                log.error("向Redis存储消息失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("向Redis存储USDK撮合订单信息失败，订单id为 {}", contractMatchedOrderDO.getId());
        }
        log.info("========完成撮合({})======= {}", System.currentTimeMillis(), contractMatchedOrderDO.getId());

        resultCode.setCode(ResultCodeEnum.SUCCESS.getCode());
        return resultCode;
    }

    private void updateContractAccount(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO contractMatchedOrderDTO) {
        BigDecimal filledAmount = new BigDecimal(contractMatchedOrderDTO.getFilledAmount());
        BigDecimal filledPrice = new BigDecimal(contractMatchedOrderDTO.getFilledPrice());
        Long userId = contractOrderDO.getUserId();
        Long contractId = contractOrderDO.getContractId();
        UserPositionDO userPositionDO = null;

        try {
            userPositionDO = userPositionMapper.selectByUserIdAndId(userId, contractId.intValue());
        } catch (Exception e) {
            log.error("userPositionMapper.selectByUserIdAndId({}, {})", userId, contractId, e);
            return;
        }
        BigDecimal contractSize = getContractSize(contractOrderDO.getContractId());
        Integer lever = new Integer(contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(),contractOrderDO.getContractId()));
        if (userPositionDO == null) {
            // 建仓
            buildPosition(contractOrderDO, contractMatchedOrderDTO, contractSize, lever);
            return;
        }
        long oldPositionAmount = userPositionDO.getUnfilledAmount();
        if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
            //todo 成交单和持仓是同方向
            long newTotalAmount = userPositionDO.getUnfilledAmount() + filledAmount.longValue();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
            updateUserPosition(userPositionDO, oldTotalPrice, addedTotalPrice, newTotalAmount);
            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO, lever);
            return;
        }

        //成交单和持仓是反方向 （平仓）
        if (filledAmount.longValue() - userPositionDO.getUnfilledAmount() <= 0) {
            //todo 不改变仓位方向
            long newTotalAmount = userPositionDO.getUnfilledAmount() - filledAmount.longValue();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount).negate();
            updateUserPosition(userPositionDO, oldTotalPrice, addedTotalPrice, newTotalAmount);
            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO, lever);
        } else {
            //todo 改变仓位方向
            long newTotalAmount = filledAmount.longValue() - userPositionDO.getUnfilledAmount();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount())).negate();
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
            userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
            updateUserPosition(userPositionDO, oldTotalPrice, addedTotalPrice, newTotalAmount);
            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO, lever);
        }
    }

    private void buildPosition(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO matchedOrderDTO,
                               BigDecimal contractSize, Integer lever) {
        UserPositionDO newUserPositionDO = new UserPositionDO();
        newUserPositionDO.setPositionType(contractOrderDO.getOrderDirection());
        newUserPositionDO.setAveragePrice(new BigDecimal(matchedOrderDTO.getFilledPrice()));
        newUserPositionDO.setUnfilledAmount(matchedOrderDTO.getFilledAmount());
        newUserPositionDO.setStatus(1);
        newUserPositionDO.setUserId(contractOrderDO.getUserId());
        newUserPositionDO.setContractName(contractOrderDO.getContractName());
        newUserPositionDO.setContractId(contractOrderDO.getContractId());
        newUserPositionDO.setContractSize(contractSize);
        newUserPositionDO.setLever(lever);
        try {
            int insertRet = userPositionMapper.insert(newUserPositionDO);
            if (insertRet != 1){
                log.error("buildPosition failed");
            }
        } catch (Exception e) {
            log.error("userPositionMapper.insert({})", newUserPositionDO, e);
        }
        int updateBalanceRet =  updateBalance(contractOrderDO, 0L, matchedOrderDTO.getFilledAmount(), matchedOrderDTO, lever);
        if (updateBalanceRet != 1){
            log.error("buildPosition updateBalance failed");
        }
    }

    private int updateUserPosition(UserPositionDO userPositionDO, BigDecimal oldTotalPrice, BigDecimal addedTotalPrice, long newTotalAmount) {
        BigDecimal newTotalPrice = oldTotalPrice.add(addedTotalPrice);
        if (newTotalAmount != 0){
            BigDecimal newAvaeragePrice = newTotalPrice.divide(new BigDecimal(newTotalAmount), 8,BigDecimal.ROUND_DOWN);
            userPositionDO.setAveragePrice(newAvaeragePrice);
        }
        userPositionDO.setUnfilledAmount(newTotalAmount);

        int updateRet = 0;
        try {
            updateRet = userPositionMapper.updateByPrimaryKey(userPositionDO);
            if (updateRet != 1){
                log.error("updateUserPosition failed");
            }
        } catch (Exception e) {
            log.error("userPositionMapper.update({})", userPositionDO, e);
        }
        return updateRet;
    }


    //todo 合约账户amoutn: + (oldPositionAmount - 当前持仓)*合约价格 - 手续费
    //todo 合约账户冻结：解冻委托价*合约份数 + 手续费
    private int updateBalance(ContractOrderDO contractOrderDO,
                              long oldPositionAmount,
                              long newPositionAmount,
                              ContractMatchedOrderDTO matchedOrderDTO,
                              Integer lever){
        long filledAmount = matchedOrderDTO.getFilledAmount();
        BigDecimal filledPrice = new BigDecimal(matchedOrderDTO.getFilledPrice());
        BigDecimal fee = contractOrderDO.getFee();
        BigDecimal contractSize = getContractSize(contractOrderDO.getContractId());
        BigDecimal actualFee = filledPrice.multiply(new BigDecimal(filledAmount)).multiply(fee).multiply(contractSize);
        BigDecimal addedTotalAmount = new BigDecimal(oldPositionAmount - newPositionAmount)
                .multiply(filledPrice)
                .multiply(contractSize)
                .divide(new BigDecimal(lever), 8, BigDecimal.ROUND_DOWN)
                .subtract(actualFee);
        UserContractDTO userContractDTO = new UserContractDTO();
        try {
            userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
        }catch (Exception e){
            log.error(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
            throw new RuntimeException(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
        }
        BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
        BigDecimal totalLockAmount = null;
        tradeLog.info("match id {}", matchedOrderDTO.getId());
        int updateRet = updateSingleOrderByFilledAmount(contractOrderDO, matchedOrderDTO.getFilledAmount(), matchedOrderDTO.getFilledPrice());
        if (updateRet != 1){
            log.error("updateSingleOrderByFilledAmount failed");
            throw new RuntimeException("updateSingleOrderByFilledAmount failed");
        }
        try {
            totalLockAmount = getTotalLockAmount(contractOrderDO.getUserId());
        } catch (Exception e) {
            log.error("get totalLockAmount failed",e);
        }
        //todo 更新余额
        try {
            BigDecimal addedTotalLocked = totalLockAmount.subtract(lockedAmount);

            boolean updateContractRet = getContractService().updateContractBalance(contractOrderDO.getUserId(),
                    addedTotalAmount.toString(),
                    addedTotalLocked.toString());
            if (!updateContractRet){
                log.error("update contract balance failed");
            }
        } catch (Exception e) {
            log.error("update contract balance failed",e);
        }
        return 1;
    }

    /**
     * 如果撮合的量等于unfilled的量，则更新状态为已成
     * 如果撮合的量小于unfilled的量并且状态为已报，增更新状态为部成，
     * 更新unfilledAmount为减去成交量后的值
     * @param contractOrderDO
     * @param filledAmount
     * @return
     */
    private int updateSingleOrderByFilledAmount(ContractOrderDO contractOrderDO, long filledAmount, String filledPrice) {
        int ret = -1;
        try {
            log.info("打印的内容----------------------"+contractOrderDO);
            tradeLog.info("update {}, fillAmount {}", contractOrderDO, filledAmount);
            BigDecimal averagePrice = PriceUtil.getAveragePrice(contractOrderDO.getAveragePrice(),
                    new BigDecimal(contractOrderDO.getTotalAmount()),
                    new BigDecimal(filledAmount),
                    new BigDecimal(filledPrice));
            ret = contractOrderMapper.updateByFilledAmount(contractOrderDO.getId(), contractOrderDO.getStatus(), filledAmount, averagePrice);
            if (ret >0){
                ContractOrderDO contractOrderDO2 = contractOrderMapper.selectByPrimaryKey(contractOrderDO.getId());
                if (contractOrderDO2.getUnfilledAmount().equals(0L) && contractOrderDO2.getStatus() != OrderStatusEnum.MATCH.getCode()){
                    contractOrderDO2.setStatus(OrderStatusEnum.MATCH.getCode());
                    contractOrderMapper.updateStatus(contractOrderDO2);
                }else if (!contractOrderDO2.getUnfilledAmount().equals(0L) && contractOrderDO2.getStatus() == OrderStatusEnum.MATCH.getCode()){
                    contractOrderDO2.setStatus(OrderStatusEnum.PART_MATCH.getCode());
                    contractOrderMapper.updateStatus(contractOrderDO2);
                }

            }
        }catch (Exception e){
            log.error(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
            throw new RuntimeException(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
        }
        return ret;
    }




}
