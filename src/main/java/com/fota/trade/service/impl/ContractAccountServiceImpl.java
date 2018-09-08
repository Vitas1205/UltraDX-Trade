package com.fota.trade.service.impl;

import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.common.Result;
import com.fota.common.ResultCodeEnum;
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
@Service("contractAccountService")
@Slf4j
public class ContractAccountServiceImpl implements ContractAccountService {
    @Autowired
    private ContractOrderManager contractOrderManager;
    @Autowired
    private AssetService assetService;
    @Override
    public Result<ContractAccount> getContractAccount(long userId) {
        ContractAccount account = new ContractAccount();
        Map<String, BigDecimal> resultMap = new HashMap<>();
        UserContractDTO userContractDTO = new UserContractDTO();
        try {
            resultMap = contractOrderManager.getAccountMsg(userId);
        }catch (Exception e){
            log.error("contractOrderManager.getAccountMsg failed{}", userId, e);
            return Result.<ContractAccount>create().error(ResultCodeEnum.SERVICE_FAILED);
        }
        try {
            userContractDTO = assetService.getContractAccount(userId);
        }catch (Exception e){
            log.error("assetService.getContractAccount{}", userId, e);
            return Result.<ContractAccount>create().error(ResultCodeEnum.SERVICE_FAILED);
        }
        BigDecimal marginCallRequirement = resultMap.get(Constant.POSITION_MARGIN);
        BigDecimal floatingPL = resultMap.get(Constant.FLOATING_PL);
        BigDecimal frozenAmount = contractOrderManager.getEntrustMargin(userId);
        BigDecimal totalAmount = new BigDecimal(userContractDTO.getAmount());
        account.setUserId(userId)
                .setAccountEquity(totalAmount.add(floatingPL))
                .setAvailableAmount(totalAmount.add(floatingPL).subtract(marginCallRequirement).subtract(frozenAmount))
                .setFrozenAmount(frozenAmount)
                .setFloatingPL(floatingPL)
                .setMarginCallRequirement(marginCallRequirement);
        return Result.<ContractAccount>create().success(account);
    }
}
