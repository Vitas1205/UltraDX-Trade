package com.fota.trade.util;

import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.fota.trade.util.ContractUtils.computeAveragePrice;

@RunWith(JUnit4.class)
@Transactional
public class ContractUtilsTest {

    /**
     * 方向相同
     */
    @Test
    public void testSameOrderDirection() {
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        contractOrderDO.setFee(new BigDecimal("0.001"));

        long filledAmount = 10L;
        BigDecimal filledPrice = new BigDecimal("500.0");

        BigDecimal contractSize = new BigDecimal("0.01");

        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setUnfilledAmount(5L);
        userPositionDO.setAveragePrice(new BigDecimal("500.005"));
        userPositionDO.setPositionType(OrderDirectionEnum.BID.getCode());

        BigDecimal expected = new BigDecimal("500.005").setScale(8, RoundingMode.DOWN);

        BigDecimal averagePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount, contractSize);

        //Assert.assertNotNull(averagePrice);
        //Assert.assertEquals(expected, averagePrice);
    }

    /**
     * 方向相反 持仓方向改变
     */
    @Test
    public void testOppositeOrderDirectionWithChangeDirection() {
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        contractOrderDO.setFee(new BigDecimal("0.001"));

        long filledAmount = 10L;
        BigDecimal filledPrice = new BigDecimal("500.0");

        BigDecimal contractSize = new BigDecimal("0.01");

        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setUnfilledAmount(5L);
        userPositionDO.setAveragePrice(new BigDecimal("500.005"));
        userPositionDO.setPositionType(OrderDirectionEnum.BID.getCode());

        BigDecimal expected = new BigDecimal("499.995").setScale(8, RoundingMode.DOWN);

        BigDecimal averagePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount, contractSize);

        //Assert.assertNotNull(averagePrice);
        //Assert.assertEquals(expected, averagePrice);
    }

    /**
     * 方向相反 持仓方向不变
     */
    @Test
    public void testOppositeOrderDirection() {
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        contractOrderDO.setFee(new BigDecimal("0.001"));

        long filledAmount = 1L;
        BigDecimal filledPrice = new BigDecimal("500.0");

        BigDecimal contractSize = new BigDecimal("0.01");

        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setUnfilledAmount(5L);
        userPositionDO.setAveragePrice(new BigDecimal("500.005"));
        userPositionDO.setPositionType(OrderDirectionEnum.BID.getCode());

        BigDecimal expected = new BigDecimal("500.005").setScale(8, RoundingMode.DOWN);

        BigDecimal averagePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount, contractSize);

        //Assert.assertNotNull(averagePrice);
        //Assert.assertEquals(expected, averagePrice);
    }

    /**
     * 建仓
     */
    @Test
    public void testBuildPosition() {
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        contractOrderDO.setFee(new BigDecimal("0.001"));

        long filledAmount = 10L;
        BigDecimal filledPrice = new BigDecimal("500.0");

        BigDecimal contractSize = new BigDecimal("0.01");

        BigDecimal expected = new BigDecimal("499.995").setScale(8, RoundingMode.DOWN);

        BigDecimal averagePrice = computeAveragePrice(contractOrderDO, null, filledPrice, filledAmount, contractSize);

        //Assert.assertNotNull(averagePrice);
        //Assert.assertEquals(expected, averagePrice);
    }

}