package com.fota.trade.util;

import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;
import static com.fota.trade.util.ContractUtils.computeAveragePrice;

@RunWith(JUnit4.class)
public class ContractUtilsTest {

    BigDecimal rate = new BigDecimal("0.001");

    BigDecimal oldAveragePrice = new BigDecimal("500.005");
    BigDecimal oldPosition = BigDecimal.valueOf(5);
    int oldPositionType = ASK.getCode();
    /**
     * 方向相同
     */
    @Test
    public void testSameOrderDirection() {

        BigDecimal filledAmount = BigDecimal.valueOf(10);
        BigDecimal filledPrice = new BigDecimal("600");

        BigDecimal expected = new BigDecimal("566.2683333333").setScale(8, BigDecimal.ROUND_DOWN);
        BigDecimal averagePrice = computeAveragePrice(ASK.getCode(), oldPositionType, rate, oldPosition, oldAveragePrice, filledAmount, filledPrice);

        Assert.assertNotNull(averagePrice);
        assert BasicUtils.equal(expected, averagePrice.setScale(8, BigDecimal.ROUND_DOWN));
    }

    /**
     * 方向相反 持仓方向改变
     */
    @Test
    public void testOppositeOrderDirectionWithChangeDirection() {


        BigDecimal filledAmount = BigDecimal.valueOf(10);
        BigDecimal filledPrice = new BigDecimal("600.0");

        //600 + 600*0.001
        BigDecimal expected = new BigDecimal("600.6");

        BigDecimal averagePrice = computeAveragePrice(BID.getCode(), oldPositionType, rate, oldPosition, oldAveragePrice, filledAmount, filledPrice);

        Assert.assertNotNull(averagePrice);
        assert BasicUtils.equal(expected, averagePrice.setScale(8, BigDecimal.ROUND_DOWN));

    }

    /**
     * 方向相反 持仓方向不变
     */
    @Test
    public void testOppositeOrderDirection() {

        BigDecimal filledAmount = new BigDecimal("4.9");
        BigDecimal filledPrice = new BigDecimal("500.0");

        BigDecimal expected = oldAveragePrice;

        BigDecimal averagePrice = computeAveragePrice(BID.getCode(), oldPositionType, rate, oldPosition, oldAveragePrice, filledAmount, filledPrice);


        Assert.assertNotNull(averagePrice);
        assert BasicUtils.equal(expected, averagePrice.setScale(8, BigDecimal.ROUND_DOWN));

    }

    /**
     * 建仓
     */
    @Test
    public void testBuildPosition() {

        BigDecimal filledAmount = BigDecimal.TEN;
        BigDecimal filledPrice = new BigDecimal("500.0");

        BigDecimal expected = new BigDecimal("499.5").setScale(8, RoundingMode.DOWN);

        BigDecimal averagePrice = computeAveragePrice(ASK.getCode(), ASK.getCode(), rate, BigDecimal.ZERO, null, filledAmount, filledPrice);

        Assert.assertNotNull(averagePrice);
        assert BasicUtils.equal(expected, averagePrice.setScale(8, BigDecimal.ROUND_DOWN));

    }
}