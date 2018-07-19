package com.fota.trade.manager;

import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.OrderMessage;
import com.fota.trade.domain.ResultCode;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.enums.OrderOperateTypeEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
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
    private ContractService contractService;
    @Autowired
    private AssetService assetService;

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


    /*public ResultCode placeOrder(ContractOrderDO contractOrderDO) throws Exception{
        ResultCode resultCode = new ResultCode();
        Long userId = contractOrderDO.getUserId();
        BigDecimal toatlLockAmount = getTotalLockAmount(contractOrderDO);
        //查询合约账户
        UserContractDTO userContractDTO = getAssetService().getContractAccount(userId);
        //查询持仓，统计所有保证金
        List<UserPositionDO> list = userPositionMapper.selectByUserId(userId);
        BigDecimal tatalEarnestAmount = BigDecimal.ZERO;
        BigDecimal earnestAmount = BigDecimal.ZERO;
        //todo 合约的买一卖一价格从入参获取
        BigDecimal contractValue = BigDecimal.ZERO;
        for(UserPositionDO userPositionDO : list){
            Long contractId = userPositionDO.getContractId();
            Long unfillesAmount = userPositionDO.getUnfilledAmount();
            Integer lever = userPositionDO.getLever();
            earnestAmount = getEarnestAmount(contractId, lever, unfillesAmount, contractValue);
            tatalEarnestAmount = earnestAmount.add(tatalEarnestAmount);
        }
        //判断是否需要追加冻结
        BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
        if (toatlLockAmount.compareTo(lockedAmount) > 0){
            //需要追加冻结判断余额是否足够
            BigDecimal rights = new BigDecimal(userContractDTO.getAmount());
            if (rights.subtract(tatalEarnestAmount).compareTo(toatlLockAmount) < 0){
                throw new RuntimeException("ContractAccount Available Amount Not Enough");
            }
        }
        //todo 调用RPC接口冻结合约账户（加锁）
        long gmtModified =  userContractDTO.getGmtModified();
        BigDecimal addLockedBalance = toatlLockAmount.subtract(lockedAmount);
        //插入合约订单
        int insertContractOrderRet = contractOrderMapper.insertSelective(contractOrderDO);
        if (insertContractOrderRet <= 0){
            throw new RuntimeException("insert contractOrder failed");
        }
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(BigDecimal.ZERO);
        redisManager.contractOrderSave(contractOrderDTO);
        resultCode = resultCode.setCode(0).setMessage("success");

        return resultCode;
    }*/

    @Transactional(rollbackFor = {Exception.class,RuntimeException.class})
    public ResultCode placeOrder(ContractOrderDO contractOrderDO) throws Exception{
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
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
        int insertContractOrderRet = contractOrderMapper.insertSelective(contractOrderDO);
        if (insertContractOrderRet <= 0){
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
        Date gmtModified =  userContractDTO.getGmtModified();
        Boolean lockContractAmountRet = getContractService().lockContractAmount(userId,toatlLockAmount.toString(),gmtModified.getTime());
        if (!lockContractAmountRet){
            throw new RuntimeException("Lock ContractAmount Failed");
        }

        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(BigDecimal.ZERO);
        redisManager.contractOrderSave(contractOrderDTO);
        //todo 推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
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
            resultCode.setCode(8);
            resultCode.setMessage("There is no order to be withdrawn");
            return resultCode;
        }else {
            resultCode.setCode(13);
            resultCode.setMessage("contractOrder status illegal");
            return resultCode;
        }
        int ret = contractOrderMapper.updateByOpLock(contractOrderDO);
        if (ret > 0){
            Long unfilledAmount = contractOrderDO.getUnfilledAmount();
            BigDecimal price = contractOrderDO.getPrice();
            BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(),contractOrderDO.getContractId()));
            BigDecimal unlockPrice = new BigDecimal(unfilledAmount).multiply(price).multiply(new BigDecimal(0.01)).divide(lever);
            BigDecimal unlockFee = unlockPrice.multiply(Constant.FEE_RATE).multiply(lever);
            BigDecimal totalUnlockPrice = unlockPrice.add(unlockFee);
            Boolean lockContractAmountRet =  getContractService().lockContractAmount(userId,totalUnlockPrice.negate().toString(),0L);
            if (!lockContractAmountRet){
                throw new RuntimeException("lockContractAmountRet failed");
            }
        }else {
            resultCode.setCode(14);
            resultCode.setMessage("update contractOrder Failed");
        }
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(new BigDecimal(contractOrderDTO.getTotalAmount()-contractOrderDTO.getUnfilledAmount()));
        redisManager.contractOrderSave(contractOrderDTO);
        //todo 推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
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
        int ret = -1;
        for(ContractOrderDO contractOrderDO : list){
            Long orderId = contractOrderDO.getId();
            resultCode = cancelOrder(userId, orderId);
            ret = resultCode.getCode();
            if (ret != 0 && ret != 8 && ret != 13){
                throw new RuntimeException("cancelAllOrder failed");
            }
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }

    //保证金计算
    public BigDecimal getEarnestAmount(Long contractId, Integer lever, Long unfilledAmount, BigDecimal contractValue){
        BigDecimal earnestAmount = contractValue.multiply(new BigDecimal(unfilledAmount)).divide(new BigDecimal(lever));
        return earnestAmount;
    }

    //todo 下单获取所需冻结总金额 (下单)
   /* public BigDecimal getTotalLockAmount(ContractOrderDO contractOrderDO){
        BigDecimal totalLockAmount = BigDecimal.ZERO;
        BigDecimal singleLockAmount;
        BigDecimal singleEarnestAmount;
        Long userId = contractOrderDO.getUserId();
        Long myContractId = contractOrderDO.getContractId();
        //查询用户持仓列表
        List<UserPositionDO> positionDOlist = userPositionMapper.selectByUserId(userId);
        if (positionDOlist != null){
            for (UserPositionDO userPositionDO : positionDOlist){
                Integer lever = userPositionDO.getLever();
                Long positionAmount = userPositionDO.getUnfilledAmount();
                //todo 获取ContractValue计算保证金
                BigDecimal contractValue = BigDecimal.ZERO;
                BigDecimal earnestAmount = getEarnestAmount(userPositionDO.getContractId(), lever, positionAmount, contractValue);
                Integer positionType = userPositionDO.getPositionType();

                Long contractId = userPositionDO.getContractId();
                //获取对应合约的委托列表
                List<ContractOrderDO> orderList = contractOrderMapper.selectByContractIdAndUserId(contractId, userId);
                if(orderList != null){
                    List<ContractOrderDO> askOrderList = null;
                    List<ContractOrderDO> bidOrderList = null;
                    Long askAmount = 0L;
                    Long bidAmount = 0L;
                    //把当比委托加入列表一起计算
                    if (myContractId.equals(userPositionDO.getContractId())){
                        orderList.add(contractOrderDO);
                    }
                    for(ContractOrderDO contractOrderDO1 : orderList){
                        //获取卖单的列表和总量
                        if (contractOrderDO1.getOrderDirection() == 1){
                            askOrderList.add(contractOrderDO1);
                            askAmount += contractOrderDO1.getUnfilledAmount();
                        }else if (contractOrderDO1.getOrderDirection() == 2){
                            bidOrderList.add(contractOrderDO1);
                            bidAmount += contractOrderDO1.getUnfilledAmount();
                        }
                    }
                    //升序排列
                    if (askOrderList != null){
                        askOrderList = sortList(askOrderList);
                    }
                    if (bidOrderList != null){
                        bidOrderList = sortList(bidOrderList);
                    }

                }

            }
        }else {

        }


        return totalLockAmount;
    }*/

    public BigDecimal getTotalLockAmount(ContractOrderDO contractOrderDO){
        Integer lever = contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(),contractOrderDO.getContractId());
        BigDecimal totalValue = contractOrderDO.getPrice().multiply(new BigDecimal(contractOrderDO.getTotalAmount()))
                .multiply(new BigDecimal(0.01)).divide(new BigDecimal(lever));
        BigDecimal fee = totalValue.multiply(Constant.FEE_RATE).multiply(new BigDecimal(lever));
        return totalValue.add(fee);
    }


    public List<ContractOrderDO> sortList(List<ContractOrderDO> list){
        List<ContractOrderDO> sortedList = list.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice))
                .collect(Collectors.toList());
        return sortedList;
    }

    /*public BigDecimal  getLockAmount(Integer positionType, List<ContractOrderDO> askOrderList, ){
        BigDecimal lockAmount;

    }*/

}
