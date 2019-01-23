package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.common.utils.LogUtil;
import com.fota.trade.common.ADLBizException;
import com.fota.trade.common.BizExceptionEnum;
import com.fota.trade.common.TradeBizTypeEnum;
import com.fota.trade.common.UpdatePositionResult;
import com.fota.trade.domain.ContractADLMatchDTO;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.dto.ProcessNoEnforceResult;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.dto.DeleverageDTO;
import com.fota.trade.mapper.sharding.ContractMatchedOrderMapper;
import com.fota.trade.mapper.sharding.ContractOrderMapper;
import com.fota.trade.mapper.trade.UserPositionMapper;
import com.fota.trade.msg.ContractDealedMessage;
import com.fota.trade.util.ContractUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;

import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.common.BizExceptionEnum.UPDATE_POSITION;
import static com.fota.trade.common.TradeBizTypeEnum.CONTRACT_ADL;
import static com.fota.trade.domain.enums.OrderCloseType.ENFORCE;
import static com.fota.trade.domain.enums.OrderStatusEnum.PART_CANCEL;
import static com.fota.trade.domain.enums.PositionTypeEnum.OVER;

/**
 * Created by lds on 2018/10/24.
 * Code is the law
 */
@Component
@Slf4j
public class ADLManager {
    @Autowired
    private ContractOrderMapper contractOrderMapper;
    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private DealManager dealManager;

    @Autowired
    private CurrentPriceService currentPriceService;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Autowired
    private RocketMqManager rocketMqManager;

    @Autowired
    private DeleverageManager deleverageManager;

    private static final Logger ADL_EXTEA_LOG = LoggerFactory.getLogger("adlExtraInfo");

    /**
     * 自动减仓
     *
     * @param
     */
    @Transactional(rollbackFor = Throwable.class)
    public void adl(ContractADLMatchDTO adlMatchDTO) {
        BigDecimal currentPrice = currentPriceService.getSpotIndexByContractName(adlMatchDTO.getContractName());
        if (null == currentPrice) {
            LogUtil.error(CONTRACT_ADL, adlMatchDTO.getId()+"", adlMatchDTO.getContractName(), "no spotIndex, contractName="+adlMatchDTO.getContractName());
            throw new ADLBizException(BizExceptionEnum.NO_CURRENT_PRICE);
        }
        Integer needPositionDirection = adlMatchDTO.getDirection();
        //计算减仓价格
        BigDecimal adlPrice = getAdlPrice( adlMatchDTO.getPrice(), currentPrice, needPositionDirection);

        if (!checkADLParam(adlMatchDTO)) {
            LogUtil.error(TradeBizTypeEnum.CONTRACT_ADL, null, adlMatchDTO, "illegal param");
            return;
        }
        int aff;
        //更新委托，同时有去重加锁的作用
        aff = contractOrderMapper.updateAmountAndStatus(adlMatchDTO.getUserId(), adlMatchDTO.getOrderId(), adlMatchDTO.getAmount(), adlMatchDTO.getPrice(), new Date());
        if (1 != aff) {
            log.warn("duplicate adl message or no such order{}", adlMatchDTO);
            return;
        }
        List<ContractDealedMessage> allPostDealTasks = new LinkedList<>();
        allPostDealTasks.add(getContractDealedMessage4EnforceOrder(adlMatchDTO));

        List<ContractMatchedOrderDO> contractMatchedOrderDOS = new LinkedList<>();
        BigDecimal platformProfit = calPlatformProfit(adlMatchDTO, adlPrice);
        ContractMatchedOrderDO forceddMatchRecord = getMatchRecordForEnforceOrder(adlMatchDTO, platformProfit);
        contractMatchedOrderDOS.add(forceddMatchRecord);

        //处理成交
        ProcessNoEnforceResult processNoEnforceResult = dealManager.processNoEnforceMatchedOrders(adlMatchDTO);

        if (!CollectionUtils.isEmpty(processNoEnforceResult.getContractMatchedOrderDOS())) {
            contractMatchedOrderDOS.addAll(processNoEnforceResult.getContractMatchedOrderDOS());
        }

        if (!CollectionUtils.isEmpty(contractMatchedOrderDOS)) {
            //写成交记录
            contractMatchedOrderMapper.insert(contractMatchedOrderDOS);
        }


        List<UpdatePositionResult> updatePositionResults = new LinkedList<>();
        for (ContractDealedMessage postDealMessage : allPostDealTasks) {
            List<ContractDealedMessage> curUserPostDealTasks =  Arrays.asList(postDealMessage);
            UpdatePositionResult positionResult = dealManager.updatePositionWithRetry(postDealMessage.getUserId(),postDealMessage.getSubjectId(), curUserPostDealTasks);
            if (null == positionResult) {
                throw new ADLBizException(UPDATE_POSITION);
            }
            updatePositionResults.add(positionResult);
            Runnable task = () -> dealManager.processAfterPositionUpdated(positionResult, curUserPostDealTasks);
            DeleverageManager.executorService.submit(task);
        }



        Map<String, Object> map = new HashMap<>();
        map.put("platformProfit", platformProfit);
        map.put("matchId", adlMatchDTO.getId());
        map.put("userId", adlMatchDTO.getUserId());
        map.put("matchedList", adlMatchDTO.getMatchedList());
        map.put("enforcePrice", adlMatchDTO.getPrice());
        map.put("unfilled", adlMatchDTO.getUnfilled());
        map.put("direction", adlMatchDTO.getDirection());
        map.put("contractId", adlMatchDTO.getContractId());
        map.put("currentPrice", currentPrice);
        map.put("updatePositionResults", updatePositionResults);
        map.put("adlPrice", adlPrice);
        ADL_EXTEA_LOG.info("{}", JSON.toJSONString(map));

        if (adlMatchDTO.getUnfilled().compareTo(BigDecimal.ZERO) > 0) {
            DeleverageDTO deleverageDTO = new DeleverageDTO();
            deleverageDTO.setUnfilledAmount(adlMatchDTO.getUnfilled());
            deleverageDTO.setAdlPrice(adlPrice);
            deleverageDTO.setContractId(adlMatchDTO.getContractId());
            deleverageDTO.setMatchId(adlMatchDTO.getId());
            deleverageDTO.setNeedPositionDirection(needPositionDirection);
            deleverageManager.sendDeleverageMessage(deleverageDTO, null);
        }

    }

    int calStatus(BigDecimal total, BigDecimal unfilled){
        return total.compareTo(unfilled) > 0 ? PART_CANCEL.getCode() : OrderStatusEnum.CANCEL.getCode();
    }

    /**
     *
     * @param targetPrice
     * @param adlPositionType 被减仓仓位
     * @return
     */
    private BigDecimal getAdlPrice( BigDecimal targetPrice, BigDecimal currentPrice, Integer adlPositionType) {
        if (adlPositionType == OVER.getCode()) {
            return targetPrice.min(currentPrice);
        }
        return targetPrice.max(currentPrice);
    }


    public ContractDealedMessage getContractDealedMessage4EnforceOrder(ContractADLMatchDTO adlMatchDTO) {
        ContractDealedMessage postDealMessage = new ContractDealedMessage(adlMatchDTO.getContractId(), adlMatchDTO.getUserId(),
                adlMatchDTO.getDirection(), BigDecimal.ZERO);
        postDealMessage.setOrderId(adlMatchDTO.getOrderId());
        postDealMessage.setMatchId(adlMatchDTO.getId());
        postDealMessage.setFilledAmount(adlMatchDTO.getAmount());
        postDealMessage.setSubjectName(adlMatchDTO.getContractName());
        postDealMessage.setFilledPrice(adlMatchDTO.getPrice());
        return postDealMessage;
    }

    public ContractMatchedOrderDO getMatchRecordForEnforceOrder(ContractADLMatchDTO contractADLMatchDTO, BigDecimal platformProfit){
        ContractMatchedOrderDO res = new ContractMatchedOrderDO();
        res.setGmtCreate(contractADLMatchDTO.getTime())
                .setMatchId(contractADLMatchDTO.getId())
                .setMatchType(contractADLMatchDTO.getDirection())
                .setOrderDirection(contractADLMatchDTO.getDirection())
                .setStatus(VALID)
                .setFilledAmount(contractADLMatchDTO.getAmount())
                .setFilledPrice(contractADLMatchDTO.getPrice())
                .setContractId(contractADLMatchDTO.getContractId())
                .setContractName(contractADLMatchDTO.getContractName());

        res.setFee(BigDecimal.ZERO);
        res.setCloseType(ENFORCE.getCode());
        res.setUserId(contractADLMatchDTO.getUserId());
        res.setOrderId(contractADLMatchDTO.getOrderId());
        res.setOrderPrice(contractADLMatchDTO.getPrice());
        res.setMatchUserId(0L);
        res.setPlatformProfit(platformProfit);
        return res;
    }

    public BigDecimal calPlatformProfit(ContractADLMatchDTO contractADLMatchDTO, BigDecimal currentPrice) {
        BigDecimal temp = contractADLMatchDTO.getMatchedList().stream().map(x -> x.getMatchedAmount().multiply(x.getFilledPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = contractADLMatchDTO.getAmount(), targetPrice = contractADLMatchDTO.getPrice(), unfilledAmount = contractADLMatchDTO.getUnfilled();
        BigDecimal opTotal = unfilledAmount.multiply(currentPrice).add(temp);
        return amount.multiply(targetPrice)
        .subtract(opTotal)
        .multiply(ContractUtils.toDir(contractADLMatchDTO.getDirection()));
    }

    public boolean checkADLParam(ContractADLMatchDTO adlMatchDTO) {
        if (null == adlMatchDTO || null == adlMatchDTO.getId() || null == adlMatchDTO.getOrderId() || null == adlMatchDTO.getUserId() ||
                null == adlMatchDTO.getAmount() || null == adlMatchDTO.getDirection()) {
            return false;
        }
        if (adlMatchDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return true;
    }
}
