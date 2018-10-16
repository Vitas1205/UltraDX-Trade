package com.fota.trade.service.internal;

import com.fota.trade.config.BlackListConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BlackListService {

    private BlackListConfig blackListConfig;

    public boolean contains(Long userId) {
        return blackListConfig.getBlackList() != null && blackListConfig.getBlackList().contains(userId);
    }

    @Autowired
    public void setBlackListConfig(BlackListConfig blackListConfig) {
        this.blackListConfig = blackListConfig;
    }
}
