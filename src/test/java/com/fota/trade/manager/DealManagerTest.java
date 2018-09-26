package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.trade.client.PostDealMessage;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.ContractUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;

import static java.math.BigDecimal.ZERO;

/**
 * Created by Swifree on 2018/9/23.
 * Code is the law
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class DealManagerTest {
    @Resource
    private DealManager dealManager;
    @Resource
    private UserPositionMapper userPositionMapper;
    @Resource
    private AssetService assetService;

    private long userId=274;
    private long contractId = 1007;

    BigDecimal rate = new BigDecimal("0.0005");
    @Test
    public void testClosePosition(){
        UserPositionDO userPositionDO = userPositionMapper.selectByUserIdAndContractId(userId, contractId);
        UserContractDTO userContractDTO = assetService.getContractAccount(userId);
        BigDecimal oldBalance = new BigDecimal(userContractDTO.getAmount());
        BigDecimal oldPosition = userPositionDO.computeSignAmount();
        BigDecimal oldOpenPrice = userPositionDO.getAveragePrice();
        if (null !=userPositionDO) {
            BigDecimal filledAmount = userPositionDO.getUnfilledAmount(),
                    filledPrice =new BigDecimal("6.0");
            PostDealMessage postDealMessage = new PostDealMessage();
            postDealMessage.setFilledAmount(filledAmount);
            postDealMessage.setFilledPrice(filledPrice);
            postDealMessage.setMatchId(1);
            ContractOrderDO contractOrderDO = new ContractOrderDO();
            //去相反方向
            contractOrderDO.setOrderDirection(3 - userPositionDO.getPositionType());
            contractOrderDO.setContractId(contractId);
            contractOrderDO.setUserId(userId);
            contractOrderDO.setFee(rate);
            postDealMessage.setContractOrderDO(contractOrderDO);

            postDealMessage.setContractOrderDO(contractOrderDO);
            dealManager.postDeal(Arrays.asList(postDealMessage));
            BigDecimal newBalance = new BigDecimal(assetService.getContractAccount(userId).getAmount());

            BigDecimal expectBalance = oldBalance.add(ContractUtils.computeClosePL(rate, filledAmount, filledPrice,oldPosition,
                    ZERO, oldOpenPrice));
            UserPositionDO curPositionDO = userPositionMapper.selectByUserIdAndContractId(userId, contractId);
            assert curPositionDO.getUnfilledAmount().compareTo(ZERO) == 0;
            assert BasicUtils.equal(expectBalance, newBalance);

        }

    }
}
