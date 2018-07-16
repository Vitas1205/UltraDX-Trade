package com.fota.trade.manager;

import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.UserContractLeverDO;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserContractLeverMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Gavin Shen
 * @Date 2018/7/14
 */
@Component
@Slf4j
public class ContractLeverManager {

    private static final int DEFAULT_LEVER = 10;

    @Resource
    private ContractOrderMapper contractOrderMapper;
    @Resource
    private ContractCategoryMapper contractCategoryMapper;
    @Resource
    private UserContractLeverMapper userContractLeverMapper;

    public Integer getLeverByContractId(Long userId, Long contractId) {
        if (contractId == null || contractId <= 0) {
            return DEFAULT_LEVER;
        }
        try {
            ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractId);
            Integer assetId = contractCategoryDO.getAssetId();
            UserContractLeverDO userContractLeverDO = userContractLeverMapper.selectUserContractLever(userId, assetId);
            if (userContractLeverDO.getLever() == null){
                return DEFAULT_LEVER;
            }
            return userContractLeverDO.getLever();
        } catch (Exception e) {
            log.error("getLeverByContractId({}, {})", userId, contractId, e);
        }
        return DEFAULT_LEVER;
    }

}
