package com.fota.trade.util;

import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.PositionTypeEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;

/**
 * Created by Swifree on 2018/8/22.
 * Code is the law
 */
public class ContractUtils {
    private static int scale = 16;
    private static int roundingMode = BigDecimal.ROUND_HALF_UP;

    public static UserPositionDO buildPosition(ContractOrderDO contractOrderDO, BigDecimal contractSize, int lever, long filledAmount, BigDecimal filledPrice) {
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
        userPositionDO.setAveragePrice(computeAveragePrice(contractOrderDO, null, filledPrice, filledAmount, contractSize));
        userPositionDO.setUnfilledAmount(filledAmount);
        userPositionDO.setStatus(1);
        userPositionDO.setUserId(contractOrderDO.getUserId());
        userPositionDO.setContractName(contractOrderDO.getContractName());
        userPositionDO.setContractId(contractOrderDO.getContractId());
        userPositionDO.setContractSize(contractSize);
        userPositionDO.setLever(lever);
        return userPositionDO;
    }

    /**
     * 计算开仓均价   公式在设计文档5.3.2.7
     *
     * @param contractOrderDO  委托单
     * @param userPositionDO   现有持仓
     * @param filledAmountLong 成交价格
     * @param filledPrice      成交数量
     * @param contractSize     合约大小
     * @return 开仓均价
     */
    public static BigDecimal computeAveragePrice(ContractOrderDO contractOrderDO, UserPositionDO userPositionDO,
                                                 BigDecimal filledPrice, long filledAmountLong, BigDecimal contractSize) {
        BigDecimal x = contractOrderDO.getOrderDirection() == ASK.getCode() ? BigDecimal.valueOf(-1) : BigDecimal.ONE;
        BigDecimal averagePrice = BigDecimal.ZERO;
        BigDecimal filledAmount = BigDecimal.valueOf(filledAmountLong);
        if (Objects.isNull(userPositionDO)) {
            BigDecimal temp = filledPrice.multiply(filledAmount);
            averagePrice = temp.add(
                    temp.multiply(contractSize)
                            .multiply(contractOrderDO.getFee())
                            .multiply(x))
                    .divide(filledAmount, scale, roundingMode);
        } else {
            if (Objects.equals(contractOrderDO.getOrderDirection(), userPositionDO.getPositionType())) {
                //成交单和持仓是同方向
                long newTotalAmount = userPositionDO.getUnfilledAmount() + filledAmount.longValue();
                BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
                BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
                averagePrice = oldTotalPrice.add(addedTotalPrice)
                        .add(addedTotalPrice.multiply(contractSize)
                                .multiply(contractOrderDO.getFee())
                                .multiply(x))
                        .divide(BigDecimal.valueOf(newTotalAmount), scale, roundingMode);
            } else {
                //成交单和持仓是反方向 （平仓）
                if (filledAmount.longValue() - userPositionDO.getUnfilledAmount() <= 0) {
                    //不改变仓位方向
                    long newTotalAmount = userPositionDO.getUnfilledAmount() - filledAmount.longValue();
                    if (newTotalAmount != 0) {
                        averagePrice = userPositionDO.getAveragePrice()
                                .setScale(scale, roundingMode);
                    }
                } else {
                    //改变仓位方向
                    long newTotalAmount = filledAmount.longValue() - userPositionDO.getUnfilledAmount();

                    BigDecimal temp = filledPrice.multiply(BigDecimal.valueOf(newTotalAmount));
                    averagePrice = temp.add(
                            temp.multiply(contractSize)
                                    .multiply(contractOrderDO.getFee())
                                    .multiply(x))
                            .divide(BigDecimal.valueOf(newTotalAmount), scale, roundingMode);
                }
            }
        }

        return averagePrice;
    }

    /**
     * 计算开仓均价   公式在设计文档5.3.2.7
     *
     * @param orderDirection    委托方向
     * @param fee               手续费率
     * @param positionDirection 持仓方向
     * @param position          持仓数量
     * @param openPrice         开仓均价
     * @param filledPrice       成交价格
     * @param filledAmountLong  成交数量
     * @param contractSize      合约大小
     * @return 成交后开仓均价
     */
    public static BigDecimal computeOpenPrice(int orderDirection, BigDecimal fee, int positionDirection, long position, BigDecimal openPrice,
                                              BigDecimal filledPrice, long filledAmountLong, BigDecimal contractSize) {
        BigDecimal x = orderDirection == ASK.getCode() ? BigDecimal.valueOf(-1) : BigDecimal.ONE;
        BigDecimal averagePrice = BigDecimal.ZERO;
        BigDecimal filledAmount = BigDecimal.valueOf(filledAmountLong);
        if (0 == position) {
            BigDecimal temp = filledPrice.multiply(filledAmount);
            averagePrice = temp.add(
                    temp.multiply(contractSize)
                            .multiply(fee)
                            .multiply(x))
                    .divide(filledAmount, scale, roundingMode);
        } else {
            if (orderDirection == positionDirection) {
                //成交单和持仓是同方向
                long newTotalAmount = position + filledAmount.longValue();
                BigDecimal oldTotalPrice = openPrice.multiply(new BigDecimal(position));
                BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
                averagePrice = oldTotalPrice.add(addedTotalPrice)
                        .add(addedTotalPrice.multiply(contractSize)
                                .multiply(fee)
                                .multiply(x))
                        .divide(BigDecimal.valueOf(newTotalAmount), scale, roundingMode);
            } else {
                //成交单和持仓是反方向 （平仓）
                if (filledAmount.longValue() - position <= 0) {
                    //不改变仓位方向
                    long newTotalAmount = position - filledAmount.longValue();
                    if (newTotalAmount != 0) {
                        averagePrice = openPrice;
                    }
                } else {
                    //改变仓位方向
                    long newTotalAmount = filledAmount.longValue() - position;
                    BigDecimal temp = filledPrice.multiply(BigDecimal.valueOf(newTotalAmount));
                    averagePrice = temp.add(
                            temp.multiply(contractSize)
                                    .multiply(fee)
                                    .multiply(x))
                            .divide(BigDecimal.valueOf(newTotalAmount), scale, roundingMode);
                }
            }
        }

        return averagePrice;
    }

    /**
     * 计算回滚后开仓均价
     *
     * @param orderType
     * @param rate
     * @param curPositionType
     * @param curPosition
     * @param curOpenPrice
     * @param filledPrice
     * @param filledAmount
     * @param contractSize
     * @return
     */
    public static BigDecimal rollbackOpenPrice(int orderType, BigDecimal rate, int curPositionType, BigDecimal curPosition, BigDecimal curOpenPrice,
                                               BigDecimal filledPrice, BigDecimal filledAmount, BigDecimal contractSize) {

        BigDecimal orderDir = BigDecimal.valueOf(toDirection(orderType));
        BigDecimal signedOriginPosition =  curPosition.multiply(new BigDecimal(toDirection(curPositionType)))
                .subtract(filledAmount.multiply(orderDir));

        BigDecimal originPositionDir = signedOriginPosition.longValue() > 0 ? BigDecimal.ONE: BigDecimal.ONE.negate();

        BigDecimal originPosition = signedOriginPosition.abs();

        //手续费
        BigDecimal fee = filledPrice.multiply(filledAmount).multiply(contractSize).multiply(rate);

        //委托和原来持仓方向相同 (curOpenPrice * curPosition - fee)/originPosition
        if (orderDir.equals(originPositionDir)) {
            return curOpenPrice.multiply(curPosition).subtract(fee).divide(originPosition, scale, roundingMode);
        }
        //不改变仓位方向
        if (filledAmount.longValue() < originPosition.longValue()) {
            return curOpenPrice;
        }
        //改变仓位方向
        return filledPrice;

    }

    public static int toDirection(int positionType) {
        if (positionType == PositionTypeEnum.EMPTY.getCode()) {
            return -1;
        }
        return 1;
    }

}
