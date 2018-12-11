package com.fota.trade.mapper;

import com.fota.trade.domain.UserPositionDO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
        "locked_amount, unfilled_amount, average_price, real_average_price,",
        "position_type, status, lever, fee_rate)",
        "values (#{id,jdbcType=BIGINT}, now(), ",
        "now(), #{userId,jdbcType=BIGINT}, ",
        "#{contractId,jdbcType=INTEGER}, #{contractName,jdbcType=VARCHAR}, ",
        "#{lockedAmount,jdbcType=DECIMAL}, #{unfilledAmount,jdbcType=DECIMAL}, #{averagePrice,jdbcType=DECIMAL}, #{realAveragePrice}, ",
        "#{positionType,jdbcType=INTEGER}, #{status,jdbcType=INTEGER}, #{lever,jdbcType=INTEGER}, #{feeRate, jdbcType=DECIMAL})"
    })
    int insert(UserPositionDO record);

    int insertSelective(UserPositionDO record);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, locked_amount, ",
            "unfilled_amount, position_type, average_price, status, lever, fee_rate",
            "from trade_user_position",
            "where id = #{id,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    UserPositionDO selectByPrimaryKey(Long id);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, locked_amount, ",
            "unfilled_amount, position_type, average_price, real_average_price, status, lever, fee_rate",
            "from trade_user_position",
            "where contract_id = #{contractId,jdbcType=BIGINT} and user_id = #{userId,jdbcType=BIGINT} and unfilled_amount > 0"
    })
    @ResultMap("BaseResultMap")
    UserPositionDO selectByUserIdAndId(@Param("userId") Long userId, @Param("contractId") Long contractId);


    List<UserPositionDO> selectByContractIdAndUserIds(@Param("userIds") List<Long> userIds, @Param("contractId") Long contractId);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, locked_amount, ",
            "unfilled_amount, position_type, average_price, real_average_price, status, lever, fee_rate",
            "from trade_user_position",
            "where contract_id = #{contractId,jdbcType=BIGINT} and user_id = #{userId,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    UserPositionDO selectByUserIdAndContractId(@Param("userId") Long userId, @Param("contractId") Long contractId);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, locked_amount, ",
            "unfilled_amount, position_type, average_price, status, lever, fee_rate",
            "from trade_user_position",
            "where contract_id = #{contractId,jdbcType=BIGINT} and user_id = #{userId,jdbcType=BIGINT} for update"
    })
    @ResultMap("BaseResultMap")
    UserPositionDO selectForUpdateByUserId(@Param("userId") Long userId, @Param("contractId") Long contractId);

    List<UserPositionDO> selectByUserId(@Param("userId") Long userId, @Param("status") Integer status);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, locked_amount, ",
            "unfilled_amount, position_type, average_price, real_average_price, status,lever, fee_rate",
            "from trade_user_position",
            "where  contract_id = #{contractId,jdbcType=BIGINT} and status = #{status} and unfilled_amount > 0"
    })
    @ResultMap("BaseResultMap")
    List<UserPositionDO> selectByContractId(@Param("contractId") Long contractId, @Param("status") Integer status);




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

    @Update({
            "update trade_user_position",
            "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
            "gmt_modified = now(),",
            "user_id = #{userId,jdbcType=BIGINT},",
            "contract_id = #{contractId,jdbcType=INTEGER},",
            "contract_name = #{contractName,jdbcType=VARCHAR},",
            "locked_amount = #{lockedAmount,jdbcType=DECIMAL},",
            "unfilled_amount = #{unfilledAmount,jdbcType=DECIMAL},",
            "position_type = #{positionType,jdbcType=INTEGER},",
            "average_price = #{averagePrice, jdbcType=DECIMAL},",
            "status = #{status,jdbcType=INTEGER}",
            "where id = #{id,jdbcType=BIGINT} and user_id = #{userId} and gmt_modified = #{gmtModified,jdbcType=TIMESTAMP}"
    })
    int updateByOpLock(UserPositionDO record);


    @Update({
            "update trade_user_position",
            "set gmt_modified = now(),",
            "position_type = #{item.positionType},",
            "unfilled_amount = #{item.unfilledAmount},",
            "real_average_price = #{item.realAveragePrice}, ",
            "average_price = #{item.averagePrice} ",
            " where id = #{item.id} and position_type=#{oldPositionType} and unfilled_amount=#{oldUnfilledAmount} " +
                    " and average_price=#{oldAveragePrice}"})
    int updatePositionById(@Param("item") UserPositionDO userPositionDO, @Param("oldPositionType") Integer oldPositionType,
                           @Param("oldUnfilledAmount") BigDecimal oldUnfilledAmount,
                           @Param("oldAveragePrice") BigDecimal oldAveragePrice);

    int countByQuery(Map<String, Object> param);

    List<UserPositionDO> listByQuery(Map<String, Object> param);

    BigDecimal countTotalPosition(@Param("contractId")Long contractId);
}