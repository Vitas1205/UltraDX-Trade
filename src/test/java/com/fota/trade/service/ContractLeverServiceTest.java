package com.fota.trade.service;

import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.trade.common.TestConfig;
import com.fota.trade.domain.UserContractLeverDTO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.Arrays;
import java.util.List;

import static com.fota.asset.domain.enums.AssetTypeEnum.BTC;
import static com.fota.trade.common.TestConfig.userId;

/**
 * Created by lds on 2018/10/22.
 * Code is the law
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class ContractLeverServiceTest {
    @Autowired
    private UserContractLeverService userContractLeverService;

    @Test
    public void testUpdateLever(){
        UserContractLeverDTO userContractLeverDTO = new UserContractLeverDTO();
        userContractLeverDTO.setLever(1);
        userContractLeverDTO.setAssetId(BTC.getCode());
        userContractLeverDTO.setAssetName(BTC.name());
        boolean suc = userContractLeverService.setUserContractLever(userId, Arrays.asList(userContractLeverDTO));
        UserContractLeverDTO userContractLeverDTO1 = userContractLeverService.getLeverByAssetId(userId, BTC.getCode());
        assert suc && userContractLeverDTO1.getLever() == 1;
    }
    @Test
    public void testList(){
        List<UserContractLeverDTO> list = userContractLeverService.listUserContractLever(userId);
        System.out.println(list);
    }
}
