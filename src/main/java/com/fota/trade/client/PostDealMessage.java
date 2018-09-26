package com.fota.trade.client;

import com.fota.trade.domain.ContractOrderDO;
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
    private long matchId;
    private BigDecimal filledAmount;
    private BigDecimal filledPrice;
    private ContractOrderDO contractOrderDO;
    private String msgKey;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostDealMessage that = (PostDealMessage) o;
        return Objects.equals(msgKey, that.msgKey);
    }
    public String getGroup(){
        return contractOrderDO.getUserId() + "_" + contractOrderDO.getContractId();
    }
}
