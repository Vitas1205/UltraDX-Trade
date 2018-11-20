package com.fota.trade.mapper;

import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UsdkOrderDO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface UsdkOrderMapper {


    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table trade_usdk_order
     *
     * @mbggenerated
     */
    @Insert({
        "insert into trade_usdt_order (id, gmt_create, ",
        "gmt_modified, user_id, ",
        "asset_id, asset_name, ",
        "order_direction, order_type, ",
        "total_amount, unfilled_amount, ",
        "price, fee, status, average_price, order_context)",
        "values (#{id,jdbcType=BIGINT}, #{gmtCreate}, #{gmtModified}, ",
        "#{userId,jdbcType=BIGINT}, ",
        "#{assetId,jdbcType=INTEGER}, #{assetName,jdbcType=VARCHAR}, ",
        "#{orderDirection,jdbcType=TINYINT}, #{orderType,jdbcType=TINYINT}, ",
        "#{totalAmount,jdbcType=DECIMAL}, #{unfilledAmount,jdbcType=DECIMAL}, ",
        "#{price,jdbcType=DECIMAL}, #{fee,jdbcType=DECIMAL}, #{status,jdbcType=INTEGER}, #{averagePrice,jdbcType=DECIMAL}, #{orderContext,jdbcType=VARCHAR})"
    })
    int insert(UsdkOrderDO record);

    int batchInsert(List<UsdkOrderDO> list);


    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, asset_id, asset_name, order_direction, ",
            "order_type, total_amount, unfilled_amount, price, fee, status, average_price, order_context, order_context",
            "from trade_usdt_order",
            "where id = #{id,jdbcType=BIGINT} and user_id =  #{userId,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    UsdkOrderDO selectByUserIdAndId(@Param("userId") long userId, @Param("id") Long id);

    List<UsdkOrderDO> listByUserIdAndIds(@Param("userId") long userId, @Param("idList") List<Long> idList);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, asset_id, asset_name, order_direction, ",
            "order_type, total_amount, unfilled_amount, price, fee, status, average_price, order_context",
            "from trade_usdt_order",
            "where user_id =  #{userId,jdbcType=BIGINT} and status in (8,9)"
    })
    @ResultMap("BaseResultMap")
    List<UsdkOrderDO> selectUnfinishedOrderByUserId(Long userId);

    int countByQuery(Map<String, Object> param);

    List<UsdkOrderDO> listByQuery(Map<String, Object> param);

    int updateByFilledAmount(@Param("userId") long userId, @Param("id") Long orderId,
                             @Param("filledAmount") BigDecimal filledAmount,
                             @Param("filledPrice") BigDecimal filledPrice,
                             @Param("gmtModified") Date gmtModified
                             );


    @Select(  " select max(gmt_create) " +
            " from trade_usdt_order " +
            " where status in (8,9) ")
    @ResultType(Date.class)
    Date getMaxCreateTime();

    @Select({
            " select * ",
            " from trade_usdt_order_#{tableIndex} ",
            " where  status in (8,9) " +
                    " and gmt_create <= #{maxGmtCreate} " +
                    " order by gmt_create asc, id asc " +
                    " limit #{start}, #{pageSize} "
    })
    @ResultMap("BaseResultMap")
    List<UsdkOrderDO> queryForRecovery(@Param("tableIndex") int tableIndex, @Param("maxGmtCreate") Date maxGmtCreate, @Param("start") int start, @Param("pageSize") int pageSize);


    @Update({
            "update trade_usdt_order",
            "set gmt_modified = now(),",
            "status = #{toStatus,jdbcType=INTEGER}",
            "where user_id=#{userId} and id = #{id,jdbcType=BIGINT}"
    })
    int cancel(@Param("userId") long userId, @Param("id") long id, @Param("toStatus") int toStatus);

}