package com.fota.trade.service.impl;

import com.fota.common.Result;
import com.fota.trade.domain.ContractAccount;
import com.fota.trade.service.ContractAccountService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Created by Swifree on 2018/8/25.
 * Code is the law
 */
@Service
public class ContractAccountServiceImpl implements ContractAccountService {
    @Override
    public Result<ContractAccount> getContractAccount(long userId) {
        ContractAccount account = new ContractAccount();
        account.setUserId(userId)
                .setAccountEquity(BigDecimal.ZERO)
                .setAvailableAmount(BigDecimal.ONE)
                .setFrozenAmount(BigDecimal.ZERO)
                .setMarginCallRequirement(BigDecimal.ZERO);
        return Result.<ContractAccount>create().success(account);
    }
}
