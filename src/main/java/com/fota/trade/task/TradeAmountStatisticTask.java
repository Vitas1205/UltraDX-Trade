package com.fota.trade.task;

import com.fota.account.domain.UserVipDTO;
import com.fota.account.service.UserVipService;
import com.fota.asset.domain.UserCapitalDTO;
import com.fota.asset.service.AssetService;
import com.fota.common.domain.config.Asset;
import com.fota.common.domain.config.BrokerTradingPairConfig;
import com.fota.common.domain.enums.AssetTypeEnum;
import com.fota.common.manager.BrokerTradingPairManager;
import com.fota.common.manager.FotaAssetManager;
import com.fota.trade.common.MatchedOrderConstants;
import com.fota.trade.domain.UsdkMatchedOrderDO;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.mapper.sharding.UsdkMatchedOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class TradeAmountStatisticTask {
    private static final Logger taskLog = LoggerFactory.getLogger("tradeAmountStatisticTask");

    @Autowired
    private AssetService assetService;

    @Resource
    private UsdkMatchedOrderMapper usdkMatchedOrderMapper;

    @Autowired
    private UserVipService userVipService;

    @Autowired
    private FotaAssetManager fotaAssetManager;

    @Resource
    private RedisManager redisManager;

    @Autowired
    private BrokerTradingPairManager brokerTradingPairManager;

    @Value("${jobStart:false}")
    private String value;

    private static HashMap<String, BigDecimal> rateMap;
    private ExecutorService threadPool;

    //做市账号
    private static final List<Integer> MARKET_USER_ID = Arrays.asList(546746,546747,546748,546753,546754,546755,546756,546757,546758,546759,546760,
            546761,546762,546763,546764,546765,546766,546767,546768,546780,546781,546782,546783,546784,546785,546786,546787,546788,546789,
            546790,546791,546792,546793,546794,546795,546796,546797,546798,546799,546800,546801,546802,546803,546804,546805);

    @PostConstruct
    public void initRateMap(){
        Long brokerId = 508090L;
        rateMap = getExchangeRate(brokerId);
        threadPool = new ThreadPoolExecutor(8, 16, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

        taskLog.info("init rateMap:{}",rateMap);
    }

    /**
     * 每天0时统计30天内交易总量和平台币锁仓量
     */
//    @Scheduled(cron = "0 0 0 * * ?")
    @Scheduled(cron = "0 0/10 * * * ?")
    public void tradeAmountStatistic() {
        if(value.equals("false")){
            return;
        }
        taskLog.info("tradeAmountStatistic task start!");

        List<Asset> assets = fotaAssetManager.getAllAssets();
        List<UserCapitalDTO> userCapitalDTOList = new ArrayList<>();
        for(Asset asset : assets){
            List<UserCapitalDTO> subUserCapitalDTOList = assetService.getUserCapital(Integer.valueOf(asset.getId()));
            if(!CollectionUtils.isEmpty(subUserCapitalDTOList)){
                userCapitalDTOList.addAll(subUserCapitalDTOList);
            }
        }
        LinkedBlockingQueue<Long> userIdBlockQueue = userCapitalDTOList.stream().map(UserCapitalDTO::getUserId)
                .filter(e -> !MARKET_USER_ID.contains(e.intValue())).distinct().collect(Collectors.toCollection(LinkedBlockingQueue::new));
        taskLog.info("handle assets:{},userIdBlockQueue:{},size:{}",assets,userIdBlockQueue,userIdBlockQueue.size());

        //多线程执行
        CountDownLatch countDownLatch = new CountDownLatch(userIdBlockQueue.size());
        Long endTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        Long startTime = LocalDateTime.now().minusDays(30).toEpochSecond(ZoneOffset.UTC);
        List<UserVipDTO> insertUserVipDTOs = new ArrayList<>();
        List<UserVipDTO> updateUserVipDTOs = new ArrayList<>();

        while(!userIdBlockQueue.isEmpty()){
            taskLog.info("current userIdBlockQueue:{},size:{}",userIdBlockQueue,userIdBlockQueue.size());
            Long userId = userIdBlockQueue.poll();
            try {
                threadPool.execute(()->{
                    try {
                        BigDecimal canUsedAmount = BigDecimal.ZERO;
                        BigDecimal tradeAmount30days = BigDecimal.ZERO;

                        UserCapitalDTO userCapitalDTO = assetService.getUserCapital(userId,AssetTypeEnum.TWD.getCode());
                        if(userCapitalDTO != null) {
                            canUsedAmount = new BigDecimal(userCapitalDTO.getAmount())
                                    .subtract(new BigDecimal(userCapitalDTO.getLockedAmount()))
                                    .setScale(4, RoundingMode.HALF_UP);
                        }

                        List<UsdkMatchedOrderDO> usdkMatchedOrderDOList = usdkMatchedOrderMapper.listByUserId(userId, null, 0, Integer.MAX_VALUE, startTime, endTime);
                        if(!CollectionUtils.isEmpty(usdkMatchedOrderDOList)) {
                            tradeAmount30days = usdkMatchedOrderDOList.parallelStream()
                                    .filter(x-> !"UNKNOW".equals(x.getAssetName()))
//                            .map(x -> x.getFilledAmount().multiply(x.getFilledPrice()).multiply(getRateByAssetName(x.getOrderDirection(),x.getAssetName())))
                                    .map(TradeAmountStatisticTask::getExchangePrice)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .setScale(4, RoundingMode.HALF_UP);
                        }

                        UserVipDTO userVipDTO = userVipService.getByUserId(userId);
                        if (userVipDTO != null) {
                            userVipDTO.setTradeAmount30days(tradeAmount30days.toPlainString());
                            userVipDTO.setLockedAmount(canUsedAmount.toPlainString());
                            updateUserVipDTOs.add(userVipDTO);
                        } else {
                            userVipDTO = new UserVipDTO();
                            userVipDTO.setUserId(userId);
                            userVipDTO.setTradeAmount30days(tradeAmount30days.toPlainString());
                            userVipDTO.setLockedAmount(canUsedAmount.toPlainString());
                            insertUserVipDTOs.add(userVipDTO);
                        }
                    }catch (Exception e){
                        taskLog.error("task error, userId{}",userId,e);
                    }finally {
                        countDownLatch.countDown();
                    }
                });
            }catch (Exception e){
                userIdBlockQueue.add(userId);
                taskLog.error("threadPool execute error! current userIdBlockQueue:{},size:{},e:{}",userIdBlockQueue,userIdBlockQueue.size(),e.getMessage());
            }
        }

        try {
            taskLog.info("countDownLatch.getCount:{}",countDownLatch.getCount());
            countDownLatch.await();
            userVipService.batchInsert(insertUserVipDTOs);
            userVipService.batchUpdate(updateUserVipDTOs);
            taskLog.info("insertUserVipDTOs.size:{},updateUserVipDTOs.size:{}",insertUserVipDTOs.size(),updateUserVipDTOs.size());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


    private static BigDecimal getExchangePrice(UsdkMatchedOrderDO usdkMatchedOrderDO){
        try {
            String baseAssetName = usdkMatchedOrderDO.getAssetName().split("/")[0];
            String quoteAssetName = usdkMatchedOrderDO.getAssetName().split("/")[1];
            if (usdkMatchedOrderDO.getOrderDirection() == 1) {
                if (AssetTypeEnum.TWD.getDesc().equals(quoteAssetName)) {
                    return usdkMatchedOrderDO.getFilledAmount()
                            .multiply(usdkMatchedOrderDO.getFilledPrice())
                            .multiply(rateMap.get(usdkMatchedOrderDO.getAssetName()));
                } else {
                    return usdkMatchedOrderDO.getFilledAmount()
                            .multiply(usdkMatchedOrderDO.getFilledPrice())
                            .multiply(rateMap.get(usdkMatchedOrderDO.getAssetName()).multiply(rateMap.get(quoteAssetName + "/TWD")));
                }
            } else {
                if (AssetTypeEnum.TWD.getDesc().equals(baseAssetName)) {
                    return usdkMatchedOrderDO.getFilledAmount()
                            .multiply(BigDecimal.ONE.divide(rateMap.get(usdkMatchedOrderDO.getAssetName()),4,RoundingMode.HALF_UP));
                } else {
                    return usdkMatchedOrderDO.getFilledAmount()
                            .multiply(BigDecimal.ONE.divide(rateMap.get(usdkMatchedOrderDO.getAssetName()),4,RoundingMode.HALF_UP).multiply(rateMap.get(baseAssetName + "/TWD")));
                }
            }
        } catch (Exception e){
            taskLog.error("getExchangePrice error, usdkMatchedOrderDO:{}", usdkMatchedOrderDO, e);
        }

        return BigDecimal.ZERO;
    }

    private HashMap<String, BigDecimal> getExchangeRate(Long brokerId){
        HashMap<String, BigDecimal> exchangeRateMap = new HashMap<>();
        Collection<BrokerTradingPairConfig> allTradingPairList = brokerTradingPairManager.listAllTradingPairsByBrokerId(brokerId);
        if (brokerId > 1){
            for (BrokerTradingPairConfig temp : allTradingPairList) {
                try {
                    String rate = redisManager.get(MatchedOrderConstants.FOTA_LATEST_USDT_MATCHED_ORDER +
                            temp.getId());
                    rate = rate != null ? rate : BigDecimal.ZERO.toPlainString();
                    BigDecimal rateBd = new BigDecimal(rate);
                    exchangeRateMap.put(temp.getName(), rateBd);
                }catch (Exception e){
                    taskLog.error("getExchangeRate error, BrokerTradingPairConfig:{}, brokerId:{}", temp, brokerId, e);
                }
            }
        } else {
            //主站逻辑
            Long assetToBtcId = null;
            for (BrokerTradingPairConfig temp : allTradingPairList) {
                try {
                    String baseName = brokerTradingPairManager.getTradingPairById(temp.getId()).getBaseName();
                    for (BrokerTradingPairConfig brokerTradingPairConfig : allTradingPairList){
                        if (brokerTradingPairConfig.getBaseName().equals(baseName) && brokerTradingPairConfig.getQuoteName().equals(com.fota.asset.domain.enums.AssetTypeEnum.BTC.getDesc())){
                            assetToBtcId = brokerTradingPairConfig.getId();
                        }
                    }
                    if (assetToBtcId == null){
                        continue;
                    }
                    String rate = redisManager.get(MatchedOrderConstants.FOTA_LATEST_USDT_MATCHED_ORDER +
                            assetToBtcId);
                    rate = rate != null ? rate : BigDecimal.ZERO.toPlainString();
                    BigDecimal rateBd = new BigDecimal(rate);
                    exchangeRateMap.put(temp.getBaseName(), rateBd);
                } catch (Exception e){
                    taskLog.error("getExchangeRate error, BrokerTradingPairConfig:{}, brokerId:{}", temp, brokerId, e);
                }
            }
            exchangeRateMap.put(com.fota.asset.domain.enums.AssetTypeEnum.BTC.getDesc(), new BigDecimal("1"));
        }

        return exchangeRateMap;
    }
}
