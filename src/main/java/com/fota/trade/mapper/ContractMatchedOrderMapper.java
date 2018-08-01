package com.fota.trade.mapper;

import com.fota.trade.domain.ContractMatchedOrderDO;
import com.fota.trade.domain.ContractMatchedOrderDTO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/31 19:32
 * @Modified:
 */
@Mapper
public interface ContractMatchedOrderMapper {
    int insert(ContractMatchedOrderDO record);
}
