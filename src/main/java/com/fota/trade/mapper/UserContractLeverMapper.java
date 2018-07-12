package com.fota.trade.mapper;

import com.fota.trade.domain.UserContractLeverDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/12
 */
@Mapper
public interface UserContractLeverMapper {


    @Insert({
            "insert into trade_user_lever (id, gmt_create, ",
            "gmt_modified, user_id, ",
            "asset_id, asset_name, ",
            "lever)",
            "values (#{id,jdbcType=BIGINT}, now(), ",
            "now(), #{userId,jdbcType=BIGINT}, ",
            "#{assetId,jdbcType=INTEGER}, #{assetName,jdbcType=VARCHAR}, ",
            "#{lever,jdbcType=TINYINT})"
    })
    int insert(UserContractLeverDO record);

    @Select({
            "select ",
            "id, gmt_create, gmt_modified, user_id, asset_id, asset_name, lever",
            "from trade_user_lever",
            "where  user_id = #{userId,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    List<UserContractLeverDO> listUserContractLever(Long userId);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, asset_id, asset_name, lever",
            "from trade_user_lever",
            "where  user_id = #{userId,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    UserContractLeverDO selectUserContractLever(@Param("userId")long userId, @Param("assetId")int assetId);

    @Update({
            "update trade_user_lever",
            "gmt_modified = now(),",
            "lever = #{lever,jdbcType=INTEGER},",
            "where user_id = #{id,jdbcType=BIGINT} and asset_id = #{assetId,jdbcType=INTEGER}"
    })
    int update(UserContractLeverDO userContractLeverDO);

}
