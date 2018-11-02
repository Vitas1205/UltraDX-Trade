package com.fota.trade.client;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Created by Swifree on 2018/9/22.
 * Code is the law
 */
@Data
@Accessors(chain = true)
public class PostDealMessage {

    private String msgKey;


    //================委托信息==========
    private long subjectId;
    private long userId;

    /**
     * 委托方向
     */
    private int orderDirection;

    /**
     * 费率
     */
    private BigDecimal feeRate;

    /**
     * 杠杆，建仓时需要
     */
    private Integer lever;

    /**
     * 合约名称,建仓时需要
     */
    private String contractName;

    //================成交信息===========
    private long matchId;
    private BigDecimal filledAmount;
    private BigDecimal filledPrice;

    //==============成交记录需要
    private long orderId;
    private Integer matchType;
    private BigDecimal price;
    private Long matchUserId;
    private Integer closeType;



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostDealMessage that = (PostDealMessage) o;
        return Objects.equals(msgKey, that.msgKey);
    }
    @Override
    public int hashCode(){
        return this.msgKey.hashCode();
    }
    public String getGroup(){
        return userId + "_" + subjectId;
    }

    public PostDealMessage(long contractId, long userId, int orderDirection, BigDecimal feeRate, Integer lever,  String contractName) {
        this.subjectId = contractId;
        this.userId = userId;
        this.orderDirection = orderDirection;
        this.lever = lever;
        this.feeRate = feeRate;
        this.contractName = contractName;
    }

    public PostDealMessage(long contractId, long userId, int orderDirection, BigDecimal feeRate) {
        this.subjectId = contractId;
        this.userId = userId;
        this.orderDirection = orderDirection;
        this.feeRate = feeRate;
    }

    public PostDealMessage() {
    }


    public BigDecimal getTotalFee() {
        return filledAmount.multiply(filledPrice)
                .multiply(feeRate);
    }
}
