package com.fota.trade.mapper;

import com.fota.asset.domain.CapitalAccountAddAmountDTO;
import com.fota.trade.domain.UserCapitalDO;
import com.fota.trade.mapper.common.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @author Gavin Shen
 * @Date 2019/1/21
 */
@Mapper
public interface UserCapitalMapper extends BaseMapper<UserCapitalDO> {


    UserCapitalDO getCapitalByAssetId(@Param("userId") Long userId, @Param("assetId") Integer assetId);

    List<UserCapitalDO> listByQuery(Map<String ,Object> param);

    int addCapitalAmount(CapitalAccountAddAmountDTO capitalAccountAddAmountDTO);

}
