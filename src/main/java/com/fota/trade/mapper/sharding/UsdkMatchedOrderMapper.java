package com.fota.trade.mapper.sharding;

import com.fota.trade.domain.UsdkMatchedOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/31 21:15
 * @Modified:
 */
@Mapper
public interface UsdkMatchedOrderMapper {
    int insert(List<UsdkMatchedOrderDO> list);

    int countByUserId(@Param("userId") Long userId, @Param("assetIds") List<Long> assetIds, @Param("startTime") Long startTime, @Param("endTime") Long endTime);

    List<UsdkMatchedOrderDO> listByUserId(@Param("userId") Long userId, @Param("assetIds") List<Long> assetIds, @Param("startRow") Integer startRow, @Param("endRow") Integer endRow,
                                          @Param("startTime") Long startTime, @Param("endTime") Long endTime);
}
