package com.fota.trade.manager;

import com.fota.trade.config.BrokerUsdkOrderFeeRateConfig;
import com.fota.trade.domain.BrokerrFeeRateDO;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: Harry Wang
 * @Date: 2019/4/9 14:55
 * @Version 1.0
 */
@Component
public class BrokerUsdkOrderFeeListManager {
    private static BrokerUsdkOrderFeeRateConfig brokerUsdkOrderFeeRateConfig;

    @Getter
    private List<BrokerrFeeRateDO> feeRateList;

    @PostConstruct
    private void initFeeRate(){
        List<String> list = brokerUsdkOrderFeeRateConfig.getBrokerUsdkOrderFeeRateList();
        List <BrokerrFeeRateDO> ret = new ArrayList<>();
        for (String temp : list){
            String[] str = temp.split(",");
            BrokerrFeeRateDO  brokerrFeeRateDO = new BrokerrFeeRateDO();
            brokerrFeeRateDO.setBrokerId(Long.valueOf(str[0]));
            brokerrFeeRateDO.setFeeRate(new BigDecimal(str[1]));
            ret.add(brokerrFeeRateDO);
        }
        feeRateList = ret;
    }

    @Autowired
    public void setBrokerUsdkOrderFeeRateConfig(BrokerUsdkOrderFeeRateConfig brokerUsdkOrderFeeRateConfig){
        BrokerUsdkOrderFeeListManager.brokerUsdkOrderFeeRateConfig = brokerUsdkOrderFeeRateConfig;
    }
}
