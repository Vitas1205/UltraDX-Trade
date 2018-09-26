package com.fota.trade.util;

import com.fota.asset.domain.ContractDealer;
import com.fota.trade.common.UpdatePositionResult;
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
    public static final BigDecimal NEG_ONE = BigDecimal.ONE.negate();

    public static UserPositionDO buildPosition(ContractOrderDO contractOrderDO) {
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
        userPositionDO.setAveragePrice(null);
        userPositionDO.setUnfilledAmount(BigDecimal.ZERO);
        userPositionDO.setStatus(1);
        userPositionDO.setUserId(contractOrderDO.getUserId());
        userPositionDO.setContractName(contractOrderDO.getContractName());
        userPositionDO.setContractId(contractOrderDO.getContractId());
        userPositionDO.setLever(contractOrderDO.getLever());
        return userPositionDO;
    }

    /**
     * 计算开仓均价   公式在设计文档5.3.2.7
     *
     * @param filledAmount 成交数量
     * @param filledPrice  成交价格
     * @return 开仓均价
     */
    public static BigDecimal computeAveragePrice(int orderDirection, int positionType, BigDecimal rate, BigDecimal oldPosition, BigDecimal oldAveragePrice,
                                                 BigDecimal filledAmount, BigDecimal filledPrice) {
        BigDecimal averagePrice = BigDecimal.ZERO;
        BigDecimal x = toDir(orderDirection);

        if (Objects.isNull(oldAveragePrice)) {
            BigDecimal temp = filledPrice.multiply(filledAmount);
            averagePrice = temp.add(
                    temp.multiply(rate)
                            .multiply(x))
                    .divide(filledAmount, scale, roundingMode);
            return averagePrice;
        }
        if (orderDirection == positionType) {
            //成交单和持仓是同方向
            BigDecimal newTotalAmount = oldPosition.add(filledAmount);
            BigDecimal oldTotalPrice = oldAveragePrice.multiply(oldPosition);
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
            averagePrice = oldTotalPrice.add(addedTotalPrice)
                    .add(addedTotalPrice.multiply(rate)
                            .multiply(x))
                    .divide(newTotalAmount, scale, roundingMode);
            return averagePrice;
        }

        //成交单和持仓是反方向 （平仓）
        if (filledAmount.compareTo(oldPosition) <= 0) {
            //不改变仓位方向
            BigDecimal newTotalAmount = oldPosition.subtract(filledAmount);
            if (newTotalAmount.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return oldAveragePrice;
        }
        //改变仓位方向
        BigDecimal newTotalAmount = filledAmount.subtract(oldPosition);

        BigDecimal temp = filledPrice.multiply(newTotalAmount);
        averagePrice = temp.add(
                temp.multiply(rate)
                        .multiply(x))
                .divide(newTotalAmount, scale, roundingMode);

        return averagePrice;
    }

    public static int toDirection(int positionType) {
        if (positionType == PositionTypeEnum.EMPTY.getCode()) {
            return -1;
        }
        return 1;
    }

    public static BigDecimal toDir(int positionType) {
        return new BigDecimal(toDirection(positionType));
    }

    public static BigDecimal computeSignAmount(BigDecimal amount, int positionType) {
        return amount.multiply(ContractUtils.toDir(positionType));
    }

    public static BigDecimal computeClosePL(BigDecimal rate, BigDecimal filledAmount, BigDecimal filledPrice,
                                            BigDecimal oldAmount, BigDecimal newAmount, BigDecimal oldOpenAveragePrice) {

        BigDecimal closeAmount = calCloseAmount(oldAmount, newAmount);
        //没有平仓，不用计算
        if (null == closeAmount) {
            return BigDecimal.ZERO;
        }
        //手续费
        BigDecimal actualFee = filledPrice.multiply(filledAmount).multiply(rate);
        BigDecimal dir = oldAmount.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ONE : NEG_ONE;
        //计算平仓盈亏
        // (filledPrice-oldOpenAveragePrice)*closeAmount*oldOpenPositionDirection - actualFee
        BigDecimal closePL = filledPrice.subtract(oldOpenAveragePrice)
                .multiply(closeAmount)
                .multiply(dir)
                .subtract(actualFee);
        return closePL;
    }

    /**
     * @return 计算平仓数量, 如果不是平仓，则为null
     */
    public static BigDecimal calCloseAmount(BigDecimal oldAmount, BigDecimal newAmount) {
        if (oldAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (newAmount.compareTo(BigDecimal.ZERO) == 0) {
            return oldAmount.abs();
        }
        //方向相反
        if (oldAmount.multiply(newAmount).compareTo(BigDecimal.ZERO) < 0) {
            return oldAmount;
        }
        //方向相同,如果数量增加，则没有平仓
        if (oldAmount.abs().compareTo(newAmount.abs()) < 0) {
            return null;
        }
        return oldAmount.abs().subtract(newAmount.abs());
    }
}
