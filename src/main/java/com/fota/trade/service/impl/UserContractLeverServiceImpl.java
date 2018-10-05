package com.fota.trade.service.impl;

import com.fota.asset.domain.AssetCategoryDTO;
import com.fota.asset.service.AssetService;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.UserContractLeverDO;
import com.fota.trade.domain.UserContractLeverDTO;
import com.fota.trade.domain.enums.AssetTypeEnum;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.UserContractLeverMapper;
import com.fota.trade.service.UserContractLeverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static org.bouncycastle.asn1.x500.style.RFC4519Style.l;

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
    @Autowired
    private AssetService assetService;

    private AssetService getAssetService() {
        return assetService;
    }


    @Override
    public List<UserContractLeverDTO> listUserContractLever(long userId) {
        List<UserContractLeverDTO> resultList = new ArrayList<>();
        List<UserContractLeverDO> list;
        List<AssetCategoryDTO> assetList = getAssetService().getAssetList();
        for (AssetCategoryDTO assetCategoryDTO : assetList) {
            if (assetCategoryDTO != null && !assetCategoryDTO.getName().toUpperCase().equals("USDT")) {
                UserContractLeverDO userContractLeverDO = userContractLeverMapper.selectUserContractLever(userId, assetCategoryDTO.getId());
                if (userContractLeverDO == null) {
                    UserContractLeverDO newUserContractLeverDO = new UserContractLeverDO();
                    newUserContractLeverDO.setUserId(userId);
                    newUserContractLeverDO.setAssetId(assetCategoryDTO.getId());
                    newUserContractLeverDO.setAssetName(assetCategoryDTO.getName());
                    newUserContractLeverDO.setLever(10);
                    userContractLeverMapper.insert(newUserContractLeverDO);
                }
            }
        }
        try {
            list = userContractLeverMapper.listUserContractLever(userId);
            if (list != null && list.size() > 0) {
                for (UserContractLeverDO userContractLeverDO : list) {
                    if (userContractLeverDO.getAssetId().equals(AssetTypeEnum.USDT.getCode())
                            || userContractLeverDO.getAssetId().equals(AssetTypeEnum.FOTA.getCode())) {
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
            log.error("userContractLeverMapper.listUserContractLever({})", l, e);
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
                userContractLeverMapper.update(userContractLeverDO);
            } catch (Exception e) {
                log.error("userContractLeverMapper.insert({})", userContractLeverDO, e);
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
