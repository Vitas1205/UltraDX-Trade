package com.fota.trade.manager;

import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.client.common.ResultCodeEnum;
import com.fota.client.domain.CompetitorsPriceDTO;
import com.fota.trade.common.BusinessException;
import com.fota.trade.common.Constant;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.*;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
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



    @Transactional(rollbackFor = {Exception.class, RuntimeException.class, BusinessException.class})
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
    }

    @Transactional(rollbackFor = {Exception.class, RuntimeException.class, BusinessException.class})
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
    }


    public ResultCode cancelAllOrder(Long userId) throws Exception{
        ResultCode resultCode = null;
        List<ContractOrderDO> list = contractOrderMapper.selectByUserId(userId);
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
    public boolean placeOrderWithCompetitorsPrice(ContractOrderDO contractOrderDO, List<CompetitorsPriceDTO> list) throws Exception{
        ResultCode resultCode = null;
        if (contractOrderDO == null){
            return false;
        }
        insertOrderRecord(contractOrderDO);
        BigDecimal totalLockAmount = getTotalLockAmount(contractOrderDO, OrderOperateTypeEnum.PLACE_ORDER.getCode(), list);
            //查询用户合约冻结金额
            UserContractDTO userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
            BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
            if (lockedAmount.compareTo(totalLockAmount) < 0 && lockedAmount.add(totalLockAmount).compareTo(BigDecimal.ZERO) >= 0){
                lockContractAccount(userContractDTO, totalLockAmount, lockedAmount);
            }
            return true;
    }

    //获取追加冻结金额
    public BigDecimal getTotalLockAmount(ContractOrderDO contractOrderDO, Integer orderOperateType, List<CompetitorsPriceDTO> list) throws Exception{
        BigDecimal totalLockAmount = BigDecimal.ZERO;
        long userId = contractOrderDO.getUserId();
        List<UserPositionDO> positionlist = userPositionMapper.selectByUserId(userId);
        if (positionlist == null || positionlist.size() == 0){
            long contractId = contractOrderDO.getContractId();
            BigDecimal orderAmount = new BigDecimal(contractOrderDO.getTotalAmount());
            BigDecimal entrustPrice = contractOrderDO.getPrice();
            BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId,contractId));
            BigDecimal orderValue = orderAmount.multiply(entrustPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
            BigDecimal orderFee = orderValue.multiply(lever).multiply(Constant.FEE_RATE);
            totalLockAmount = orderValue.add(orderFee);
            if (orderOperateType == OrderOperateTypeEnum.CANCLE_ORDER.getCode()){
                return totalLockAmount.negate();
            }
            UserContractDTO userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
            BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
            if (lockedAmount.compareTo(totalLockAmount) >= 0){
                return lockedAmount;
            }
            return totalLockAmount;
        }else {
            BigDecimal totalAskExtraEntrustAmount = BigDecimal.ZERO;
            BigDecimal totalBidExtraEntrustAmount = BigDecimal.ZERO;
            for (UserPositionDO userPositionDO : positionlist){
                long contractId = userPositionDO.getContractId();
                //todo 根据contractId获取买一卖一价
                BigDecimal askCurrentPrice = BigDecimal.ZERO;
                BigDecimal bidCurrentPrice = BigDecimal.ZERO;
                for (CompetitorsPriceDTO competitorsPriceDTO:list){
                    if (competitorsPriceDTO.getId() == contractId && competitorsPriceDTO.getOrderDirection() == OrderDirectionEnum.ASK.getCode()){
                        askCurrentPrice = competitorsPriceDTO.getPrice();
                    }
                    if (competitorsPriceDTO.getId() == contractId && competitorsPriceDTO.getOrderDirection() == OrderDirectionEnum.BID.getCode()){
                        bidCurrentPrice = competitorsPriceDTO.getPrice();
                    }
                }
                List<ContractOrderDO> bidList = new ArrayList();
                List<ContractOrderDO> askList = new ArrayList();
                BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId,contractId));
                Integer positionType = userPositionDO.getPositionType();
                BigDecimal positionUnfilledAmount = new BigDecimal(userPositionDO.getUnfilledAmount());
                List <ContractOrderDO> contractOrderList = contractOrderMapper.selectByContractIdAndUserId(contractId, userId);
                if (positionType == PositionTypeEnum.OVER.getCode()){
                    BigDecimal askPositionEntrustAmount = positionUnfilledAmount.multiply(askCurrentPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    if (contractOrderList != null){
                        for (ContractOrderDO contractOrder : contractOrderList){
                            Integer orderDerection = contractOrder.getOrderDirection();
                            if (orderDerection == OrderDirectionEnum.ASK.getCode()){
                                bidList.add(contractOrder);
                            }else if (orderDerection == OrderDirectionEnum.BID.getCode()){
                                askList.add(contractOrder);
                            }
                        }
                        totalAskExtraEntrustAmount = totalAskExtraEntrustAmount.add(getExtraEntrustAmount(bidList,askList,positionType,positionUnfilledAmount,askPositionEntrustAmount,lever));
                    }
                }else if (positionType == PositionTypeEnum.EMPTY.getCode()){
                    BigDecimal bidPositionEntrustAmount = positionUnfilledAmount.multiply(bidCurrentPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    if (contractOrderList != null){
                        for (ContractOrderDO contractOrder : contractOrderList){
                            Integer orderDerection = contractOrder.getOrderDirection();
                            if (orderDerection == 2){
                                bidList.add(contractOrder);
                            }else if (orderDerection == 1){
                                askList.add(contractOrder);
                            }
                        }
                        totalBidExtraEntrustAmount = totalBidExtraEntrustAmount.add(getExtraEntrustAmount(bidList,askList,positionType,positionUnfilledAmount,bidPositionEntrustAmount,lever));
                    }
                }
            }
            totalLockAmount = totalBidExtraEntrustAmount.add(totalAskExtraEntrustAmount);
            return totalLockAmount;
        }
    }

    //获取多空仓额外保证金
    public BigDecimal getExtraEntrustAmount(List<ContractOrderDO> bidList, List<ContractOrderDO> askList, Integer positionType,
                                               BigDecimal positionUnfilledAmount, BigDecimal positionEntrustAmount, BigDecimal lever){
        if (positionType == PositionTypeEnum.OVER.getCode()){
            BigDecimal max1 = BigDecimal.ZERO;
            BigDecimal max2 = BigDecimal.ZERO;
            BigDecimal bidPositionUnfilledAmount = positionUnfilledAmount;
            BigDecimal totalAskEntrustAmount = BigDecimal.ZERO;
            BigDecimal totalBidEntrustAmount = BigDecimal.ZERO;
            BigDecimal askEntrustAmount = BigDecimal.ZERO;
            BigDecimal askPositionEntrustAmount = positionEntrustAmount;
            if (askList != null){
                List<ContractOrderDO> sortedAskList = sortListEsc(askList);
                for (int i = 0;i < sortedAskList.size();i++){
                    bidPositionUnfilledAmount = bidPositionUnfilledAmount.subtract(new BigDecimal(sortedAskList.get(i).getUnfilledAmount()));
                    if (bidPositionUnfilledAmount.compareTo(BigDecimal.ZERO) <= 0){
                        BigDecimal restAmount = bidPositionUnfilledAmount.negate().multiply(sortedAskList.get(i).getPrice()).divide(lever, 8,BigDecimal.ROUND_DOWN);
                        BigDecimal restFee = restAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        BigDecimal totalRest = restAmount.add(restFee);
                        for (int j = i + 1;j < sortedAskList.size();j++){
                            BigDecimal orderAmount = sortedAskList.get(j).getPrice().multiply(new BigDecimal(sortedAskList.get(j).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                            BigDecimal orderFee = orderAmount.multiply(Constant.FEE_RATE);
                            askEntrustAmount = askEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalAskEntrustAmount = totalRest.add(askEntrustAmount);
                        break;
                    }
                }
                if (totalAskEntrustAmount.compareTo(askPositionEntrustAmount) > 0){
                    max1 = totalAskEntrustAmount.subtract(askPositionEntrustAmount);
                }
            }
            if (bidList != null){
                for (int i = 0;i < bidList.size();i++){
                    BigDecimal orderAmount = bidList.get(i).getPrice().multiply(new BigDecimal(bidList.get(i).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(Constant.FEE_RATE);
                    totalBidEntrustAmount = totalBidEntrustAmount.add(orderAmount.add(orderFee));
                }
                if (totalBidEntrustAmount.compareTo(max1) > 0){
                    max2 = totalBidEntrustAmount;
                    return max2;
                }
            }
            return max1;
        }else if (positionType == PositionTypeEnum.EMPTY.getCode()){
            BigDecimal max1 = BigDecimal.ZERO;
            BigDecimal max2 = BigDecimal.ZERO;
            BigDecimal askPositionUnfilledAmount = positionUnfilledAmount;
            BigDecimal totalAskEntrustAmount = BigDecimal.ZERO;
            BigDecimal totalBidEntrustAmount = BigDecimal.ZERO;
            BigDecimal bidEntrustAmount = BigDecimal.ZERO;
            BigDecimal bidPositionEntrustAmount = positionEntrustAmount;
            if (bidList != null){
                List<ContractOrderDO> sortedBidList = sortListDesc(bidList);
                for (int i = 0;i < sortedBidList.size();i++){
                    askPositionUnfilledAmount = askPositionUnfilledAmount.subtract(new BigDecimal(sortedBidList.get(i).getUnfilledAmount()));
                    if (askPositionUnfilledAmount.compareTo(BigDecimal.ZERO) <= 0){
                        BigDecimal restAmount = askPositionUnfilledAmount.negate().multiply(sortedBidList.get(i).getPrice()).divide(lever, 8,BigDecimal.ROUND_DOWN);
                        BigDecimal restFee = restAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        BigDecimal totalRest = restAmount.add(restFee);
                        for (int j = i + 1;j < sortedBidList.size();j++){
                            BigDecimal orderAmount = sortedBidList.get(j).getPrice().multiply(new BigDecimal(sortedBidList.get(j).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                            BigDecimal orderFee = orderAmount.multiply(Constant.FEE_RATE);
                            bidEntrustAmount = bidEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalBidEntrustAmount = totalRest.add(bidEntrustAmount);
                        break;
                    }
                }
                if (totalBidEntrustAmount.compareTo(bidPositionEntrustAmount) > 0){
                    max1 = totalBidEntrustAmount.subtract(bidPositionEntrustAmount);
                }
            }
            if (askList != null){
                for (int i = 0;i < askList.size();i++){
                    BigDecimal orderAmount = askList.get(i).getPrice().multiply(new BigDecimal(askList.get(i).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(Constant.FEE_RATE);
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
    public void lockContractAccount(UserContractDTO userContractDTO, BigDecimal totalLockAmount, BigDecimal lockedAmount) throws Exception{
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        if (amount.compareTo(totalLockAmount) < 0){
            throw new RuntimeException("ContractAccount USDK Not Enough");
        }
        //调用RPC接口冻结合约账户（加锁）
        BigDecimal addLockAmount = totalLockAmount.subtract(lockedAmount);
        long gmtModified =  userContractDTO.getGmtModified().getTime();
        Boolean lockContractAmountRet = getContractService().lockContractAmount(userContractDTO.getUserId(), addLockAmount.toString(), gmtModified);
        if (!lockContractAmountRet){
            throw new RuntimeException("Lock ContractAmount Failed");
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

    public BigDecimal getTotalLockAmount(ContractOrderDO contractOrderDO){
        Integer lever = contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(),contractOrderDO.getContractId());
        BigDecimal totalValue = contractOrderDO.getPrice().multiply(new BigDecimal(contractOrderDO.getTotalAmount()))
                .multiply(new BigDecimal(0.01)).divide(new BigDecimal(lever), 8,BigDecimal.ROUND_DOWN);
        BigDecimal fee = totalValue.multiply(Constant.FEE_RATE).multiply(new BigDecimal(lever));
        return totalValue.add(fee);
    }





}
