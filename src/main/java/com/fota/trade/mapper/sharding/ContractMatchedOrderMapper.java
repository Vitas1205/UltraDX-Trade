package com.fota.trade.mapper.sharding;

import com.fota.trade.domain.ContractMatchedOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
    int insert(List<ContractMatchedOrderDO> record);

    int countByUserId(@Param("userId") Long userId, @Param("contractIds") List<Long> contractIds, @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    List<ContractMatchedOrderDO> listByUserId(@Param("userId") Long userId, @Param("contractIds") List<Long> contractIds, @Param("startRow") Integer startRow, @Param("endRow") Integer endRow,
                                            @Param("startTime") Date startTime, @Param("endTime") Date endTime);

}
