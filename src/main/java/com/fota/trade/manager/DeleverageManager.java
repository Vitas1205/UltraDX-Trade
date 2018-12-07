package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.common.utils.LogUtil;
import com.fota.risk.client.domain.UserRRLDTO;
import com.fota.risk.client.manager.RelativeRiskLevelManager;
import com.fota.trade.common.*;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.dto.DeleverageDTO;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.msg.ContractDealedMessage;
import com.fota.trade.msg.DeleveragedMessage;
import com.fota.trade.msg.DeleveragedMessages;
import com.fota.trade.msg.TopicConstants;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ConvertUtils;
import lombok.experimental.var;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.fota.trade.common.ResultCodeEnum.BIZ_ERROR;
import static com.fota.trade.common.TradeBizTypeEnum.CONTRACT_ADL;
import static com.fota.trade.domain.enums.OrderCloseType.DECREASE_LEVERAGE;

@Component
public class DeleverageManager {

    @Autowired
    RelativeRiskLevelManager riskLevelManager;

    @Autowired
    RocketMqManager rocketMqManager;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Autowired
    private DealManager dealManager;

    private static final Logger ADL_EXTEA_LOG = LoggerFactory.getLogger("adlExtraInfo");
    private static final Logger ADL_FAILED_LOGGER = LoggerFactory.getLogger("adlFailed");
    private static final Logger tradeLog = LoggerFactory.getLogger("trade");



    public static final ExecutorService executorService = new ThreadPoolExecutor(4, 10, 3, TimeUnit.MINUTES, new LinkedBlockingDeque<>());


    @Transactional(rollbackFor = Throwable.class)
    public void deleverage(DeleverageDTO deleverageDTO){

        Long matchId = deleverageDTO.getMatchId();
        Long contractId = deleverageDTO.getContractId();
        BigDecimal adlPrice = deleverageDTO.getAdlPrice();

        BigDecimal unfilledAmount = deleverageDTO.getUnfilledAmount();
        int pageSize = 100;
        int needPositionDirection = deleverageDTO.getNeedPositionDirection();
        //获取当前价格

        //降杠杆和强平单成交记录
        List<ContractMatchedOrderDO> contractMatchedOrderDOS = new LinkedList<>();

        List<Runnable> updateBalanceTasks = new LinkedList<>();
        List<UpdatePositionResult> updatePositionResults = new LinkedList<>();

        List<DeleveragedMessage> deleveragedMessageList = new LinkedList<>();
        DeleveragedMessages deleveragedMessages = new DeleveragedMessages();
        deleveragedMessages.setDeleveragedMessageList(deleveragedMessageList);

        for (int start = 0; unfilledAmount.compareTo(BigDecimal.ZERO) > 0; start = start + pageSize + 1) {
            List<UserRRLDTO>  RRL = getRRLWithRetry(deleverageDTO.getContractId(),
                    needPositionDirection, start, start + pageSize);

            //调用排行榜失败，break
            if (CollectionUtils.isEmpty(RRL)) {
                LogUtil.error(CONTRACT_ADL, deleverageDTO.getMatchId()+"", null, "empty RRL, start="+start+" contractId="+contractId+" dir="+needPositionDirection);
                break;
            }
            List<Long> userIds = RRL.stream().map(UserRRLDTO::getUserId).collect(Collectors.toList());

            //批量查询持仓
            List<UserPositionDO> userPositionDOS = userPositionMapper.selectByContractIdAndUserIds(userIds, deleverageDTO.getContractId());
            if (CollectionUtils.isEmpty(userPositionDOS)) {
                LogUtil.error(CONTRACT_ADL, deleverageDTO.getMatchId()+"", null, "empty userPositionDOS, userIds="+userIds+", contractId="+contractId+", dir="+needPositionDirection);
                continue;
            }
            Map<Long, UserPositionDO> userPositionDOMap = userPositionDOS.stream().collect(Collectors.toMap(UserPositionDO::getUserId, x -> x));
            //按照RRL顺序减仓
            for (UserRRLDTO userRRLDTO : RRL) {
                UserPositionDO userPositionDO = userPositionDOMap.get(userRRLDTO.getUserId());
                //过滤掉不正确的持仓
                if (null == userPositionDO || userPositionDO.getUnfilledAmount().compareTo(BigDecimal.ZERO) == 0 || userPositionDO.getPositionType() != needPositionDirection) {
                    LogUtil.error(CONTRACT_ADL, deleverageDTO.getMatchId()+"", userPositionDO, "illegal userPositionDO:{}");
                    continue;
                }
                BigDecimal subAmount = userPositionDO.getUnfilledAmount().min(unfilledAmount);
                //注意direction与仓位方向相反,费率为0
                ContractDealedMessage contractDealedMessage = new ContractDealedMessage(userPositionDO.getContractId(), userPositionDO.getUserId(),
                        ConvertUtils.opDirection(deleverageDTO.getNeedPositionDirection()), BigDecimal.ZERO);
                contractDealedMessage.setOrderId(BasicUtils.generateId());
                contractDealedMessage.setMatchId(matchId);
                contractDealedMessage.setFilledAmount(subAmount);
                contractDealedMessage.setFilledPrice(adlPrice);
                contractDealedMessage.setSubjectName(userPositionDO.getContractName());

                List<ContractDealedMessage> curUserPostDealTasks =  Arrays.asList(contractDealedMessage);
                UpdatePositionResult positionResult = dealManager.updatePosition(contractDealedMessage.getUserId(),contractDealedMessage.getSubjectId(), curUserPostDealTasks);
                //更新失败，换下一个持仓
                if (null == positionResult) {
                    LogUtil.error(TradeBizTypeEnum.CONTRACT_ADL,matchId+"", contractDealedMessage, "update position failed when adl");
                    continue;
                }

                DeleveragedMessage deleveragedMessage = new DeleveragedMessage(userPositionDO.getUserId(), userPositionDO.getContractId(), userPositionDO.getContractName(),
                        userPositionDO.getPositionType(), subAmount, adlPrice);
                deleveragedMessageList.add(deleveragedMessage);
                Runnable task = () -> {
                    dealManager.processAfterPositionUpdated(positionResult, Arrays.asList(contractDealedMessage));
                    tradeLog.info("adl@{}@@@{}@@@{}@@@{}@@@{}", userPositionDO.getUserId(),
                            userPositionDO.getPositionType(), userPositionDO.getContractName(), subAmount, System.currentTimeMillis());

                };
                updateBalanceTasks.add(task);
                updatePositionResults.add(positionResult);
                contractMatchedOrderDOS.add(ConvertUtils.toMatchedOrderDO(contractDealedMessage,
                        adlPrice, DECREASE_LEVERAGE.getCode(), 0L, ConvertUtils.opDirection(needPositionDirection)
                ));

                unfilledAmount = unfilledAmount.subtract(subAmount);
                if (unfilledAmount.compareTo(BigDecimal.ZERO) == 0) {
                    break;
                }

            }

        }

        if (unfilledAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new ADLBizException( "unfilledAmount="+unfilledAmount, BizExceptionEnum.NO_ENOUGH_POSITION);
        }


        if (!CollectionUtils.isEmpty(contractMatchedOrderDOS)) {
            contractMatchedOrderMapper.insert(contractMatchedOrderDOS);
        }

        for (Runnable updateBalanceTask : updateBalanceTasks) {
            updateBalanceTask.run();
        }

        rocketMqManager.sendMessage(TopicConstants.TRD_CONTRACT_DELEVERAGED, "deleveraged", matchId+"", deleveragedMessages);

        Map<String, Object> map = new HashMap<>();
        map.put("matchId", matchId);
        map.put("adlPrice", adlPrice);
        map.put("direction", deleverageDTO.getNeedPositionDirection());
        map.put("contractId", deleverageDTO.getContractId());
        map.put("updatePositionResults", updatePositionResults);
        map.put("unfilled", deleverageDTO.getUnfilledAmount());
        map.put("adlPrice", adlPrice);
        ADL_EXTEA_LOG.info("{}", JSON.toJSONString(map));

    }

    public List<UserRRLDTO> getRRLWithRetry(long contractId, int direction, int start, int end) {
        return BasicUtils.retryWhenFail(() -> riskLevelManager.range(contractId,
                direction, start, end), ret -> CollectionUtils.isEmpty(ret), Duration.ofMillis(30), 5);

    }

    public boolean sendDeleverageMessage(DeleverageDTO deleverageDTO, String retryKey){
        String messageKey = StringUtils.isEmpty(retryKey)? deleverageDTO.key():retryKey;

        MessageQueueSelector queueSelector = (final List<MessageQueue> mqs, final Message msg, final Object arg) -> {
            int key = BasicUtils.absHash(arg);
            return mqs.get(key % mqs.size());
        };
        return rocketMqManager.sendMessage(TopicConstants.TRD_CONTRACT_DELEVERAGE, "adl", messageKey, deleverageDTO, queueSelector, deleverageDTO.queue());
    }
}
