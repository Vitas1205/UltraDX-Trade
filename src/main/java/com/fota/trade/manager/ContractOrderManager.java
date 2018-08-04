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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Component
public class ContractOrderManager {
    private static final Logger log = LoggerFactory.getLogger(ContractOrderManager.class);


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



    /*@Transactional(rollbackFor = {Exception.class, RuntimeException.class, BusinessException.class})
    public ResultCode placeOrder(ContractOrderDO contractOrderDO) throws Exception{
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
            log.error("Contract Is Null");
            throw new BusinessException(ResultCodeEnum.CONTRANCT_IS_NULL.getCode(),ResultCodeEnum.CONTRANCT_IS_NULL.getMessage());
        }
        contractOrderDO.setContractName(contractCategoryDO.getContractName());
        ResultCode resultCode = new ResultCode();
        Long userId = contractOrderDO.getUserId();
        BigDecimal toatlLockAmount = getTotalLockAmount(contractOrderDO);
        //插入合约订单
        contractOrderDO.setStatus(8);
        contractOrderDO.setFee(Constant.FEE_RATE);
        contractOrderDO.setUnfilledAmount(contractOrderDO.getTotalAmount());
        contractOrderDO.setCloseType(OrderCloseTypeEnum.MANUAL.getCode());
        int insertContractOrderRet = contractOrderMapper.insertSelective(contractOrderDO);
        if (insertContractOrderRet <= 0){
            log.error("insert contractOrder failed");
            throw new BusinessException(ResultCodeEnum.INSERT_CONTRACT_ORDER_FAILED.getCode(),ResultCodeEnum.INSERT_CONTRACT_ORDER_FAILED.getMessage());
        }
        //查询合约账户
        UserContractDTO userContractDTO = getAssetService().getContractAccount(userId);
        BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        BigDecimal availableAmount = amount.subtract(lockedAmount);
        if (availableAmount.compareTo(toatlLockAmount) < 0){
            log.error("ContractAccount USDK Not Enough");
            throw new BusinessException(ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(),ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
        }
        //todo 调用RPC接口冻结合约账户（加锁）
        Date gmtModified =  userContractDTO.getGmtModified();
        Boolean lockContractAmountRet = getContractService().lockContractAmount(userId,
                                                toatlLockAmount.toString(),gmtModified.getTime());
        if (!lockContractAmountRet){
            log.error("update contract account lockedAmount failed");
            throw new BusinessException(ResultCodeEnum.UPDATE_CONTRACT_ACCOUNT_LOCKEDAMOUNT_FAILED.getCode(),ResultCodeEnum.UPDATE_CONTRACT_ACCOUNT_LOCKEDAMOUNT_FAILED.getMessage());
        }

        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(BigDecimal.ZERO);
        contractOrderDTO.setContractId(contractOrderDO.getContractId().intValue());
        redisManager.contractOrderSave(contractOrderDTO);
        //todo 推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId().intValue());
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder", orderMessage);
        if (!sendRet){
            log.error("Send RocketMQ Message Failed ");
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }*/

    /*@Transactional(rollbackFor = {Exception.class, RuntimeException.class, BusinessException.class})
    public ResultCode cancelOrder(Long userId, Long orderId) throws Exception{
        ResultCode resultCode = new ResultCode();
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByIdAndUserId(orderId, userId);
        Integer status = contractOrderDO.getStatus();
        if (status == OrderStatusEnum.COMMIT.getCode() || status == OrderStatusEnum.CANCEL.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.CANCEL.getCode());
        }else if (status == OrderStatusEnum.PART_MATCH.getCode() || status == OrderStatusEnum.PART_CANCEL.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.PART_CANCEL.getCode());
        }else if (status == OrderStatusEnum.MATCH.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
            resultCode.setCode(ResultCodeEnum.ORDER_IS_CANCLED.getCode());
            resultCode.setMessage(ResultCodeEnum.ORDER_IS_CANCLED.getMessage());
            return resultCode;
        }else {
            resultCode.setCode(ResultCodeEnum.ORDER_STATUS_ILLEGAL.getCode());
            resultCode.setMessage(ResultCodeEnum.ORDER_STATUS_ILLEGAL.getMessage());
            return resultCode;
        }
        int ret = contractOrderMapper.updateByOpLock(contractOrderDO);
        if (ret > 0){
            Long unfilledAmount = contractOrderDO.getUnfilledAmount();
            BigDecimal price = contractOrderDO.getPrice();
            BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(),contractOrderDO.getContractId()));
            BigDecimal unlockPrice = new BigDecimal(unfilledAmount).multiply(price).multiply(new BigDecimal(0.01)).divide(lever, 8,BigDecimal.ROUND_DOWN);
            BigDecimal unlockFee = unlockPrice.multiply(Constant.FEE_RATE).multiply(lever);
            BigDecimal totalUnlockPrice = unlockPrice.add(unlockFee);
            Boolean lockContractAmountRet =  getContractService().lockContractAmount(userId,totalUnlockPrice.negate().toString(),0L);
            if (!lockContractAmountRet){
                log.error("update contract account lockedAmount failed");
                throw new BusinessException(ResultCodeEnum.UPDATE_CONTRACT_ACCOUNT_LOCKEDAMOUNT_FAILED.getCode(),ResultCodeEnum.UPDATE_CONTRACT_ACCOUNT_LOCKEDAMOUNT_FAILED.getMessage());
            }
        }else {
            log.error("update contractOrder Failed");
            throw new BusinessException(ResultCodeEnum.UPDATE_CONTRACT_ORDER_FAILED.getCode(),ResultCodeEnum.UPDATE_CONTRACT_ORDER_FAILED.getMessage());
        }
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(new BigDecimal(contractOrderDTO.getTotalAmount()-contractOrderDTO.getUnfilledAmount()));
        contractOrderDTO.setContractId(contractOrderDO.getContractId().intValue());
        redisManager.contractOrderSave(contractOrderDTO);
        //todo 推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId().intValue());
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder", orderMessage);
        if (!sendRet){
            log.info("Send RocketMQ Message Failed ");
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }*/


    public ResultCode cancelAllOrder(Long userId) throws Exception{
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByUserId(userId);
        int i = 0;
        if (list != null){
            for(ContractOrderDO contractOrderDO : list){
                if (contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode() || contractOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode()){
                    i++;
                    Long orderId = contractOrderDO.getId();
                    cancelOrder(userId, orderId);
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
    public ResultCode placeOrder(ContractOrderDO contractOrderDO) throws Exception{
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
            log.error("Contract Is Null");
            throw new BusinessException(ResultCodeEnum.CONTRANCT_IS_NULL.getCode(),ResultCodeEnum.CONTRANCT_IS_NULL.getMessage());
        }
        contractOrderDO.setContractName(contractCategoryDO.getContractName());
        contractOrderDO.setCloseType(OrderCloseTypeEnum.MANUAL.getCode());
        ResultCode resultCode = new ResultCode();
        insertOrderRecord(contractOrderDO);
        BigDecimal totalLockAmount = getTotalLockAmount(contractOrderDO.getUserId());
        //查询用户合约冻结金额
        UserContractDTO userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
        BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
        if (lockedAmount.compareTo(totalLockAmount) < 0 ){
            lockContractAccount(userContractDTO, totalLockAmount);
        }

        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(0L);
        contractOrderDTO.setContractId(contractOrderDO.getContractId().intValue());
        redisManager.contractOrderSave(contractOrderDTO);
        //推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId().intValue());
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder", orderMessage);
        if (!sendRet){
            log.error("Send RocketMQ Message Failed ");
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }


    @Transactional(rollbackFor = {Exception.class, RuntimeException.class, BusinessException.class})
    public ResultCode cancelOrder(Long userId, Long orderId) throws Exception{
        ResultCode resultCode = new ResultCode();
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByIdAndUserId(orderId, userId);
        Integer status = contractOrderDO.getStatus();
        boolean judegRet = getJudegRet(orderId,contractOrderDO.getOrderDirection(),new BigDecimal(contractOrderDO.getUnfilledAmount()));
        if (!judegRet){
            resultCode.setCode(ResultCodeEnum.ORDER_CAN_NOT_CANCLE.getCode());
            resultCode.setMessage(ResultCodeEnum.ORDER_CAN_NOT_CANCLE.getMessage());
            return resultCode;
        }
        if (status == OrderStatusEnum.COMMIT.getCode() || status == OrderStatusEnum.CANCEL.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.CANCEL.getCode());
        }else if (status == OrderStatusEnum.PART_MATCH.getCode() || status == OrderStatusEnum.PART_CANCEL.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.PART_CANCEL.getCode());
        }else if (status == OrderStatusEnum.MATCH.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
            resultCode.setCode(ResultCodeEnum.ORDER_IS_CANCLED.getCode());
            resultCode.setMessage(ResultCodeEnum.ORDER_IS_CANCLED.getMessage());
            return resultCode;
        }else {
            resultCode.setCode(ResultCodeEnum.ORDER_STATUS_ILLEGAL.getCode());
            resultCode.setMessage(ResultCodeEnum.ORDER_STATUS_ILLEGAL.getMessage());
            return resultCode;
        }
        int ret = contractOrderMapper.updateByOpLock(contractOrderDO);
        if (ret > 0){
            UserContractDTO userContractDTO = getAssetService().getContractAccount(userId);
            BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
            BigDecimal totalLockAmount = getTotalLockAmount(userId);
            if (lockedAmount.compareTo(totalLockAmount) != 0 ){
                lockContractAccount(userContractDTO, totalLockAmount);
            }
        }else {
            log.error("update contractOrder Failed");
            throw new BusinessException(ResultCodeEnum.UPDATE_CONTRACT_ORDER_FAILED.getCode(),ResultCodeEnum.UPDATE_CONTRACT_ORDER_FAILED.getMessage());
        }
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(contractOrderDTO.getTotalAmount()-contractOrderDTO.getUnfilledAmount());
        contractOrderDTO.setContractId(contractOrderDO.getContractId().intValue());
        redisManager.contractOrderSave(contractOrderDTO);
        //todo 推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId().intValue());
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder", orderMessage);
        if (!sendRet){
            log.info("Send RocketMQ Message Failed ");
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
        if (queryList != null && queryList.size() != 0 && contractOrderlist != null && contractOrderlist.size() != 0){
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
                                BigDecimal askPositionEntrustAmount = positionUnfilledAmount.multiply(contractSize).multiply(askCurrentPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
                                totalAskExtraEntrustAmount = totalAskExtraEntrustAmount.add(getExtraEntrustAmount(bidList,askList,positionType,positionUnfilledAmount,askPositionEntrustAmount,lever,contractSize));
                            }else if (positionType == PositionTypeEnum.EMPTY.getCode()){
                                BigDecimal bidPositionEntrustAmount = positionUnfilledAmount.multiply(contractSize).multiply(bidCurrentPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
                                totalBidExtraEntrustAmount = totalBidExtraEntrustAmount.add(getExtraEntrustAmount(bidList,askList,positionType,positionUnfilledAmount,bidPositionEntrustAmount,lever,contractSize));
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

    public void insertOrderRecord(ContractOrderDO contractOrderDO){
        contractOrderDO.setStatus(8);
        contractOrderDO.setFee(Constant.FEE_RATE);
        contractOrderDO.setUnfilledAmount(contractOrderDO.getTotalAmount());
        int insertContractOrderRet = contractOrderMapper.insertSelective(contractOrderDO);
        if (insertContractOrderRet <= 0){
            log.error("insert contractOrder failed");
            throw new RuntimeException("insert contractOrder failed");
        }
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
            throw new BusinessException(ResultCodeEnum.UPDATE_CONTRACT_ACCOUNT_LOCKEDAMOUNT_FAILED.getCode(),ResultCodeEnum.UPDATE_CONTRACT_ACCOUNT_LOCKEDAMOUNT_FAILED.getMessage());
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
        //存入Redis缓存
        ContractOrderDTO bidContractOrderDTO = new ContractOrderDTO();
        ContractOrderDTO askContractOrderDTO = new ContractOrderDTO();
        org.springframework.beans.BeanUtils.copyProperties(askContractOrder, askContractOrderDTO);
        askContractOrderDTO.setContractId(askContractOrder.getContractId().intValue());
        org.springframework.beans.BeanUtils.copyProperties(bidContractOrder, bidContractOrderDTO);
        bidContractOrderDTO.setContractId(bidContractOrder.getContractId().intValue());
        askContractOrderDTO.setCompleteAmount(contractMatchedOrderDTO.getFilledAmount());
        bidContractOrderDTO.setCompleteAmount(contractMatchedOrderDTO.getFilledAmount());
        bidContractOrderDTO.setStatus(contractMatchedOrderDTO.getBidOrderStatus());
        askContractOrderDTO.setStatus(contractMatchedOrderDTO.getAskOrderStatus());
        redisManager.contractOrderSave(askContractOrderDTO);
        redisManager.contractOrderSave(bidContractOrderDTO);
        log.info("---------------"+contractMatchedOrderDTO.toString());
        log.info("---------------"+askContractOrder.toString());
        log.info("---------------"+bidContractOrder.toString());
        if (askContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0
                || bidContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0){
            log.error(ResultCodeEnum.ORDER_UNFILLEDAMOUNT_NOT_ENOUGHT.getMessage());
            throw new RuntimeException(ResultCodeEnum.ORDER_UNFILLEDAMOUNT_NOT_ENOUGHT.getMessage());
        }
        if (askContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()
                && bidContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error(ResultCodeEnum.ASK_AND_BID_ILLEGAL.getMessage());
            throw new RuntimeException(ResultCodeEnum.ASK_AND_BID_ILLEGAL.getMessage());
        }
        if (askContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error(ResultCodeEnum.ASK_ILLEGAL.getMessage());
            throw new RuntimeException(ResultCodeEnum.ASK_ILLEGAL.getMessage());
        }
        if (bidContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()){
            log.error(ResultCodeEnum.BID_ILLEGAL.getMessage());
            throw new RuntimeException(ResultCodeEnum.BID_ILLEGAL.getMessage());
        }
        askContractOrder.setStatus(contractMatchedOrderDTO.getAskOrderStatus());
        bidContractOrder.setStatus(contractMatchedOrderDTO.getBidOrderStatus());
        updateContractAccount(askContractOrder, contractMatchedOrderDTO);
        updateContractAccount(bidContractOrder, contractMatchedOrderDTO);

        ContractMatchedOrderDO contractMatchedOrderDO = com.fota.trade.common.BeanUtils.copy(contractMatchedOrderDTO);
        BigDecimal fillMul = contractMatchedOrderDO.getFilledAmount().multiply(contractMatchedOrderDO.getFilledPrice()).multiply(Constant.FEE_RATE).setScale(2, BigDecimal.ROUND_UP);
        contractMatchedOrderDO.setFee(fillMul);
        contractMatchedOrderDO.setAskUserId(askContractOrder.getUserId());
        contractMatchedOrderDO.setBidUserId(bidContractOrder.getUserId());
        contractMatchedOrderDO.setAskCloseType(askContractOrder.getCloseType().byteValue());
        contractMatchedOrderDO.setBidCloseType(bidContractOrder.getCloseType().byteValue());
        contractMatchedOrderDO.setGmtCreate(new Date());
        try {
            int ret = contractMatchedOrderMapper.insert(contractMatchedOrderDO);
            if (ret < 1){
                log.error("保存Contract订单数据到数据库失败({})", contractMatchedOrderDO);
                throw new RuntimeException(ResultCodeEnum.UPDATE_MATCH_ORDER_FAILED.getMessage());
            }
        } catch (Exception e) {
            log.error("保存Contract订单数据到数据库失败({})", contractMatchedOrderDO, e);
            throw new RuntimeException(ResultCodeEnum.UPDATE_MATCH_ORDER_FAILED.getMessage());
        }

        // 向MQ推送消息
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.getContractCategoryById(askContractOrder.getContractId());
        ContractMatchedOrderMarketDTO contractMatchedOrderMarketDTO =new ContractMatchedOrderMarketDTO();
        contractMatchedOrderMarketDTO.setContractMatchedOrderId(contractMatchedOrderDO.getId());
        contractMatchedOrderMarketDTO.setContractId(askContractOrder.getContractId());
        contractMatchedOrderMarketDTO.setContractName(askContractOrder.getContractName());
        contractMatchedOrderMarketDTO.setFilledPrice(new BigDecimal(contractMatchedOrderDTO.getFilledPrice()));
        contractMatchedOrderMarketDTO.setFilledAmount(new BigDecimal(contractMatchedOrderDTO.getFilledAmount()));
        contractMatchedOrderMarketDTO.setFilledDate(contractMatchedOrderDO.getGmtCreate());
        contractMatchedOrderMarketDTO.setAssetName(contractCategoryDO.getAssetName());
        contractMatchedOrderMarketDTO.setContractType(contractCategoryDO.getContractType());
        contractMatchedOrderMarketDTO.setBidUserId(bidContractOrder.getUserId());
        contractMatchedOrderMarketDTO.setAskUserId(askContractOrder.getUserId());
        contractMatchedOrderMarketDTO.setAskOrderId(askContractOrder.getId());
        contractMatchedOrderMarketDTO.setBidOrderId(bidContractOrder.getId());
        rocketMqManager.producer("trade", "contract", contractMatchedOrderMarketDTO.getContractMatchedOrderId().toString(),
                JSONObject.toJSONString(contractMatchedOrderMarketDTO));

        // 向Redis存储消息
        ContractMatchedOrderTradeDTO contractMatchedOrderTradeDTO = new ContractMatchedOrderTradeDTO();
        contractMatchedOrderTradeDTO.setContractMatchedOrderId(contractMatchedOrderDO.getId());
        contractMatchedOrderTradeDTO.setAskOrderId(contractMatchedOrderDO.getAskOrderId());
        contractMatchedOrderTradeDTO.setBidOrderId(contractMatchedOrderDO.getBidOrderId());
        contractMatchedOrderTradeDTO.setFilledAmount(contractMatchedOrderDO.getFilledAmount());
        contractMatchedOrderTradeDTO.setFilledPrice(contractMatchedOrderDO.getFilledPrice());
        contractMatchedOrderTradeDTO.setFilledDate(contractMatchedOrderDO.getGmtCreate());
        contractMatchedOrderTradeDTO.setMatchType((int)contractMatchedOrderDO.getMatchType());
        contractMatchedOrderTradeDTO.setContractId((long)askContractOrder.getContractId());
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
        log.info("========完成撮合({})=======", System.currentTimeMillis());

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
        BigDecimal addedTotalLocked = totalLockAmount.subtract(lockedAmount);
        //todo 更新余额
        try {
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
            BigDecimal averagePrice = PriceUtil.getAveragePrice(contractOrderDO.getAveragePrice(),
                    new BigDecimal(contractOrderDO.getTotalAmount()),
                    new BigDecimal(filledAmount),
                    new BigDecimal(filledPrice));
            ret = contractOrderMapper.updateByFilledAmount(contractOrderDO.getId(), contractOrderDO.getStatus(), filledAmount, averagePrice);
            if (ret >0){
                ContractOrderDO contractOrderDO2 = contractOrderMapper.selectByPrimaryKey(contractOrderDO.getId());
                if (contractOrderDO2.getUnfilledAmount().equals(0L)){
                    contractOrderDO2.setStatus(OrderStatusEnum.MATCH.getCode());
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
