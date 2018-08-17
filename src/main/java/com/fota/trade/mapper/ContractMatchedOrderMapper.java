package com.fota.trade.mapper;

import com.fota.trade.domain.BaseQuery;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.ContractMatchedOrderDTO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/31 19:32
 * @Modified:
 */
@Mapper
public interface ContractMatchedOrderMapper {
    int insert(ContractMatchedOrderDO record);

    long count(BaseQuery query);

    List<ContractMatchedOrderDO> queryMatchedOrder(BaseQuery baseQuery);

    BigDecimal getAllFee(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Update("UPDATE contract_matched_order SET status=#{status} WHERE id=#{id}")
    int updateStatus(int id, int status);
}
