package com.fota.trade.mapper.sharding;

import com.fota.trade.domain.ContractOrderDO;
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


    int batchInsert(@Param("items") List<ContractOrderDO> contractOrderDOS);



    @Select({
            "select * from trade_contract_order ",
            "where user_id =  #{userId,jdbcType=BIGINT} and id = #{id,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    ContractOrderDO selectByIdAndUserId(@Param("userId") Long userId, @Param("id") Long id);


    List<ContractOrderDO> selectByUserIdAndIds(@Param("userId") Long userId, @Param("items") List<Long> ids);

    @Select(  " select max(gmt_create) " +
            " from trade_contract_order " +
            " where status in (8,9) ")
    @ResultType(Date.class)
    Date getMaxCreateTime();



    List<ContractOrderDO> selectNotEnforceOrderByUserIdAndContractId(@Param("userId") Long userId, @Param("contractId") Long contractId);



    @Select({
            "select * ",
            "from trade_contract_order ",
            "where user_id =  #{userId,jdbcType=BIGINT} and status in (8,9)"
    })
    @ResultMap("BaseResultMap")
    List<ContractOrderDO> selectUnfinishedOrderByUserId(Long userId);

    @Select({
            "select * ",
            "from trade_contract_order ",
            "where user_id =  #{userId,jdbcType=BIGINT} and status in (8,9) and order_type != 3"
    })
    @ResultMap("BaseResultMap")
    List<ContractOrderDO> selectNotEnforceOrderByUserId(Long userId);


    @Select({
            " select * ",
            " from trade_contract_order_#{tableIndex} ",
            " where  status in (8,9) " +
            " and gmt_create <= #{maxGmtCreate} " +
            " order by gmt_create asc, id asc " +
             " limit #{start}, #{pageSize} "
    })
    @ResultMap("BaseResultMap")
    List<ContractOrderDO> queryForRecovery(@Param("tableIndex") int tableIndex, @Param("maxGmtCreate") Date maxGmtCreate, @Param("start") int start, @Param("pageSize") int pageSize);


    int updateAmountAndStatus(@Param("userId") Long userId,
                              @Param("orderId") Long orderId,
                              @Param("filledAmount") BigDecimal filledAmount,
                              @Param("filledPrice") BigDecimal filledPrice,
                              @Param("gmtModified") Date gmtModified);


    @Update("update trade_contract_order set gmt_modified=now(3), unfilled_amount=#{unfilledAmount},  status=#{toStatus} " +
            " where user_id=#{userId} and id=#{orderId}")
    int updateAAS(@Param("userId") Long userId,
                  @Param("orderId") Long orderId,
                  @Param("unfilledAmount") BigDecimal amount,
                  @Param("toStatus") int toStatus);


//    int batchUpdateAmountAndStatus(List<UpdateOrderItem> updateOrderItems);

    int countByQuery(Map<String, Object> param);

    List<ContractOrderDO> listByQuery(Map<String, Object> param);


    @Update({
            " update trade_contract_order" +
            " set gmt_modified = now()," +
            " status = #{toStatus}" +
            " where user_id=#{userId} and id = #{id} and status != #{toStatus} "
    })
    int cancel(@Param("userId") long userId, @Param("id") long id, @Param("toStatus") int toStatus);

    List<ContractOrderDO> listByUserIdAndOrderType(@Param("userId") Long userId, @Param("orderTypes") List<Integer> orderTypes);
}