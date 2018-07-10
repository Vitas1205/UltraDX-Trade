package com.fota.client.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @Author huangtao 2018/7/5 下午2:58
 * @Description 合约交易成交（撮合）记录，往MQ和Redis推送
 */
@Data
public class ContractMatchedOrderDTO {

    private Long id;

    /**
     * 卖单id
     */
    private Long askOrderId;

    /**
     * 卖单合约委托价格
     */
    private BigDecimal askOrderPrice;

    /**
     * 买单id
     */
    private Long bidOrderId;

    /**
     * 买单合约委托价格
     */
    private BigDecimal bidOrderPrice;

    /**
     * 撮合价格
     */
    private BigDecimal filledPrice;

    /**
     * 撮合量
     */
    private BigDecimal filledAmount;

    /**
     * 合约类型名称
     */
    private String contractName;

    /**
     * 成交时间
     */
    private Date gmtCreate;

    /**
     * 1-买家主动（先）,2-卖家主动
     */
    private Integer matchType;
    /**
     * 标的物名称 trade_contract_category.asset_name
     */
    private String assetName;

    /**
     * 合约类型：1-周、2-月、3-季
     * trade_contract_category.contract_type
     */
    private Integer contractType;

}
