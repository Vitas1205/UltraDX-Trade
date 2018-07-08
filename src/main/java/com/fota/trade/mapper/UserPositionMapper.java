package com.fota.trade.mapper;

import com.fota.client.domain.query.UserPositionQuery;
import com.fota.trade.domain.UserPositionDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserPositionMapper {

    @Delete({
        "delete from trade_user_position",
        "where user_id = #{userId,jdbcType=BIGINT}"
    })
    int deleteByUserId(Long userId);


    @Insert({
        "insert into trade_user_position (id, gmt_create, ",
        "gmt_modified, user_id, ",
        "contract_id, contract_name, ",
        "locked_amount, unfilled_amount, average_price,",
        "position_type, status)",
        "values (#{id,jdbcType=BIGINT}, now(), ",
        "now(), #{userId,jdbcType=BIGINT}, ",
        "#{contractId,jdbcType=INTEGER}, #{contractName,jdbcType=VARCHAR}, ",
        "#{lockedAmount,jdbcType=DECIMAL}, #{unfilledAmount,jdbcType=DECIMAL}, #{averagePrice,jdbcType=DECIMAL}, ",
        "#{positionType,jdbcType=INTEGER}, #{status,jdbcType=INTEGER})"
    })
    int insert(UserPositionDO record);

    int insertSelective(UserPositionDO record);

    @Select({
        "select",
        "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, locked_amount, ",
        "unfilled_amount, position_type, status",
        "from trade_user_position",
        "where id = #{id,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    UserPositionDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(UserPositionDO record);

    @Update({
        "update trade_user_position",
        "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
          "gmt_modified = #{gmtModified,jdbcType=TIMESTAMP},",
          "user_id = #{userId,jdbcType=BIGINT},",
          "contract_id = #{contractId,jdbcType=INTEGER},",
          "contract_name = #{contractName,jdbcType=VARCHAR},",
          "locked_amount = #{lockedAmount,jdbcType=DECIMAL},",
          "unfilled_amount = #{unfilledAmount,jdbcType=DECIMAL},",
          "position_type = #{positionType,jdbcType=INTEGER},",
          "status = #{status,jdbcType=INTEGER}",
        "where id = #{id,jdbcType=BIGINT}"
    })
    int updateByPrimaryKey(UserPositionDO record);


    int countByQuery(UserPositionQuery userPositionQuery);

    List<UserPositionDO> listByQuery(UserPositionQuery userPositionQuery);
}