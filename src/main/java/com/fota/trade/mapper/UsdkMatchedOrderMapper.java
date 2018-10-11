package com.fota.trade.mapper;

import com.fota.trade.domain.UsdkMatchedOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

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
    int insert(UsdkMatchedOrderDO record);

    int countByUserId(@Param("userId") Long userId, @Param("assetIds") List<Long> assetIds, @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    List<UsdkMatchedOrderDO> listByUserId(@Param("userId") Long userId, @Param("assetIds") List<Long> assetIds, @Param("startRow") Integer startRow, @Param("endRow") Integer endRow,
                                          @Param("startTime") Date startTime, @Param("endTime") Date endTime);
    Long getLatestUsdkMatched();

    List<UsdkMatchedOrderDO> getLatestUsdkMatchedList(@Param("assetId") Integer assetId, @Param("id") Long id);
}
