package com.fota.trade.task;

import com.fota.account.domain.UserVipDTO;
import com.fota.account.service.UserVipService;
import com.fota.asset.domain.UserCapitalDTO;
import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.asset.service.AssetService;
import com.fota.trade.domain.UsdkMatchedOrderDO;
import com.fota.trade.mapper.sharding.UsdkMatchedOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class TradeAmountStatisticTask {

    @Autowired
    private AssetService assetService;

    @Resource
    private UsdkMatchedOrderMapper usdkMatchedOrderMapper;

    @Autowired
    private UserVipService userVipService;

    /**
     * todo 统计的币种
     */
    private final Long assetId = (long) AssetTypeEnum.TWD.getCode();

    /**
     * 每天0时统计30天内交易总量和平台币锁仓量
     */
//    @Scheduled(cron = "0 0 0 * * ?")
    @Scheduled(cron = "0 0/30 * * * ?")
    public void tradeAmountStatistic() {
        log.info("tradeAmountStatistic task start!");
        List<UserCapitalDTO> userCapitalDTOList = assetService.getUserCapital(assetId);
        log.info("userCapitalDTOList:{}",userCapitalDTOList);
        userCapitalDTOList.forEach(
                e->{
                    Long userId = e.getUserId();

                    BigDecimal canUsedAmount = new BigDecimal(e.getAmount())
                            .subtract(new BigDecimal(e.getLockedAmount()))
                            .setScale(4, RoundingMode.HALF_UP);

                    Long nowTime = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
                    Long startTime = nowTime - 30*24*60*60;
                    List<UsdkMatchedOrderDO> usdkMatchedOrderDOList = usdkMatchedOrderMapper.listByUserId(userId, Collections.singletonList(assetId),0,Integer.MAX_VALUE,startTime,nowTime);

                    BigDecimal tradeAmount30days = usdkMatchedOrderDOList.stream()
                            .map(x->x.getFilledAmount().multiply(x.getFilledPrice()))
                            .reduce(BigDecimal.ZERO,BigDecimal::add)
                            .setScale(4,RoundingMode.HALF_UP);

                    UserVipDTO userVipDTO = new UserVipDTO();
                    userVipDTO.setUserId(userId);
                    userVipDTO.setTradeAmount30days(tradeAmount30days.toPlainString());
                    userVipDTO.setLockedAmount(canUsedAmount.toPlainString());
                    log.info("userVipDTO:{}",userVipDTO);
                    userVipService.updateByUserId(userVipDTO);
                }
        );

    }
}
