package com.fota.trade.mapper;

import com.fota.trade.domain.UserContractLeverDO;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/12
 */
@SpringBootTest
@RunWith(SpringRunner.class)
public class LeverMapperTest {

    @Resource
    private UserContractLeverMapper userContractLeverMapper;

    @Test
    public void selectLeverTest(){
        UserContractLeverDO userContractLeverDO = new UserContractLeverDO();
        userContractLeverDO.setUserId(9527L);
        userContractLeverDO.setAssetId(1);
        userContractLeverDO.setAssetName("btc");
        userContractLeverDO.setLever(10);
        int insetRet = userContractLeverMapper.insert(userContractLeverDO);
        Assert.assertTrue(insetRet > 0);
    }

    @Test
    public void testSelect() {
        List<UserContractLeverDO> list = userContractLeverMapper.listUserContractLever(9527L);
        Assert.assertTrue(list != null);
    }

}
