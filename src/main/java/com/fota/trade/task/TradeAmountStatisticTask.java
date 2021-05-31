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

    @PostConstruct
    public void initRateMap(){
        Long brokerId = 508090L;
        rateMap = getExchangeRate(brokerId);
        taskLog.info("rateMap:{}",rateMap);
    }

    /**
     * 每天0时统计30天内交易总量和平台币锁仓量
     */
//    @Scheduled(cron = "0 0 0 * * ?")
    @Scheduled(cron = "0 0/30 * * * ?")
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
        Map<Long, List<UserCapitalDTO>> map = userCapitalDTOList.stream().collect(Collectors.groupingBy(UserCapitalDTO::getUserId));
        //多线程执行
        ExecutorService threadPool = new ThreadPoolExecutor(8, 20,
                0L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
        for(Map.Entry<Long, List<UserCapitalDTO>> entry : map.entrySet()){
            Long userId = entry.getKey();
            List<UserCapitalDTO> list = entry.getValue();
            threadPool.execute(new StatisticTask(userId,list));
        }
    }

    class StatisticTask implements Runnable{
        private final Long userId;
        private final List<UserCapitalDTO> list;
        StatisticTask(Long userId, List<UserCapitalDTO> list){
            this.userId = userId;
            this.list = list;
        }

        @Override
        public void run() {
            try {
                BigDecimal canUsedAmount = BigDecimal.ZERO;
                BigDecimal tradeAmount30days = BigDecimal.ZERO;
                for(UserCapitalDTO userCapitalDTO : list) {
                    if(userCapitalDTO.getAssetId().equals(AssetTypeEnum.TWD.getCode())) {
                        canUsedAmount = new BigDecimal(userCapitalDTO.getAmount())
                                .subtract(new BigDecimal(userCapitalDTO.getLockedAmount()))
                                .setScale(4, RoundingMode.HALF_UP);
                    }
                }

                Long nowTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
                Long startTime = LocalDateTime.now().minusDays(30).toEpochSecond(ZoneOffset.UTC);
                List<UsdkMatchedOrderDO> usdkMatchedOrderDOList = usdkMatchedOrderMapper.listByUserId(userId, null, 0, Integer.MAX_VALUE, startTime, nowTime);
                if(!CollectionUtils.isEmpty(usdkMatchedOrderDOList)) {
                    tradeAmount30days = usdkMatchedOrderDOList.stream()
                            .filter(x-> "UNKNOW".equals(x.getAssetName()))
//                            .map(x -> x.getFilledAmount().multiply(x.getFilledPrice()).multiply(getRateByAssetName(x.getOrderDirection(),x.getAssetName())))
                            .map(TradeAmountStatisticTask::getExchangePrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(4, RoundingMode.HALF_UP);
                }

                UserVipDTO userVipDTO = userVipService.getByUserId(userId);
                if(userVipDTO != null){
                    userVipDTO.setTradeAmount30days(tradeAmount30days.toPlainString());
                    userVipDTO.setLockedAmount(canUsedAmount.toPlainString());
                    taskLog.info("update userVipDTO:{}",userVipDTO);
                    userVipService.updateByUserId(userVipDTO);
                }else{
                    userVipDTO = new UserVipDTO();
                    userVipDTO.setUserId(userId);
                    userVipDTO.setTradeAmount30days(tradeAmount30days.toPlainString());
                    userVipDTO.setLockedAmount(canUsedAmount.toPlainString());
                    taskLog.info("insert userVipDTO:{}",userVipDTO);
                    userVipService.insert(userVipDTO);
                }

            }catch (Exception e){
                taskLog.error("task error, userId{}",userId,e);
            }

        }
    }


    private static BigDecimal getExchangePrice(UsdkMatchedOrderDO usdkMatchedOrderDO){
        String assetName = usdkMatchedOrderDO.getAssetName();
        String baseAssetName = assetName.split("/")[0];
        String quoteAssetName = assetName.split("/")[1];
        if(usdkMatchedOrderDO.getOrderDirection()==1){
            if(AssetTypeEnum.TWD.getDesc().equals(quoteAssetName)){
                return usdkMatchedOrderDO.getFilledAmount()
                        .multiply(usdkMatchedOrderDO.getFilledPrice())
                        .multiply(rateMap.get(assetName));
            }else{
                return usdkMatchedOrderDO.getFilledAmount()
                        .multiply(usdkMatchedOrderDO.getFilledPrice())
                        .multiply(rateMap.get(assetName).multiply(rateMap.get(quoteAssetName+"/TWD")));
            }
        }else{
            if(AssetTypeEnum.TWD.getDesc().equals(baseAssetName)){
                return usdkMatchedOrderDO.getFilledAmount()
                        .multiply(rateMap.get(quoteAssetName+"/"+baseAssetName));
            }else{
                return usdkMatchedOrderDO.getFilledAmount()
                        .multiply(rateMap.get(quoteAssetName+"/"+baseAssetName).multiply(rateMap.get(quoteAssetName+"/TWD")));
            }
        }
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
