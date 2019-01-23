package com.fota.trade.service.impl;

import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.UserContractLeverDO;
import com.fota.trade.domain.UserContractLeverDTO;
import com.fota.trade.mapper.trade.ContractCategoryMapper;
import com.fota.trade.mapper.trade.UserContractLeverMapper;
import com.fota.trade.service.UserContractLeverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fota.trade.client.constants.Constants.DEFAULT_LEVER;

/**
 * @author Gavin Shen
 * @Date 2018/7/12
 */
@Slf4j
public class UserContractLeverServiceImpl implements UserContractLeverService  {

    @Resource
    private UserContractLeverMapper userContractLeverMapper;
    @Resource
    private ContractCategoryMapper contractCategoryMapper;

    @Override
    public List<UserContractLeverDTO> listUserContractLever(long userId) {
        List<UserContractLeverDTO> resultList = new ArrayList<>();
        List<UserContractLeverDO> list;

        try {
            list = userContractLeverMapper.listUserContractLever(userId);
            if (CollectionUtils.isEmpty(list)) {
                list = new LinkedList<>();
            }
            Set<Integer>  leverAssetList = list.stream().map(UserContractLeverDO::getAssetId).collect(Collectors.toSet());
            List<UserContractLeverDO> defaultLevers = Stream.of(AssetTypeEnum.values()).filter(x -> x.isValid() && !leverAssetList.contains(x.getCode()))
                    .map(x -> {
                        UserContractLeverDO userContractLeverDO = new UserContractLeverDO();
                        userContractLeverDO.setAssetId(x.getCode());
                        userContractLeverDO.setAssetName(x.name());
                        userContractLeverDO.setLever(DEFAULT_LEVER);
                        return userContractLeverDO;
                    })
                    .collect(Collectors.toList());

            list.addAll(defaultLevers);

            List<Integer> validAssetCodes = AssetTypeEnum.getValidAssetCodes();
            if (list != null && list.size() > 0) {
                for (UserContractLeverDO userContractLeverDO : list) {
                    if (!validAssetCodes.contains(userContractLeverDO.getAssetId())) {
                        continue;
                    }
                    UserContractLeverDTO newUserContractLeverDTO = new UserContractLeverDTO();
                    newUserContractLeverDTO.setAssetId(userContractLeverDO.getAssetId());
                    newUserContractLeverDTO.setAssetName(userContractLeverDO.getAssetName());
                    newUserContractLeverDTO.setLever(userContractLeverDO.getLever());
                    resultList.add(newUserContractLeverDTO);
                }
            }
        } catch (Exception e) {
            log.error("userContractLeverMapper.listUserContractLever({})", userId,  e);
        }
        return resultList;
    }

    @Override
    public boolean setUserContractLever(long userId, List<UserContractLeverDTO> list) {
        if (userId <= 0 || list == null || list.size() <= 0) {
            return false;
        }
        for (UserContractLeverDTO temp : list) {
            UserContractLeverDO userContractLeverDO = new UserContractLeverDO();
            userContractLeverDO.setLever(temp.getLever());
            userContractLeverDO.setAssetName(temp.assetName);
            userContractLeverDO.setAssetId(temp.assetId);
            userContractLeverDO.setUserId(userId);
            try {
                int aff = userContractLeverMapper.update(userContractLeverDO);
                if (0 == aff) {
                    userContractLeverMapper.insert(userContractLeverDO);
                }
            } catch (Exception e) {
                log.error("userContractLeverMapper.insert({})", userContractLeverDO, e);
                return false;
            }
        }
        return true;
    }

    @Override
    public UserContractLeverDTO getLeverByAssetId(long userId, int assetId) {
        if (userId <= 0 || assetId <= 0) {
            return null;
        }
        try {
            UserContractLeverDO userContractLeverDO = userContractLeverMapper.selectUserContractLever(userId, assetId);
            if (userContractLeverDO != null) {
                UserContractLeverDTO userContractLeverDTO = new UserContractLeverDTO();
                userContractLeverDTO.setAssetId(userContractLeverDO.getAssetId());
                userContractLeverDTO.setAssetName(userContractLeverDO.getAssetName());
                userContractLeverDTO.setLever(userContractLeverDO.getLever());
                return userContractLeverDTO;
            }
        } catch (Exception e) {
            log.error("userContractLeverMapper.selectUserContractLever exception", e);
        }
        return null;
    }

    @Override
    public UserContractLeverDTO getLeverByContractId(long userId, long contractId) {
        if (userId <= 0 || contractId <= 0) {
            return null;
        }
        ContractCategoryDO contractCategoryDO = null;
        try {
            contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractId);
        } catch (Exception e) {
            log.error("contractCategoryMapper.selectByIdAndUserId exception", e);
        }
        if (contractCategoryDO == null ||
                contractCategoryDO.getAssetId() == null ||
                contractCategoryDO.getAssetId() <= 0) {
            log.error("illegal contractCategory, contractId :{}", contractId);
            return null;
        }
        return getLeverByAssetId(userId, contractCategoryDO.getAssetId());
    }

}
