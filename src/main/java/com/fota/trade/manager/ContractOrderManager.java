package com.fota.trade.manager;

import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.client.common.ResultCodeEnum;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.*;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.thrift.ThriftJ;
import com.fota.trade.domain.enums.OrderOperateTypeEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Component
@Slf4j
public class ContractOrderManager {

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
    private ThriftJ thriftJ;
    @Value("${fota.asset.server.thrift.port}")
    private int thriftPort;
    @PostConstruct
    public void init() {
        thriftJ.initService("FOTA-ASSET", thriftPort);
    }
    private ContractService.Client getContractService() {
        ContractService.Client serviceClient =
                thriftJ.getServiceClient("FOTA-ASSET")
                        .iface(ContractService.Client.class, "contractService");
        return serviceClient;
    }
    private AssetService.Client getAssetService() {
        AssetService.Client serviceClient =
                thriftJ.getServiceClient("FOTA-ASSET")
                        .iface(AssetService.Client.class, "assetService");
        return serviceClient;
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



    @Transactional(rollbackFor = {Exception.class,RuntimeException.class})
    public ResultCode placeOrder(ContractOrderDO contractOrderDO) throws Exception{
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
            log.error("Contract Name Is Null");
            throw new RuntimeException("Contract Name Is Null");
        }
        contractOrderDO.setContractName(contractCategoryDO.getContractName());
        ResultCode resultCode = new ResultCode();
        Long userId = contractOrderDO.getUserId();
        BigDecimal toatlLockAmount = getTotalLockAmount(contractOrderDO);
        //插入合约订单
        contractOrderDO.setStatus(8);
        contractOrderDO.setFee(Constant.FEE_RATE);
        contractOrderDO.setUnfilledAmount(contractOrderDO.getTotalAmount());
        contractOrderDO.setCloseType(1);
        int insertContractOrderRet = contractOrderMapper.insertSelective(contractOrderDO);
        if (insertContractOrderRet <= 0){
            log.error("insert contractOrder failed");
            throw new RuntimeException("insert contractOrder failed");
        }
        //查询合约账户
        UserContractDTO userContractDTO = getAssetService().getContractAccount(userId);
        BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        BigDecimal availableAmount = amount.subtract(lockedAmount);
        if (availableAmount.compareTo(toatlLockAmount) < 0){
            throw new RuntimeException("ContractAccount USDK Not Enough");
        }
        //todo 调用RPC接口冻结合约账户（加锁）
        long gmtModified =  userContractDTO.getGmtModified();
        Boolean lockContractAmountRet = getContractService().lockContractAmount(userId,toatlLockAmount.toString(),gmtModified);
        if (!lockContractAmountRet){
            throw new RuntimeException("Lock ContractAmount Failed");
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
        resultCode = resultCode.setCode(0).setMessage("success");
        return resultCode;
    }

    @Transactional(rollbackFor = {Exception.class,RuntimeException.class})
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
            resultCode = resultCode.setCode(ResultCodeEnum.ORDER_IS_CANCLED.getCode()).setMessage(ResultCodeEnum.ORDER_IS_CANCLED.getMessage());
            return resultCode;
        }else {
            resultCode = resultCode.setCode(ResultCodeEnum.ORDER_STATUS_ILLEGAL.getCode()).setMessage(ResultCodeEnum.ORDER_STATUS_ILLEGAL.getMessage());
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
                throw new RuntimeException("lockContractAmountRet failed");
            }
        }else {
            resultCode = resultCode.setCode(14).setMessage("update contractOrder Failed");
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
        resultCode = resultCode.setCode(0).setMessage("success");
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
            return resultCode.setCode(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getCode()).setMessage(ResultCodeEnum.NO_CANCELLABLE_ORDERS.getMessage());
        }
        resultCode = resultCode.setCode(0).setMessage("success");
        return resultCode;
    }

    public boolean placeOrderNew(ContractOrderDO contractOrderDO) throws Exception{
        //查询持仓表
        long userId = contractOrderDO.getUserId();
        List<UserPositionDO> positionlist = userPositionMapper.selectByUserId(userId);
        if (positionlist == null){
            long contractId = contractOrderDO.getContractId();
            BigDecimal orderAmount = new BigDecimal(contractOrderDO.getTotalAmount());
            BigDecimal entrustPrice = contractOrderDO.getPrice();
            BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId,contractId));
            insertOrderRecord(contractOrderDO);
            BigDecimal orderValue = orderAmount.multiply(entrustPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
            BigDecimal orderFee = orderValue.multiply(lever).multiply(Constant.FEE_RATE);
            BigDecimal totalLockAmount = orderValue.add(orderFee);
            //查询用户合约冻结金额
            UserContractDTO userContractDTO = getAssetService().getContractAccount(userId);
            BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
            if (lockedAmount.compareTo(totalLockAmount) < 0){
                lockContractAccount(userContractDTO, totalLockAmount, lockedAmount);
            }
            return true;
        }else {
            BigDecimal totalAskExtraEntrustAmount = BigDecimal.ZERO;
            BigDecimal totalBidExtraEntrustAmount = BigDecimal.ZERO;
            for (UserPositionDO userPositionDO : positionlist){
                long contractId = userPositionDO.getContractId();
                //todo 根据contractId获取买一卖一价
                BigDecimal askCurrentPrice = BigDecimal.ZERO;
                BigDecimal bidCurrentPrice = BigDecimal.ZERO;
                List<ContractOrderDO> bidList = new ArrayList();
                List<ContractOrderDO> askList = new ArrayList();
                BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId,contractId));
                Integer positionType = userPositionDO.getPositionType();
                BigDecimal positionUnfilledAmount = new BigDecimal(userPositionDO.getUnfilledAmount());
                List <ContractOrderDO> contractOrderList = contractOrderMapper.selectByContractIdAndUserId(contractId, userId);
                if (positionType ==1){          //多仓位，可以和卖单冲抵
                    BigDecimal bidPositionEntrustAmount = positionUnfilledAmount.multiply(bidCurrentPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    if (contractOrderList != null){
                        for (ContractOrderDO contractOrder : contractOrderList){
                            Integer orderDerection = contractOrder.getOrderDirection();
                            if (orderDerection == 1){
                                bidList.add(contractOrder);
                            }else if (orderDerection == 2){
                                askList.add(contractOrder);
                            }
                        }
                        totalAskExtraEntrustAmount = totalAskExtraEntrustAmount.add(getAskExtraEntrustAmount(bidList,askList,positionType,positionUnfilledAmount,bidPositionEntrustAmount,lever));
                    }
                }else if (positionType == 2){   //空仓位，可以和买单冲抵
                    BigDecimal askPositionEntrustAmount = positionUnfilledAmount.multiply(askCurrentPrice).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    if (contractOrderList != null){
                        for (ContractOrderDO contractOrder : contractOrderList){
                            Integer orderDerection = contractOrder.getOrderDirection();
                            if (orderDerection == 1){
                                bidList.add(contractOrder);
                            }else if (orderDerection == 2){
                                askList.add(contractOrder);
                            }
                        }
                        totalBidExtraEntrustAmount = totalBidExtraEntrustAmount.add(getAskExtraEntrustAmount(bidList,askList,positionType,positionUnfilledAmount,askPositionEntrustAmount,lever));
                    }
                }

            }
            BigDecimal totalLockAmount = totalBidExtraEntrustAmount.add(totalAskExtraEntrustAmount);
            //查询用户合约冻结金额
            UserContractDTO userContractDTO = getAssetService().getContractAccount(userId);
            BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
            if (lockedAmount.compareTo(totalLockAmount) < 0){
                lockContractAccount(userContractDTO, totalLockAmount, lockedAmount);
            }
            return true;
        }
    }

    //获取多空仓额外保证金
    public BigDecimal getAskExtraEntrustAmount(List<ContractOrderDO> bidList, List<ContractOrderDO> askList, Integer positionType,
                                               BigDecimal positionUnfilledAmount, BigDecimal PositionEntrustAmount, BigDecimal lever){
        if (positionType == 1){
            BigDecimal Max1 = BigDecimal.ZERO;
            BigDecimal Max2 = BigDecimal.ZERO;
            BigDecimal bidPositionUnfilledAmount = positionUnfilledAmount;
            BigDecimal totalAskEntrustAmount = BigDecimal.ZERO;
            BigDecimal totalBidEntrustAmount = BigDecimal.ZERO;
            BigDecimal AskEntrustAmount = BigDecimal.ZERO;
            BigDecimal bidPositionEntrustAmount = PositionEntrustAmount;
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
                            AskEntrustAmount = AskEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalAskEntrustAmount = totalRest.add(AskEntrustAmount);
                        break;
                    }
                }
                if (totalAskEntrustAmount.compareTo(bidPositionEntrustAmount) > 0){
                    Max1 = totalAskEntrustAmount.subtract(bidPositionEntrustAmount);
                }
            }
            if (bidList != null){
                for (int i = 0;i < bidList.size();i++){
                    BigDecimal orderAmount = bidList.get(i).getPrice().multiply(new BigDecimal(bidList.get(i).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(Constant.FEE_RATE);
                    totalBidEntrustAmount = totalBidEntrustAmount.add(orderAmount.add(orderFee));
                }
                if (totalBidEntrustAmount.compareTo(Max1) > 0){
                    Max2 = totalBidEntrustAmount;
                    return Max2;
                }
            }
            return Max1;
        }else if (positionType == 2){
            BigDecimal Max1 = BigDecimal.ZERO;
            BigDecimal Max2 = BigDecimal.ZERO;
            BigDecimal askPositionUnfilledAmount = positionUnfilledAmount;
            BigDecimal totalAskEntrustAmount = BigDecimal.ZERO;
            BigDecimal totalBidEntrustAmount = BigDecimal.ZERO;
            BigDecimal BidEntrustAmount = BigDecimal.ZERO;
            BigDecimal askPositionEntrustAmount = PositionEntrustAmount;
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
                            BidEntrustAmount = BidEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalBidEntrustAmount = totalRest.add(BidEntrustAmount);
                        break;
                    }
                }
                if (totalBidEntrustAmount.compareTo(askPositionEntrustAmount) > 0){
                    Max1 = totalBidEntrustAmount.subtract(askPositionEntrustAmount);
                }
            }
            if (askList != null){
                for (int i = 0;i < askList.size();i++){
                    BigDecimal orderAmount = askList.get(i).getPrice().multiply(new BigDecimal(askList.get(i).getUnfilledAmount())).divide(lever, 8,BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(Constant.FEE_RATE);
                    totalAskEntrustAmount = totalAskEntrustAmount.add(orderAmount.add(orderFee));
                }
                if (totalAskEntrustAmount.compareTo(Max1) > 0){
                    Max2 = totalAskEntrustAmount;
                    return Max2;
                }
            }
            return Max1;
        }else {
            throw new RuntimeException("positionType illegal");
        }
    }

    //插入合约订单记录
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
        long gmtModified =  userContractDTO.getGmtModified();
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
