package com.fota.trade.service.impl;

import com.fota.asset.domain.AssetCategoryDTO;
import com.fota.asset.service.AssetService;
import com.fota.thrift.ThriftJ;
import com.fota.trade.domain.UserContractLeverDO;
import com.fota.trade.domain.UserContractLeverDTO;
import com.fota.trade.mapper.UserContractLeverMapper;
import com.fota.trade.service.UserContractLeverService;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static org.bouncycastle.asn1.x500.style.RFC4519Style.l;

/**
 * @author Gavin Shen
 * @Date 2018/7/12
 */
@Service
@Slf4j
public class UserContractLeverServiceImpl implements UserContractLeverService  {

    @Resource
    private UserContractLeverMapper userContractLeverMapper;
    @Autowired
    private ThriftJ thriftJ;
    @Value("${fota.asset.server.thrift.port}")
    private int thriftPort;
    @PostConstruct
    public void init() {
        thriftJ.initService("FOTA-ASSET", thriftPort);
    }

    private AssetService.Client getAssetService() {
        AssetService.Client serviceClient =
                thriftJ.getServiceClient("FOTA-ASSET")
                        .iface(AssetService.Client.class, "assetService");
        return serviceClient;
    }


    @Override
    public List<UserContractLeverDTO> listUserContractLever(long userId) {
        List<UserContractLeverDTO> resultList = new ArrayList<>();
        List<UserContractLeverDO> list;
        try {

            List<AssetCategoryDTO> assetList = getAssetService().getAssetList();
            for (AssetCategoryDTO assetCategoryDTO : assetList) {
                if (assetCategoryDTO != null && !assetCategoryDTO.getName().toUpperCase().equals("USDK")) {
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
            list = userContractLeverMapper.listUserContractLever(userId);
            if (list != null && list.size() > 0) {
                for (UserContractLeverDO userContractLeverDO : list) {
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
}
