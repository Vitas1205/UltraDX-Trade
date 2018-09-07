package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.asset.domain.ContractDealer;
import com.fota.asset.service.ContractService;
import com.fota.trade.client.RollbackTask;
import com.fota.trade.common.UpdatePositionResult;
import com.fota.trade.domain.*;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
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

    @Resource
    private ContractMatchedOrderMapper matchedOrderMapper;


    @Transactional(rollbackFor = {Exception.class})
    public ResultCode rollBack(Date safePoint, long contractId) {
        int pageSize = 100;
        int pageIndex = 1;
        Date startTaskTime = new Date();

        BaseQuery query = new BaseQuery();
        query.setSourceId((int) contractId);
        query.setStartTime(safePoint);
        query.setEndTime(new Date());
        long count = matchedOrderMapper.count(query);
        if (0 >= count) {
            log.info("there is nothing to rollback");
            return ResultCode.success();
        }

        //分割任务
        int maxPageIndex = ((int) count - 1) / pageSize + 1;;
        List<RollbackTask> tasks = new ArrayList<>();
        while (pageIndex <= maxPageIndex) {
            RollbackTask rollbackTask = new RollbackTask();
            rollbackTask.setContractId(contractId);
            rollbackTask.setPageSize(pageSize);
            rollbackTask.setRollbackPoint(safePoint);
            rollbackTask.setTaskStartPoint(startTaskTime);
            rollbackTask.setPageSize(pageSize)
                    .setPageIndex(pageIndex);
            tasks.add(rollbackTask);
            pageIndex++;
        }
        tasks.forEach(task ->{
            rollbackMatchedOrder(task);
        });

        return ResultCode.success();

    }
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

        //更新委托状态
        long oppositeFilledAmount = matchedOrderDO.getFilledAmount().negate().longValue();
        BigDecimal filledPrice = matchedOrderDO.getFilledPrice();
        long filledAmount = matchedOrderDO.getFilledAmount().longValue();

        contractOrderManager.updateContractOrder(askContractOrder.getId(), oppositeFilledAmount, matchedOrderDO.getFilledPrice(), new Date());
        contractOrderManager.updateContractOrder(bidContractOrder.getId(), oppositeFilledAmount, matchedOrderDO.getFilledPrice(), new Date());

        int tmp = askContractOrder.getOrderDirection();
        askContractOrder.setOrderDirection(bidContractOrder.getOrderDirection());
        bidContractOrder.setOrderDirection(tmp);

        BigDecimal contractSize = contractOrderManager.getContractSize(askContractOrder.getContractId());
        //更新持仓
        UpdatePositionResult askResult = contractOrderManager.updatePosition(askContractOrder, contractSize, filledAmount, filledPrice);
        UpdatePositionResult bidResult = contractOrderManager.updatePosition(bidContractOrder, contractSize, filledAmount, filledPrice);

        contractMatchedOrderMapper.updateStatus(matchedOrderDO.getId(), DELETE);

        ContractDealer dealer1 = calRollbackBalance(askContractOrder, filledAmount, filledPrice, contractSize, askResult);
        ContractDealer dealer2 = calRollbackBalance(bidContractOrder, filledAmount, filledPrice, contractSize, bidResult);
        com.fota.common.Result result = contractService.updateBalances(dealer1, dealer2);
        if (!result.isSuccess()) {
            throw new RuntimeException("update balance failed");
        }


    }
    private ContractDealer calRollbackBalance(ContractOrderDO contractOrderDO, long filledAmount, BigDecimal filledPrice,
                                              BigDecimal contractSize, UpdatePositionResult positionResult){
        long userId = contractOrderDO.getUserId();
        BigDecimal rate = contractOrderDO.getFee();
        if (null == positionResult.getCloseAmount()) {
            return null;
        }
        //手续费
        BigDecimal actualFee = filledPrice.multiply(new BigDecimal(filledAmount)).multiply(rate).multiply(contractSize);
        // (filledPrice-openAveragePrice)*closeAmount*contractSize*openPositionDirection - actualFee
        BigDecimal addAmount = filledPrice.subtract(positionResult.getOpenAveragePrice())
                .multiply(new BigDecimal(positionResult.getCloseAmount()))
                .multiply(contractSize)
                .multiply(new BigDecimal(positionResult.getOpenPositionDirection()))
                .add(actualFee);
        ContractDealer dealer = new ContractDealer()
                .setUserId(userId)
                .setAddedTotalAmount(addAmount)
                .setTotalLockAmount(BigDecimal.ZERO);
        dealer.setDealType( ContractDealer.DealType.FORCE);
        return dealer;
    }


}
