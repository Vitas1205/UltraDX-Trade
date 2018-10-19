package com.fota.trade.service.impl;

import com.fota.common.Result;
import com.fota.common.ResultCodeEnum;
import com.fota.trade.domain.ContractAccount;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.service.ContractAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Swifree on 2018/8/25.
 * Code is the law
 */
@Slf4j
@Service("contractAccountService")
public class ContractAccountServiceImpl implements ContractAccountService {
    @Autowired
    private ContractOrderManager contractOrderManager;

    @Override
    public Result<ContractAccount> getContractAccount(long userId) {
        ContractAccount account;

        try {
            account = contractOrderManager.computeContractAccount(userId, null);
            if (null == account) {
                log.error("computeContractAccount return null, userId={}", userId);
            }
        }catch (Exception e){
            log.error("=ContractOrderManager.computeContractAccount({}) exception", userId, e);
            return Result.<ContractAccount>create().error(ResultCodeEnum.SERVICE_FAILED);
        }
        return Result.<ContractAccount>create().success(account);
    }
}
