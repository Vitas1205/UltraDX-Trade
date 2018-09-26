package com.fota.trade.mapper;

import com.fota.trade.domain.UserFeeRateDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/9/17 17:16
 * @Modified:
 */
@Mapper
public interface UserFeeRateMapper {

    int insert(UserFeeRateDO userFeeRateDO);

    UserFeeRateDO getByLevel(Integer level);
}
