package com.fota.trade.manager;

import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.UserContractLeverDO;
import com.fota.trade.mapper.sharding.ContractOrderMapper;
import com.fota.trade.mapper.trade.ContractCategoryMapper;
import com.fota.trade.mapper.trade.UserContractLeverMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            if (userContractLeverDO == null || userContractLeverDO.getLever() == null){
                return DEFAULT_LEVER;
            }
            return userContractLeverDO.getLever();
        } catch (Exception e) {
            log.error("getLeverByContractId({}, {})", userId, contractId, e);
        }
        return DEFAULT_LEVER;
    }
    public Map<Integer, Integer> getLeverMapByUserId(long userId) {
        List<UserContractLeverDO> contractLeverDOList =  userContractLeverMapper.listUserContractLever(userId);
        if (CollectionUtils.isEmpty(contractLeverDOList)) {
            return new HashMap<>();
        }
        return contractLeverDOList.stream().collect(Collectors.toMap(UserContractLeverDO::getAssetId, UserContractLeverDO::getLever));
    }

    public BigDecimal findLever(Map<Integer, Integer> leverMap, Integer assetId) {
        return new BigDecimal(doFindLever(leverMap, assetId));
    }
    public Integer doFindLever(Map<Integer, Integer> leverMap, Integer assetId){
        Integer lever = leverMap.get(assetId);
        if (null == lever) {
            return DEFAULT_LEVER;
        }
        return lever;
    }

}
