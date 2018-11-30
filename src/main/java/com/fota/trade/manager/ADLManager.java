package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.common.utils.LogUtil;
import com.fota.risk.client.domain.UserRRLDTO;
import com.fota.risk.client.manager.RelativeRiskLevelManager;
import com.fota.trade.common.ADLBizException;
import com.fota.trade.common.BizExceptionEnum;
import com.fota.trade.common.TradeBizTypeEnum;
import com.fota.trade.common.UpdatePositionResult;
import com.fota.trade.domain.ContractADLMatchDTO;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.dto.ProcessNoEnforceResult;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.msg.ContractDealedMessage;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ContractUtils;
import com.fota.trade.util.ConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.common.BizExceptionEnum.UPDATE_POSITION;
import static com.fota.trade.common.TradeBizTypeEnum.CONTRACT_ADL;
import static com.fota.trade.domain.enums.OrderCloseType.DECREASE_LEVERAGE;
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
    RelativeRiskLevelManager riskLevelManager;

    @Autowired
    private DealManager dealManager;

    @Autowired
    private CurrentPriceService currentPriceService;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    private static final Logger ADL_EXTEA_LOG = LoggerFactory.getLogger("adlExtraInfo");

    private static final ExecutorService executorService = new ThreadPoolExecutor(4, 10, 3, TimeUnit.MINUTES, new LinkedBlockingDeque<>());
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


        BigDecimal unfilledAmount = adlMatchDTO.getUnfilled();
        int pageSize = 100;
        int needPositionDirection = adlMatchDTO.getDirection();
        //获取当前价格
        BigDecimal adlPrice = getAdlPrice( adlMatchDTO.getPrice(), currentPrice, needPositionDirection);

        //降杠杆和强平单成交记录
        List<ContractMatchedOrderDO> contractMatchedOrderDOS = new LinkedList<>();

        for (int start = 0; unfilledAmount.compareTo(BigDecimal.ZERO) > 0; start = start + pageSize + 1) {
            List<UserRRLDTO>  RRL = getRRLWithRetry(adlMatchDTO.getContractId(),
                    needPositionDirection, start, start + pageSize);

            //调用排行榜失败，break
            if (CollectionUtils.isEmpty(RRL)) {
                LogUtil.error(CONTRACT_ADL, adlMatchDTO.getId()+"", null, "start="+start+" contractId="+adlMatchDTO.getContractId()+" dir="+needPositionDirection);
                break;
            }
            List<Long> userIds = RRL.stream().map(UserRRLDTO::getUserId).collect(Collectors.toList());

            //批量查询持仓
            List<UserPositionDO> userPositionDOS = userPositionMapper.selectByContractIdAndUserIds(userIds, adlMatchDTO.getContractId());
            if (CollectionUtils.isEmpty(userPositionDOS)) {
                continue;
            }

            Map<Long, UserPositionDO> userPositionDOMap = userPositionDOS.stream().collect(Collectors.toMap(UserPositionDO::getUserId, x -> x));
            //按照RRL顺序减仓
            for (UserRRLDTO userRRLDTO : RRL) {
                UserPositionDO userPositionDO = userPositionDOMap.get(userRRLDTO.getUserId());
                //过滤掉不正确的持仓
                if (null == userPositionDO || userPositionDO.getUnfilledAmount().compareTo(BigDecimal.ZERO) == 0 || userPositionDO.getPositionType() != needPositionDirection) {
                    continue;
                }
                BigDecimal subAmount = userPositionDO.getUnfilledAmount().min(unfilledAmount);
                //注意direction与仓位方向相反,费率为0
                ContractDealedMessage postDealMessage = new ContractDealedMessage(userPositionDO.getContractId(), userPositionDO.getUserId(),
                        ConvertUtils.opDirection(adlMatchDTO.getDirection()), BigDecimal.ZERO);
                postDealMessage.setMatchId(adlMatchDTO.getId());
                postDealMessage.setFilledAmount(subAmount);
                postDealMessage.setFilledPrice(adlPrice);
                postDealMessage.setMsgKey(adlMatchDTO.getId()+"_"+BasicUtils.generateId());
                postDealMessage.setSubjectName(adlMatchDTO.getContractName());
                allPostDealTasks.add(postDealMessage);
                contractMatchedOrderDOS.add(ConvertUtils.toMatchedOrderDO(postDealMessage,
                        adlPrice, DECREASE_LEVERAGE.getCode(), adlMatchDTO.getUserId(), ConvertUtils.opDirection(adlMatchDTO.getDirection())
                ));
                unfilledAmount = unfilledAmount.subtract(subAmount);
                if (unfilledAmount.compareTo(BigDecimal.ZERO) == 0) {
                    break;
                }

            }

        }

        //如果数量不足，修改委托状态
        if (unfilledAmount.compareTo(BigDecimal.ZERO)>0) {
            contractOrderMapper.updateAAS(adlMatchDTO.getUserId(), adlMatchDTO.getOrderId(), unfilledAmount, calStatus(adlMatchDTO.getAmount(), unfilledAmount));
        }

        ProcessNoEnforceResult processNoEnforceResult = dealManager.processNoEnforceMatchedOrders(adlMatchDTO);
        //更新持仓,账户余额
        if (!CollectionUtils.isEmpty(processNoEnforceResult.getContractDealedMessages())) {
            //处理已经撮合的非强平单
            allPostDealTasks.addAll(processNoEnforceResult.getContractDealedMessages());
        }
        if (!CollectionUtils.isEmpty(processNoEnforceResult.getContractMatchedOrderDOS())) {
            contractMatchedOrderDOS.addAll(processNoEnforceResult.getContractMatchedOrderDOS());
        }

        BigDecimal platformProfit = calPlatformProfit(adlMatchDTO, adlPrice);
        ContractMatchedOrderDO forceddMatchRecord = getMatchRecordForEnforceOrder(adlMatchDTO, platformProfit);
        contractMatchedOrderDOS.add(forceddMatchRecord);

        if (!CollectionUtils.isEmpty(contractMatchedOrderDOS)) {
            //写成交记录
            contractMatchedOrderMapper.insert(contractMatchedOrderDOS);
        }


        allPostDealTasks.add(getContractDealedMessage4EnforceOrder(adlMatchDTO));
        List<UpdatePositionResult> updatePositionResults = new LinkedList<>();
        for (ContractDealedMessage postDealMessage : allPostDealTasks) {
            List<ContractDealedMessage> curUserPostDealTasks =  Arrays.asList(postDealMessage);
            UpdatePositionResult positionResult = dealManager.updatePosition(postDealMessage.getUserId(),postDealMessage.getSubjectId(), curUserPostDealTasks);
            if (null == positionResult) {
                throw new ADLBizException(UPDATE_POSITION);
            }
            updatePositionResults.add(positionResult);
            Runnable task = () -> dealManager.processAfterPositionUpdated(positionResult, curUserPostDealTasks);
            executorService.submit(task);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("platformProfit", platformProfit);
        map.put("adlMatchDTO", adlMatchDTO);
        map.put("currentPrice", currentPrice);
        map.put("updatePositionResults", updatePositionResults);
        map.put("adlPrice", adlPrice);
        ADL_EXTEA_LOG.info("{}", JSON.toJSONString(map));

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

    /**
     * 划分需要哪些用户足够分摊 unfilledAmount 数量的持仓。因为amount不准确，而且有可能会变化，所以会有一个buffer=end-start,
     * 即 找到一个end使得  end - start + sum(RRL[i].amount）>= unfilledAmount, (start<=i<end)
     *
     * @param
     * @param start 开始
     * @return end
     */
//    public int split(List<UserRRLDTO> RRL, int start, BigDecimal unfilledAmount, List<Long> userIds) {
//        userIds.clear();
//        BigDecimal amount = BigDecimal.ZERO;
//        Iterator<UserRRLDTO> iterator = RRL.listIterator(start);
//        UserRRLDTO userRRLDTO = null;
//        int end = start;
//        for (; iterator.hasNext(); userRRLDTO = iterator.next()) {
//            amount = amount.add(userRRLDTO.getAmount());
//            userIds.add(userRRLDTO.getUserId());
//            end++;
//            if (amount.add(new BigDecimal(end - start)).compareTo(unfilledAmount) >= 0) {
//                return end;
//            }
//        }
//        return end;
//    }

    public List<UserRRLDTO> getRRLWithRetry(long contractId, int direction, int start, int end) {
        return BasicUtils.retryWhenFail(() -> riskLevelManager.range(contractId,
                direction, start, end), ret -> !CollectionUtils.isEmpty(ret), Duration.ofMillis(30), 5);

    }

//    public List<UserRRLDTO> getRRL(long contractId, long direction, long start, long end){
//
//    }

    public ContractDealedMessage getContractDealedMessage4EnforceOrder(ContractADLMatchDTO adlMatchDTO) {
        ContractDealedMessage postDealMessage = new ContractDealedMessage(adlMatchDTO.getContractId(), adlMatchDTO.getUserId(),
                adlMatchDTO.getDirection(), BigDecimal.ZERO);
        postDealMessage.setMatchId(adlMatchDTO.getId());
        postDealMessage.setFilledAmount(adlMatchDTO.getAmount());
        postDealMessage.setFilledPrice(adlMatchDTO.getPrice());
        postDealMessage.setMsgKey(adlMatchDTO.getId()+"_"+adlMatchDTO.getOrderId());
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
