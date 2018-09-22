package com.fota.trade.mapper;

import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.mapper.common.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContractOrderMapper extends BaseMapper<ContractOrderDO> {

    @Delete({
        "delete from trade_contract_order",
        "where id = #{id,jdbcType=BIGINT}"
    })
    int deleteByPrimaryKey(Long id);


    @Override
    @Insert({
            "insert into trade_contract_order (id, gmt_create, ",
            "gmt_modified, user_id, ",
            "contract_id, contract_name, ",
            "order_direction, ",
            "total_amount, unfilled_amount, ",
            "price, fee, ",
            "status, average_price, order_type, close_type, order_context)",
            "values (#{id}, #{gmtCreate}, ",
            "#{gmtModified}, #{userId}, ",
            "#{contractId,jdbcType=INTEGER}, #{contractName,jdbcType=VARCHAR}, ",
            "#{orderDirection,jdbcType=TINYINT}, ",
            "#{totalAmount,jdbcType=BIGINT}, #{unfilledAmount,jdbcType=BIGINT}, ",
            "#{price,jdbcType=DECIMAL}, #{fee,jdbcType=DECIMAL},",
            " #{status}, #{averagePrice,jdbcType=DECIMAL}, #{orderType}, #{closeType}, #{orderContext,jdbcType=VARCHAR})"
    })
    int insert(ContractOrderDO record);


    int insertSelective(ContractOrderDO record);


    @Select({
        "select",
        "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, order_direction, ",
        "operate_type, operate_direction, lever, total_amount, unfilled_amount, price, ",
        "fee, usdk_locked_amount, position_locked_amount, status, order_type, close_type, average_price, order_context",
        "from trade_contract_order",
        "where id = #{id,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    ContractOrderDO selectByPrimaryKey(Long id);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, order_direction, ",
            "operate_type, order_type, operate_direction, lever, total_amount, unfilled_amount, close_type, price, ",
            "fee, usdk_locked_amount, position_locked_amount, status, average_price, order_context",
            "from trade_contract_order",
            "where id = #{id,jdbcType=BIGINT} and user_id =  #{userId,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    ContractOrderDO selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);


    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, order_direction, ",
            "operate_type,order_type,close_type, operate_direction, lever, total_amount, unfilled_amount, price, ",
            "fee, usdk_locked_amount, position_locked_amount, status, average_price, order_context",
            "from trade_contract_order",
            "where contract_id = #{contractId,jdbcType=BIGINT} and user_id =  #{userId,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    List<ContractOrderDO> selectByContractIdAndUserId(@Param("contractId") Long contractId, @Param("userId") Long userId);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, order_direction, ",
            "operate_type,order_type,close_type, operate_direction, lever, total_amount, unfilled_amount, price, ",
            "fee, usdk_locked_amount, position_locked_amount, status, average_price, order_context",
            "from trade_contract_order",
            "where user_id =  #{userId,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    List<ContractOrderDO> selectByUserId(Long userId);


    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, order_direction, ",
            "operate_type,order_type,close_type, operate_direction, lever, total_amount, unfilled_amount, price, ",
            "fee, usdk_locked_amount, position_locked_amount, status, average_price, order_context",
            "from trade_contract_order",
            "where user_id =  #{userId,jdbcType=BIGINT} and status in (8,9)"
    })
    @ResultMap("BaseResultMap")
    List<ContractOrderDO> selectUnfinishedOrderByUserId(Long userId);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, order_direction, ",
            "operate_type,order_type,close_type, operate_direction, lever, total_amount, unfilled_amount, price, ",
            "fee, usdk_locked_amount, position_locked_amount, status, average_price, order_context",
            "from trade_contract_order",
            "where user_id =  #{userId,jdbcType=BIGINT} and status in (8,9) and order_type != 3"
    })
    @ResultMap("BaseResultMap")
    List<ContractOrderDO> selectNotEnforceOrderByUserId(Long userId);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, order_direction, ",
            "operate_type,order_type,close_type, operate_direction, lever, total_amount, unfilled_amount, price, ",
            "fee, usdk_locked_amount, position_locked_amount, status, average_price, order_context",
            "from trade_contract_order",
            "where contract_id = #{contractId,jdbcType=BIGINT} and status in (8,9)"
    })
    @ResultMap("BaseResultMap")
    List<ContractOrderDO> selectUnfinishedOrderByContractId(Long contractId);


    int updateByPrimaryKeySelective(ContractOrderDO record);


    @Update({
        "update trade_contract_order",
        "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
          "gmt_modified = now(),",
          "user_id = #{userId,jdbcType=BIGINT},",
          "contract_id = #{contractId,jdbcType=INTEGER},",
          "contract_name = #{contractName,jdbcType=VARCHAR},",
          "order_direction = #{orderDirection,jdbcType=TINYINT},",
          "operate_type = #{operateType,jdbcType=TINYINT},",
          "operate_direction = #{operateDirection,jdbcType=TINYINT},",
          "lever = #{lever,jdbcType=INTEGER},",
          "total_amount = #{totalAmount,jdbcType=BIGINT},",
          "unfilled_amount = #{unfilledAmount,jdbcType=BIGINT},",
          "price = #{price,jdbcType=DECIMAL},",
          "fee = #{fee,jdbcType=DECIMAL},",
          "usdk_locked_amount = #{usdkLockedAmount,jdbcType=DECIMAL},",
          "position_locked_amount = #{positionLockedAmount,jdbcType=DECIMAL},",
          "status = #{status,jdbcType=INTEGER}",
          "average_price = #{averagePrice,jdbcType=DECIMAL}",
        "where id = #{id,jdbcType=BIGINT}"
    })
    int updateByPrimaryKey(ContractOrderDO record);

    @Update({
            "update trade_contract_order",
            "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
            "gmt_modified = now(),",
            "user_id = #{userId,jdbcType=BIGINT},",
            "contract_id = #{contractId,jdbcType=INTEGER},",
            "contract_name = #{contractName,jdbcType=VARCHAR},",
            "order_direction = #{orderDirection,jdbcType=TINYINT},",
            "operate_type = #{operateType,jdbcType=TINYINT},",
            "operate_direction = #{operateDirection,jdbcType=TINYINT},",
            "lever = #{lever,jdbcType=INTEGER},",
            "total_amount = #{totalAmount,jdbcType=BIGINT},",
            "unfilled_amount = #{unfilledAmount,jdbcType=BIGINT},",
            "price = #{price,jdbcType=DECIMAL},",
            "fee = #{fee,jdbcType=DECIMAL},",
            "usdk_locked_amount = #{usdkLockedAmount,jdbcType=DECIMAL},",
            "position_locked_amount = #{positionLockedAmount,jdbcType=DECIMAL},",
            "status = #{status,jdbcType=INTEGER}",
            "average_price = #{averagePrice,jdbcType=DECIMAL}",
            "where id = #{id,jdbcType=BIGINT} and gmt_modified = #{gmtModified}"
    })
    int updateByPrimaryKeyAndOpLock(ContractOrderDO record);

    @Update({
            "update trade_contract_order",
            "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
            "gmt_modified = now(),",
            "user_id = #{userId,jdbcType=BIGINT},",
            "contract_id = #{contractId,jdbcType=BIGINT},",
            "contract_name = #{contractName,jdbcType=VARCHAR},",
            "order_direction = #{orderDirection,jdbcType=TINYINT},",
            "operate_type = #{operateType,jdbcType=TINYINT},",
            "order_type = #{orderType,jdbcType=TINYINT},",
            "operate_direction = #{operateDirection,jdbcType=TINYINT},",
            "lever = #{lever,jdbcType=INTEGER},",
            "total_amount = #{totalAmount,jdbcType=BIGINT},",
            "unfilled_amount = #{unfilledAmount,jdbcType=BIGINT},",
            "price = #{price,jdbcType=DECIMAL},",
            "fee = #{fee,jdbcType=DECIMAL},",
            "usdk_locked_amount = #{usdkLockedAmount,jdbcType=DECIMAL},",
            "position_locked_amount = #{positionLockedAmount,jdbcType=DECIMAL},",
            "status = #{status,jdbcType=INTEGER}",
            "where id = #{id,jdbcType=BIGINT} and user_id = #{userId,jdbcType=BIGINT} and gmt_modified = #{gmtModified}"
    })
    int updateByOpLock(ContractOrderDO record);


//    int updateByFilledAmount(@Param("orderId") Long orderId, @Param("status") Integer status, @Param("filledAmount") long filledAmount);

    int updateByFilledAmount(@Param("orderId") Long orderId,
                             @Param("status") Integer status,
                             @Param("filledAmount") long filledAmount,
                             @Param("averagePrice") BigDecimal averagePrice);

    int updateAmountAndStatus(@Param("orderId") Long orderId,
                              @Param("lFilledAmount") BigDecimal filledAmount,
                              @Param("filledPrice") BigDecimal filledPrice,
                              @Param("gmtModified") Date gmtModified);

    List<ContractOrderDO> notMatchOrderList(
            @Param("placeOrder") Integer placeOrder, @Param("partialSuccess") Integer partialSuccess,
            @Param("contractOrderIndex") Long contractOrderIndex, @Param("orderDirection") Integer orderDirection);

    int countByQuery(Map<String, Object> param);

    List<ContractOrderDO> listByQuery(Map<String, Object> param);

    List<ContractOrderDO> listByQuery4Recovery(Map<String, Object> param);

    @Update({
            " update trade_contract_order" +
            " set gmt_modified = now()," +
            " status = #{toStatus}" +
            " where id = #{id} and gmt_modified=#{gmtModified}"
    })
    int cancelByOpLock(@Param("id") long id, @Param("toStatus") int toStatus, @Param("gmtModified") Date gmtModified);

    List<ContractOrderDO> listByUserIdAndOrderType(@Param("userId") Long userId, @Param("orderTypes") List<Integer> orderTypes);
}