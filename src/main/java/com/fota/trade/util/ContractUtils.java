package com.fota.trade.util;

import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.enums.PositionTypeEnum;

import java.math.BigDecimal;
import java.util.Objects;

import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;

/**
 * Created by Swifree on 2018/8/22.
 * Code is the law
 */
public class ContractUtils {
    private static int scale = 16;
    private static int roundingMode = BigDecimal.ROUND_HALF_UP;

    public static UserPositionDO buildPosition(ContractOrderDO contractOrderDO, int lever, BigDecimal filledAmount, BigDecimal filledPrice) {
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
        userPositionDO.setAveragePrice(computeAveragePrice(contractOrderDO, null, filledPrice, filledAmount));
        userPositionDO.setUnfilledAmount(filledAmount);
        userPositionDO.setStatus(1);
        userPositionDO.setUserId(contractOrderDO.getUserId());
        userPositionDO.setContractName(contractOrderDO.getContractName());
        userPositionDO.setContractId(contractOrderDO.getContractId());
        userPositionDO.setLever(lever);
        return userPositionDO;
    }

    /**
     * 计算开仓均价   公式在设计文档5.3.2.7
     *
     * @param contractOrderDO  委托单
     * @param userPositionDO   现有持仓
     * @param filledAmount     成交数量
     * @param filledPrice      成交价格
     * @return 开仓均价
     */
    public static BigDecimal computeAveragePrice(ContractOrderDO contractOrderDO, UserPositionDO userPositionDO,
                                                 BigDecimal filledPrice, BigDecimal filledAmount) {
        BigDecimal x = contractOrderDO.getOrderDirection() == ASK.getCode() ? BigDecimal.valueOf(-1) : BigDecimal.ONE;
        BigDecimal averagePrice = BigDecimal.ZERO;
        if (Objects.isNull(userPositionDO)) {
            BigDecimal temp = filledPrice.multiply(filledAmount);
            averagePrice = temp.add(
                    temp.multiply(contractOrderDO.getFee())
                            .multiply(x))
                    .divide(filledAmount, scale, roundingMode);
        } else {
            if (Objects.equals(contractOrderDO.getOrderDirection(), userPositionDO.getPositionType())) {
                //成交单和持仓是同方向
                BigDecimal newTotalAmount = userPositionDO.getUnfilledAmount().add(filledAmount);
                BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(userPositionDO.getUnfilledAmount());
                BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
                averagePrice = oldTotalPrice.add(addedTotalPrice)
                        .add(addedTotalPrice.multiply(contractOrderDO.getFee())
                                .multiply(x))
                        .divide(newTotalAmount, scale, roundingMode);
            } else {
                //成交单和持仓是反方向 （平仓）
                if (filledAmount.compareTo(userPositionDO.getUnfilledAmount()) <= 0) {
                    //不改变仓位方向
                    BigDecimal newTotalAmount = userPositionDO.getUnfilledAmount().subtract(filledAmount);
                    if (!BigDecimal.ZERO.equals(newTotalAmount.stripTrailingZeros())) {
                        averagePrice = userPositionDO.getAveragePrice()
                                .setScale(scale, roundingMode);
                    }
                } else {
                    //改变仓位方向
                    BigDecimal newTotalAmount = filledAmount.subtract(userPositionDO.getUnfilledAmount());

                    BigDecimal temp = filledPrice.multiply(newTotalAmount);
                    averagePrice = temp.add(
                            temp.multiply(contractOrderDO.getFee())
                                    .multiply(x))
                            .divide(newTotalAmount, scale, roundingMode);
                }
            }
        }

        return averagePrice;
    }

    public static int toDirection(int positionType) {
        if (positionType == PositionTypeEnum.EMPTY.getCode()) {
            return -1;
        }
        return 1;
    }

}
