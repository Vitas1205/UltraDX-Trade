package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.common.Result;
import com.fota.risk.client.domain.UserRRLDTO;
import com.fota.risk.client.manager.RelativeRiskLevelManager;
import com.fota.trade.common.BizException;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.ContractADLMatchDTO;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.enums.OrderCloseType;
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
import java.util.*;
import java.util.stream.Collectors;

import static com.fota.common.ResultCodeEnum.ILLEGAL_PARAM;
import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.common.ResultCodeEnum.BIZ_ERROR;
import static com.fota.trade.domain.enums.OrderCloseType.DECREASE_LEVERAGE;
import static com.fota.trade.domain.enums.OrderCloseType.ENFORCE;

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
    private CurrentPriceManager currentPriceManager;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    private static final Logger ADL_EXTEA_LOG = LoggerFactory.getLogger("adlExtraInfo");

//    private static final ExecutorService executorService = new ThreadPoolExecutor(1, 10, 10, TimeUnit.MINUTES, new LinkedBlockingDeque<>());
    /**
     * 自动减仓
     *
     * @param
     */
    @Transactional(rollbackFor = Throwable.class)
    public Result adl(ContractADLMatchDTO adlMatchDTO) {
        if (!checkADLParam(adlMatchDTO)) {
            return Result.fail(ILLEGAL_PARAM.getCode(), ILLEGAL_PARAM.getMessage());
        }
        //更新委托，同时有去重加锁的作用
        int aff = contractOrderMapper.updateAmountAndStatus(adlMatchDTO.getUserId(), adlMatchDTO.getOrderId(), adlMatchDTO.getAmount(), adlMatchDTO.getPrice(), new Date());
        if (1 != aff) {
            return Result.fail(ILLEGAL_PARAM.getCode(), "duplicate message or concurrency problem");
        }
        List<ContractDealedMessage> postDealMessages = new LinkedList<>();


        List<ContractDealedMessage> matchedPostDeal = dealManager.processNoEnforceMatchedOrders(adlMatchDTO);

        BigDecimal unfilledAmount = adlMatchDTO.getUnfilled();
        int pageSize = 100;
        int needPositionDirection = adlMatchDTO.getDirection();
        //获取当前价格
        BigDecimal currentPrice = currentPriceManager.getSpotIndexByContractName(adlMatchDTO.getContractName());
        //降杠杆和强平单成交记录
        List<ContractMatchedOrderDO> contractMatchedOrderDOS = new LinkedList<>();

        for (int start = 0; unfilledAmount.compareTo(BigDecimal.ZERO) > 0; start = start + pageSize + 1) {
            Result<List<UserRRLDTO>> riskResult = getRRLWithRetry(adlMatchDTO.getContractId(),
                    needPositionDirection, start, start + pageSize);

            //调用排行榜失败，回滚事务
            if (null == riskResult || !riskResult.isSuccess() || CollectionUtils.isEmpty(riskResult.getData())) {
                throw new BizException(ResultCodeEnum.BIZ_ERROR.getCode(), "failed riskResult:" + riskResult);
            }

            List<UserRRLDTO> RRL = riskResult.getData();
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
                postDealMessage.setFilledPrice(currentPrice);
                postDealMessage.setMsgKey(adlMatchDTO.getId()+"_"+BasicUtils.generateId());
                postDealMessage.setSubjectName(adlMatchDTO.getContractName());
                postDealMessages.add(postDealMessage);
                contractMatchedOrderDOS.add(ConvertUtils.toMatchedOrderDO(postDealMessage,
                        currentPrice, DECREASE_LEVERAGE.getCode(), adlMatchDTO.getUserId(), ConvertUtils.opDirection(adlMatchDTO.getDirection())
                ));
                unfilledAmount = unfilledAmount.subtract(subAmount);
                if (unfilledAmount.compareTo(BigDecimal.ZERO) == 0) {
                    break;
                }

            }

        }

        if (unfilledAmount.compareTo(BigDecimal.ZERO)>0) {
            throw new BizException(BIZ_ERROR.getCode(), "unfilledAmount still bigger than 0.userId="+ adlMatchDTO.getUserId() +", orderId=" + adlMatchDTO.getOrderId()+
            ", unfilled=" + unfilledAmount);
        }

        BigDecimal platformProfit = calPlatformProfit(adlMatchDTO, currentPrice);
        ContractMatchedOrderDO forceddMatchRecord = getMatchRecordForEnforceOrder(adlMatchDTO, platformProfit);
        contractMatchedOrderDOS.add(forceddMatchRecord);
        if (!CollectionUtils.isEmpty(contractMatchedOrderDOS)) {
            //写成交记录
            aff = contractMatchedOrderMapper.insert(contractMatchedOrderDOS);
            if (aff < contractMatchedOrderDOS.size()) {
                throw new BizException(BIZ_ERROR.getCode(), "insert matched record failed");
            }
        }


        //更新持仓,账户余额
        if (!CollectionUtils.isEmpty(matchedPostDeal)) {
            //处理已经撮合的非强平单
            postDealMessages.addAll(matchedPostDeal);
        }
        postDealMessages.add(getContractDealedMessage4EnforceOrder(adlMatchDTO));
        for (ContractDealedMessage postDealMessage : postDealMessages) {
            Result result = dealManager.postDeal(Arrays.asList(postDealMessage), true);
            if (!result.isSuccess()) {
                throw new BizException(BIZ_ERROR.getCode(), "update position failed");
            }
        }
        Map<String, Object> map = new HashMap<>();
        map.put("platformProfit", platformProfit);
        map.put("adlMatchDTO", adlMatchDTO);
        map.put("adlUserIds", contractMatchedOrderDOS.stream().map(ContractMatchedOrderDO::getUserId).collect(Collectors.toList()));
        ADL_EXTEA_LOG.info("{}", JSON.toJSONString(map));
        return Result.suc(null);

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

    public Result<List<UserRRLDTO>> getRRLWithRetry(long contractId, int direction, int start, int end) {
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            try {
                //获取排行榜，以后可以看排行榜变化频率考虑是否缓存
                List<UserRRLDTO> ret = riskLevelManager.range(contractId,
                        direction, start, end);
                if (CollectionUtils.isEmpty(ret) && (i+1) < maxRetries) {
                    BasicUtils.exeWhitoutError(() -> Thread.sleep(30));
                    continue;
                }
                return Result.suc(ret);
            } catch (Throwable t) {
                //调用抛异常三次，回滚事务
                if ((maxRetries - 1) >= i) {
                    throw new BizException(ResultCodeEnum.BIZ_ERROR.getCode(), "riskLevelService.range result exception." + t.getMessage());
                }
                BasicUtils.exeWhitoutError(() -> Thread.sleep(30));
            }
        }
        throw new BizException(ResultCodeEnum.BIZ_ERROR.getCode(), "riskLevelService.range result exception.");

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
