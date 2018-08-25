package com.fota.trade.service.impl;

import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.common.Result;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.ContractAccount;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.service.ContractAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Swifree on 2018/8/25.
 * Code is the law
 */
@Service
@Slf4j
public class ContractAccountServiceImpl implements ContractAccountService {
    @Autowired
    ContractOrderManager contractOrderManager;
    @Autowired
    AssetService assetService;
    @Override
    public Result<ContractAccount> getContractAccount(long userId) {
        ContractAccount account = new ContractAccount();
        Map<String, BigDecimal> resultMap = new HashMap<>();
        UserContractDTO userContractDTO = new UserContractDTO();
        try {
            resultMap = contractOrderManager.getAccountDetailMsg(userId);
        }catch (Exception e){
            log.error("contractOrderManager.getAccountDetailMsg failed{}", userId, e);
            throw new RuntimeException("contractOrderManager.getAccountDetailMsg failed{}", e);
        }
        try {
            userContractDTO = assetService.getContractAccount(userId);
        }catch (Exception e){
            log.error("assetService.getContractAccount{}", userId, e);
            throw new RuntimeException("assetService.getContractAccount{}", e);
        }
        BigDecimal marginCallRequirement = resultMap.get(Constant.POSITION_MARGIN);
        BigDecimal floatingPL = resultMap.get(Constant.FLOATING_PL);
        BigDecimal frozenAmount = resultMap.get(Constant.ENTRUST_MARGIN);
        BigDecimal totalAmount = new BigDecimal(userContractDTO.getAmount());
        account.setUserId(userId)
                .setAccountEquity(totalAmount.add(floatingPL))
                .setAvailableAmount(totalAmount.add(floatingPL).subtract(marginCallRequirement))
                .setFrozenAmount(frozenAmount)
                .setFloatingPL(floatingPL)
                .setMarginCallRequirement(marginCallRequirement);
        return Result.<ContractAccount>create().success(account);
    }
}
