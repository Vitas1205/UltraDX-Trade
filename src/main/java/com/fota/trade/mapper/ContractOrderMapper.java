package com.fota.trade.mapper;

import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.DateWrapper;
import com.fota.trade.mapper.common.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContractOrderMapper extends BaseMapper<ContractOrderDO> {


    @Override
    @Insert({
            "insert into trade_contract_order (id, gmt_create, ",
            "gmt_modified, user_id, ",
            "contract_id, contract_name, ",
            "order_direction, ",
            "total_amount, unfilled_amount, ",
            "price, fee, ",
            "status, average_price, order_type, close_type, order_context)",
            "values (#{id}, now(3), ",
            "now(3), #{userId}, ",
            "#{contractId,jdbcType=INTEGER}, #{contractName,jdbcType=VARCHAR}, ",
            "#{orderDirection,jdbcType=TINYINT}, ",
            "#{totalAmount,jdbcType=BIGINT}, #{unfilledAmount,jdbcType=BIGINT}, ",
            "#{price,jdbcType=DECIMAL}, #{fee,jdbcType=DECIMAL},",
            " #{status}, #{averagePrice,jdbcType=DECIMAL}, #{orderType}, #{closeType}, #{orderContext,jdbcType=VARCHAR})"
    })
    int insert(ContractOrderDO record);




    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, contract_id, contract_name, order_direction, ",
            "operate_type, order_type, operate_direction, lever, total_amount, unfilled_amount, close_type, price, ",
            "fee, usdk_locked_amount, position_locked_amount, status, average_price, order_context",
            "from trade_contract_order",
            "where user_id =  #{userId,jdbcType=BIGINT} and id = #{id,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    ContractOrderDO selectByIdAndUserId(@Param("userId") Long userId, @Param("id") Long id);

    @Select(  " select max(gmt_create) " +
            " from trade_contract_order " +
            " where status in (8,9) ")
    @ResultType(Date.class)
    Date getMaxCreateTime();



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


    int updateAmountAndStatus(@Param("userId") Long userId,
                              @Param("orderId") Long orderId,
                              @Param("filledAmount") BigDecimal filledAmount,
                              @Param("filledPrice") BigDecimal filledPrice,
                              @Param("gmtModified") Date gmtModified);


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