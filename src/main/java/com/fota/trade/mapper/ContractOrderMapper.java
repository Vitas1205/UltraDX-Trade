package com.fota.trade.mapper;

import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.mapper.common.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.math.BigInteger;
import java.util.List;

@Mapper
public interface ContractOrderMapper extends BaseMapper<ContractOrderDO> {

    @Delete({
        "delete from trade_contract_order",
        "where id = #{id,jdbcType=BIGINT}"
    })
    int deleteByPrimaryKey(Long id);


    @Insert({
        "insert into trade_contract_order (id, gmt_create, ",
        "gmt_modified, user_id, ",
        "contract_id, contract_name, ",
        "order_direction, operate_type, ",
        "operate_direction, lever, ",
        "total_amount, unfilled_amount, ",
        "price, fee, usdk_locked_amount, ",
        "position_locked_amount, status)",
        "values (#{id,jdbcType=BIGINT}, #{gmtCreate,jdbcType=TIMESTAMP}, ",
        "#{gmtModified,jdbcType=TIMESTAMP}, #{userId,jdbcType=BIGINT}, ",
        "#{contractId,jdbcType=INTEGER}, #{contractName,jdbcType=VARCHAR}, ",
        "#{orderDirection,jdbcType=TINYINT}, #{operateType,jdbcType=TINYINT}, ",
        "#{operateDirection,jdbcType=TINYINT}, #{lever,jdbcType=INTEGER}, ",
        "#{totalAmount,jdbcType=DECIMAL}, #{unfilledAmount,jdbcType=DECIMAL}, ",
        "#{price,jdbcType=DECIMAL}, #{fee,jdbcType=DECIMAL}, #{usdkLockedAmount,jdbcType=DECIMAL}, ",
        "#{positionLockedAmount,jdbcType=DECIMAL}, #{status,jdbcType=INTEGER})"
    })
    int insert(ContractOrderDO record);


    int insertSelective(ContractOrderDO record);


    @Select({
        "select",
        "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, order_direction, ",
        "operate_type, operate_direction, lever, total_amount, unfilled_amount, price, ",
        "fee, usdk_locked_amount, position_locked_amount, status",
        "from trade_contract_order",
        "where id = #{id,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    ContractOrderDO selectByPrimaryKey(Long id);


    int updateByPrimaryKeySelective(ContractOrderDO record);


    @Update({
        "update trade_contract_order",
        "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
          "gmt_modified = #{gmtModified,jdbcType=TIMESTAMP},",
          "user_id = #{userId,jdbcType=BIGINT},",
          "contract_id = #{contractId,jdbcType=INTEGER},",
          "contract_name = #{contractName,jdbcType=VARCHAR},",
          "order_direction = #{orderDirection,jdbcType=TINYINT},",
          "operate_type = #{operateType,jdbcType=TINYINT},",
          "operate_direction = #{operateDirection,jdbcType=TINYINT},",
          "lever = #{lever,jdbcType=INTEGER},",
          "total_amount = #{totalAmount,jdbcType=DECIMAL},",
          "unfilled_amount = #{unfilledAmount,jdbcType=DECIMAL},",
          "price = #{price,jdbcType=DECIMAL},",
          "fee = #{fee,jdbcType=DECIMAL},",
          "usdk_locked_amount = #{usdkLockedAmount,jdbcType=DECIMAL},",
          "position_locked_amount = #{positionLockedAmount,jdbcType=DECIMAL},",
          "status = #{status,jdbcType=INTEGER}",
        "where id = #{id,jdbcType=BIGINT}"
    })
    int updateByPrimaryKey(ContractOrderDO record);

    List<ContractOrderDO> notMatchOrderList(
            @Param("placeOrder") Integer placeOrder, @Param("partialSuccess") Integer partialSuccess,
            @Param("contractOrderIndex") BigInteger contractOrderIndex, @Param("orderDirection") Integer orderDirection);
}