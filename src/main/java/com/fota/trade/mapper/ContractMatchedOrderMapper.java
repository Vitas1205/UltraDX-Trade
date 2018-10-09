package com.fota.trade.mapper;

import com.fota.trade.domain.BaseQuery;
import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.ContractMatchedOrderDTO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/31 19:32
 * @Modified:
 */
@Mapper
public interface ContractMatchedOrderMapper {
    int insert(ContractMatchedOrderDO record);

    int countByUserId(@Param("userId") Long userId, @Param("contractIds") List<Long> contractIds, @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    List<ContractMatchedOrderDO> listByUserId(@Param("userId") Long userId, @Param("contractIds") List<Long> contractIds, @Param("startRow") Integer startRow, @Param("endRow") Integer endRow,
                                            @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    long count(BaseQuery query);

    List<ContractMatchedOrderDO> queryMatchedOrder(BaseQuery baseQuery);

    BigDecimal getAllFee(Map<String, Object> map);

    @Update("UPDATE contract_matched_order SET gmt_modified=now(), status=#{toStatus} WHERE id=#{id}")
    int updateStatus(@Param("id") long id, @Param("toStatus") int toStatus);

    Long getLatestContractMatched();

    List<ContractMatchedOrderDO> getLatestContractMatchedList(@Param("contractId") Long contractId , @Param("id") Long id );
}
