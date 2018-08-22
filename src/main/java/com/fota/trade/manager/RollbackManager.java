package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.asset.service.ContractService;
import com.fota.trade.client.RollbackTask;
import com.fota.trade.domain.BaseQuery;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

import static com.fota.trade.client.constants.MatchedOrderStatus.DELETE;

/**
 * Created by Swifree on 2018/8/20.
 * Code is the law
 */
@Component
@Slf4j
public class RollbackManager {

    @Resource
    private ContractLeverManager contractLeverManager;
    @Resource
    private ContractMatchedOrderMapper contractMatchedOrderMapper;
    @Resource
    private ContractOrderMapper contractOrderMapper;
    @Resource
    private UserPositionMapper userPositionMapper;
    @Resource
    private ContractService contractService;
    @Resource
    private ContractOrderManager contractOrderManager;

    public void rollbackMatchedOrder(RollbackTask rollbackTask) {
        log.info("start rollbackTask:{}",JSON.toJSONString(rollbackTask));
        BaseQuery query = new BaseQuery();
        query.setSourceId((int) rollbackTask.getContractId());
        query.setStartTime(rollbackTask.getRollbackPoint());
        query.setEndTime(rollbackTask.getTaskStartPoint());
        query.setStartRow(rollbackTask.getStartRow());
        query.setEndRow(rollbackTask.getPageSize());

        List<ContractMatchedOrderDO> matchedOrderDOS = contractMatchedOrderMapper.queryMatchedOrder(query);
        matchedOrderDOS.stream().forEach(x -> rollbackMatchedOrder(x));

    }



    private void rollbackMatchedOrder(ContractMatchedOrderDO matchedOrderDO) {
        if (DELETE == matchedOrderDO.getStatus()) {
            return;
        }

        ContractOrderDO askContractOrder = contractOrderMapper.selectByPrimaryKey(matchedOrderDO.getAskOrderId());
        ContractOrderDO bidContractOrder = contractOrderMapper.selectByPrimaryKey(matchedOrderDO.getBidOrderId());

        int tmp = askContractOrder.getOrderDirection();

        askContractOrder.setOrderDirection(bidContractOrder.getOrderDirection());
        bidContractOrder.setOrderDirection(tmp);

        //更新委托状态
        long filledAmount = matchedOrderDO.getFilledAmount().negate().longValue();
        contractOrderManager.updateContractOrder(askContractOrder.getId(), filledAmount, matchedOrderDO.getFilledPrice());
        contractOrderManager.updateContractOrder(bidContractOrder.getId(), filledAmount, matchedOrderDO.getFilledPrice());

        contractMatchedOrderMapper.updateStatus(matchedOrderDO.getId(), DELETE);
        //更新持仓
        updatePosition(askContractOrder, matchedOrderDO);
        updatePosition(bidContractOrder, matchedOrderDO);

    }
    private void rollbackBalance(ContractOrderDO contractOrderDO,
                                 long oldPositionAmount,
                                 long newPositionAmount,
                                 ContractMatchedOrderDO matchedOrderDO,
                                 Integer lever) {
        BigDecimal filledAmount = matchedOrderDO.getFilledAmount();
        BigDecimal filledPrice = matchedOrderDO.getFilledPrice();
        BigDecimal fee = contractOrderDO.getFee();
        BigDecimal contractSize = contractOrderManager.getContractSize(contractOrderDO.getContractId());
        BigDecimal actualFee = filledPrice.multiply(filledAmount).multiply(fee).multiply(contractSize);
        BigDecimal addedTotalAmount = new BigDecimal(newPositionAmount - oldPositionAmount)
                .multiply(filledPrice)
                .multiply(contractSize)
                .divide(new BigDecimal(lever), 8, BigDecimal.ROUND_DOWN)
                .subtract(actualFee)
                .negate();
        boolean suc = contractService.addTotaldAmount(contractOrderDO.getUserId(), addedTotalAmount);
        if (!suc) {
            throw new RuntimeException("update balance failed");
        }
    }

    private void updatePosition(ContractOrderDO contractOrderDO, ContractMatchedOrderDO contractMatchedOrderDO) {
        BigDecimal filledAmount = contractMatchedOrderDO.getFilledAmount();
        BigDecimal filledPrice =contractMatchedOrderDO.getFilledPrice();
        Long userId = contractOrderDO.getUserId();
        Long contractId = contractOrderDO.getContractId();
        UserPositionDO userPositionDO = null;
        try {
            userPositionDO = userPositionMapper.selectByUserIdAndId(userId, contractId);
        } catch (Exception e) {
            log.error("userPositionMapper.selectByUserIdAndId({}, {})", userId, contractId, e);
            return;
        }
        Integer lever = new Integer(contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(), contractOrderDO.getContractId()));
        long oldPositionAmount = userPositionDO.getUnfilledAmount();
        if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
            long newTotalAmount = userPositionDO.getUnfilledAmount() + filledAmount.longValue();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
            BigDecimal newTotalPrice = oldTotalPrice.add(addedTotalPrice);
            BigDecimal newAvaeragePrice = newTotalPrice.divide(new BigDecimal(newTotalAmount), 8, BigDecimal.ROUND_DOWN);
            contractOrderManager.updateUserPosition(userPositionDO, newAvaeragePrice, newTotalAmount);
            rollbackBalance(contractOrderDO, oldPositionAmount, newTotalAmount, contractMatchedOrderDO, lever);
            return;
        }

        //成交单和持仓是反方向 （平仓）
        if (filledAmount.longValue() - userPositionDO.getUnfilledAmount() <= 0) {
            //不改变仓位方向
            long newTotalAmount = userPositionDO.getUnfilledAmount() - filledAmount.longValue();
            BigDecimal newAvaeragePrice = null;
            if (newTotalAmount != 0){
                newAvaeragePrice = userPositionDO.getAveragePrice().setScale(8, BigDecimal.ROUND_DOWN);
            }
            contractOrderManager.updateUserPosition(userPositionDO, newAvaeragePrice, newTotalAmount);
            rollbackBalance(contractOrderDO, oldPositionAmount, newTotalAmount, contractMatchedOrderDO, lever);
        } else {
            //改变仓位方向
            long newTotalAmount = filledAmount.longValue() - userPositionDO.getUnfilledAmount();
            BigDecimal newAvaeragePrice = filledPrice.setScale(8, BigDecimal.ROUND_DOWN);;
            userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
            contractOrderManager.updateUserPosition(userPositionDO, newAvaeragePrice, newTotalAmount);
            rollbackBalance(contractOrderDO, oldPositionAmount, newTotalAmount, contractMatchedOrderDO, lever);
        }
    }

}
