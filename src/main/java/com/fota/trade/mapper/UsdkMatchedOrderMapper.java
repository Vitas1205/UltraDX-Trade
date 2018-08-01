package com.fota.trade.mapper;

import com.fota.trade.domain.UsdkMatchedOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/31 21:15
 * @Modified:
 */
@Mapper
public interface UsdkMatchedOrderMapper {
    int insert(UsdkMatchedOrderDO record);
}
