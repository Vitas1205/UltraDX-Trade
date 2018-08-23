package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.asset.domain.ContractDealer;
import com.fota.asset.service.ContractService;
import com.fota.trade.client.RollbackTask;
import com.fota.trade.common.UpdatePositionResult;
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
import java.util.Date;
import java.util.List;

import static com.fota.trade.client.constants.MatchedOrderStatus.DELETE;
import static com.fota.trade.domain.enums.OrderTypeEnum.ENFORCE;

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

        Integer askLever = contractLeverManager.getLeverByContractId(askContractOrder.getUserId(), askContractOrder.getContractId());
        askContractOrder.setLever(askLever.intValue());

        Integer bidLever = contractLeverManager.getLeverByContractId(bidContractOrder.getUserId(), bidContractOrder.getContractId());
        bidContractOrder.setLever(bidLever);

        //更新委托状态
        long oppositeFilledAmount = matchedOrderDO.getFilledAmount().negate().longValue();
        BigDecimal filledPrice = matchedOrderDO.getFilledPrice();
        long filledAmount = matchedOrderDO.getFilledAmount().longValue();

        contractOrderManager.updateContractOrder(askContractOrder.getId(), oppositeFilledAmount, matchedOrderDO.getFilledPrice(), new Date());
        contractOrderManager.updateContractOrder(bidContractOrder.getId(), oppositeFilledAmount, matchedOrderDO.getFilledPrice(), new Date());

        contractMatchedOrderMapper.updateStatus(matchedOrderDO.getId(), DELETE);
        BigDecimal contractSize = contractOrderManager.getContractSize(askContractOrder.getContractId());
        //更新持仓
        UpdatePositionResult askResult = contractOrderManager.updatePosition(askContractOrder, contractSize, filledAmount, filledPrice);
        UpdatePositionResult bidResult = contractOrderManager.updatePosition(bidContractOrder, contractSize, filledAmount, filledPrice);


        ContractDealer dealer1 = calRollbackBalance(askContractOrder, filledAmount, filledPrice, contractSize, askResult, new BigDecimal(askLever));
        ContractDealer dealer2 = calRollbackBalance(bidContractOrder, filledAmount, filledPrice, contractSize, bidResult, new BigDecimal(bidLever));
        com.fota.common.Result result = contractService.updateBalances(dealer1, dealer2);
        if (!result.isSuccess()) {
            throw new RuntimeException("update balance failed");
        }


    }
    private ContractDealer calRollbackBalance(ContractOrderDO contractOrderDO, long filledAmount, BigDecimal filledPrice,
                                              BigDecimal contractSize, UpdatePositionResult positionResult, BigDecimal lever){
        long userId = contractOrderDO.getUserId();
        BigDecimal rate = contractOrderDO.getFee();

        BigDecimal actualFee = filledPrice.multiply(new BigDecimal(filledAmount)).multiply(rate).multiply(contractSize);
        BigDecimal addedTotalAmount = new BigDecimal(positionResult.getCurAmount() - positionResult.getOriginAmount())
                .multiply(filledPrice)
                .multiply(contractSize)
                .divide(lever, 8, BigDecimal.ROUND_DOWN)
                .subtract(actualFee)
                .negate();

        ContractDealer dealer = new ContractDealer()
                .setUserId(userId)
                .setAddedTotalAmount(addedTotalAmount)
                .setAddedLockAmount(BigDecimal.ZERO);
        dealer.setDealType((null != contractOrderDO.getOrderType() && ENFORCE.getCode() == contractOrderDO.getOrderType()) ? ContractDealer.DealType.FORCE : ContractDealer.DealType.NORMAL);
        return dealer;
    }


}
