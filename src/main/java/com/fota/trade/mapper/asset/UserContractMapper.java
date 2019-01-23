package com.fota.trade.mapper.asset;

import com.fota.trade.domain.UserContractDO;
import com.fota.trade.mapper.common.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * @author Gavin Shen
 * @Date 2019/1/21
 */
@Mapper
public interface UserContractMapper extends BaseMapper<UserContractDO> {

    UserContractDO getByUserId(Long userId);

    int updateBalanceUnlimit(@Param("userId")Long userId, @Param("addAmount")BigDecimal addAmount);

}
