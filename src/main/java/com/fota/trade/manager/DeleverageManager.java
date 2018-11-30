package com.fota.trade.manager;

import com.fota.common.utils.LogUtil;
import com.fota.risk.client.domain.UserRRLDTO;
import com.fota.risk.client.manager.RelativeRiskLevelManager;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.dto.DeleverageDTO;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.msg.ContractDealedMessage;
import com.fota.trade.msg.TopicConstants;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ConvertUtils;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        List<ContractDealedMessage> contractDealedMessages = new LinkedList<>();

        for (int start = 0; unfilledAmount.compareTo(BigDecimal.ZERO) > 0; start = start + pageSize + 1) {
            List<UserRRLDTO>  RRL = getRRLWithRetry(deleverageDTO.getContractId(),
                    needPositionDirection, start, start + pageSize);

            //调用排行榜失败，break
            if (CollectionUtils.isEmpty(RRL)) {
                LogUtil.error(CONTRACT_ADL, deleverageDTO.getMatchId()+"", null, "start="+start+" contractId="+contractId+" dir="+needPositionDirection);
                break;
            }
            List<Long> userIds = RRL.stream().map(UserRRLDTO::getUserId).collect(Collectors.toList());

            //批量查询持仓
            List<UserPositionDO> userPositionDOS = userPositionMapper.selectByContractIdAndUserIds(userIds, deleverageDTO.getContractId());
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
                ContractDealedMessage contractDealedMessage = new ContractDealedMessage(userPositionDO.getContractId(), userPositionDO.getUserId(),
                        ConvertUtils.opDirection(deleverageDTO.getNeedPositionDirection()), BigDecimal.ZERO);
                contractDealedMessage.setOrderId(BasicUtils.generateId());
                contractDealedMessage.setMatchId(matchId);
                contractDealedMessage.setFilledAmount(subAmount);
                contractDealedMessage.setFilledPrice(adlPrice);
                contractDealedMessage.setSubjectName(userPositionDO.getContractName());
                contractDealedMessages.add(contractDealedMessage);
                contractMatchedOrderDOS.add(ConvertUtils.toMatchedOrderDO(contractDealedMessage,
                        adlPrice, DECREASE_LEVERAGE.getCode(), 0L, ConvertUtils.opDirection(needPositionDirection)
                ));
                unfilledAmount = unfilledAmount.subtract(subAmount);
                if (unfilledAmount.compareTo(BigDecimal.ZERO) == 0) {
                    break;
                }

            }

        }


        if (!CollectionUtils.isEmpty(contractMatchedOrderDOS)) {
            contractMatchedOrderMapper.insert(contractMatchedOrderDOS);
            contractDealedMessages.forEach(x -> dealManager.sendDealMessage(x));
        }

        for (ContractDealedMessage contractDealedMessage : contractDealedMessages) {
            dealManager.sendDealMessage(contractDealedMessage);
        }

        deleverageDTO.setUnfilledAmount(unfilledAmount);
        if (unfilledAmount.compareTo(BigDecimal.ZERO) > 0) {
            sendDeleverageMessage(deleverageDTO);
        }



    }

    public List<UserRRLDTO> getRRLWithRetry(long contractId, int direction, int start, int end) {
        return BasicUtils.retryWhenFail(() -> riskLevelManager.range(contractId,
                direction, start, end), ret -> !CollectionUtils.isEmpty(ret), Duration.ofMillis(30), 5);

    }

    public boolean sendDeleverageMessage(DeleverageDTO deleverageDTO){
        MessageQueueSelector queueSelector = (final List<MessageQueue> mqs, final Message msg, final Object arg) -> {
            int key = arg.hashCode();
            return mqs.get(key % mqs.size());
        };
        return rocketMqManager.sendMessage(TopicConstants.TRD_CONTRACT_DELEVERAGE, "adl", deleverageDTO.key(), deleverageDTO, queueSelector, deleverageDTO.queue());
    }
}
