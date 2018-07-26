package com.fota.trade.mapper;

import com.fota.client.domain.UsdkOrderDTO;
import com.fota.client.domain.query.UsdkOrderQuery;
import com.fota.trade.domain.UsdkOrderDO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
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
    @Delete({
        "delete from trade_usdk_order",
        "where id = #{id,jdbcType=BIGINT}"
    })
    int deleteByPrimaryKey(Long id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table trade_usdk_order
     *
     * @mbggenerated
     */
    @Insert({
        "insert into trade_usdk_order (id, gmt_create, ",
        "gmt_modified, user_id, ",
        "asset_id, asset_name, ",
        "order_direction, order_type, ",
        "total_amount, unfilled_amount, ",
        "price, fee, status)",
        "values (#{id,jdbcType=BIGINT}, now(), ",
        "now(), #{userId,jdbcType=BIGINT}, ",
        "#{assetId,jdbcType=INTEGER}, #{assetName,jdbcType=VARCHAR}, ",
        "#{orderDirection,jdbcType=TINYINT}, #{orderType,jdbcType=TINYINT}, ",
        "#{totalAmount,jdbcType=DECIMAL}, #{unfilledAmount,jdbcType=DECIMAL}, ",
        "#{price,jdbcType=DECIMAL}, #{fee,jdbcType=DECIMAL}, #{status,jdbcType=INTEGER})"
    })
    int insert(UsdkOrderDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table trade_usdk_order
     *
     * @mbggenerated
     */
    int insertSelective(UsdkOrderDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table trade_usdk_order
     *
     * @mbggenerated
     */
    @Select({
        "select",
        "id, gmt_create, gmt_modified, user_id, asset_id, asset_name, order_direction, ",
        "order_type, total_amount, unfilled_amount, price, fee, status",
        "from trade_usdk_order",
        "where id = #{id,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    UsdkOrderDO selectByPrimaryKey(Long id);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, asset_id, asset_name, order_direction, ",
            "order_type, total_amount, unfilled_amount, price, fee, status",
            "from trade_usdk_order",
            "where user_id =  #{userId,jdbcType=BIGINT} and status in (8,9)"
    })
    @ResultMap("BaseResultMap")
    List<UsdkOrderDO> selectUnfinishedOrderByUserId(Long userId);


    @Select({
            "select",
            "id, gmt_create, gmt_modified, user_id, asset_id, asset_name, order_direction, ",
            "order_type, total_amount, unfilled_amount, price, fee, status",
            "from trade_usdk_order",
            "where id = #{id,jdbcType=BIGINT} and user_id =  #{userId,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    UsdkOrderDO selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int countByQuery(Map<String, Object> param);

    List<UsdkOrderDO> listByQuery(Map<String, Object> param);

    List<UsdkOrderDO> selectByUserId(Long userId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table trade_usdk_order
     *
     * @mbggenerated
     */
    int updateByPrimaryKeySelective(UsdkOrderDO record);


    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table trade_usdk_order
     *
     * @mbggenerated
     */
    @Update({
        "update trade_usdk_order",
        "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
          "gmt_modified = now(),",
          "user_id = #{userId,jdbcType=BIGINT},",
          "asset_id = #{assetId,jdbcType=INTEGER},",
          "asset_name = #{assetName,jdbcType=VARCHAR},",
          "order_direction = #{orderDirection,jdbcType=TINYINT},",
          "order_type = #{orderType,jdbcType=TINYINT},",
          "total_amount = #{totalAmount,jdbcType=DECIMAL},",
          "unfilled_amount = #{unfilledAmount,jdbcType=DECIMAL},",
          "price = #{price,jdbcType=DECIMAL},",
          "fee = #{fee,jdbcType=DECIMAL},",
          "status = #{status,jdbcType=INTEGER}",
        "where id = #{id,jdbcType=BIGINT}"
    })
    int updateByPrimaryKey(UsdkOrderDO record);

    /*@Update({
        "update trade_usdk_order",
        "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
          "gmt_modified = now(),",
          "user_id = #{userId,jdbcType=BIGINT},",
          "asset_id = #{assetId,jdbcType=INTEGER},",
          "asset_name = #{assetName,jdbcType=VARCHAR},",
          "order_direction = #{orderDirection,jdbcType=TINYINT},",
          "order_type = #{orderType,jdbcType=TINYINT},",
          "total_amount = #{totalAmount,jdbcType=DECIMAL},",
          "unfilled_amount = #{unfilledAmount,jdbcType=DECIMAL},",
          "price = #{price,jdbcType=DECIMAL},",
          "fee = #{fee,jdbcType=DECIMAL},",
          "status = #{status,jdbcType=INTEGER}",
        "where id = #{id,jdbcType=BIGINT} and gmt_modified = #{gmtModified,jdbcType=TIMESTAMP}"
    })*/
    @Update({
            "update trade_usdk_order",
            "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
            "gmt_modified = now(),",
            "user_id = #{userId,jdbcType=BIGINT},",
            "asset_id = #{assetId,jdbcType=INTEGER},",
            "asset_name = #{assetName,jdbcType=VARCHAR},",
            "order_direction = #{orderDirection,jdbcType=TINYINT},",
            "order_type = #{orderType,jdbcType=TINYINT},",
            "total_amount = #{totalAmount,jdbcType=DECIMAL},",
            "unfilled_amount = #{unfilledAmount,jdbcType=DECIMAL},",
            "price = #{price,jdbcType=DECIMAL},",
            "fee = #{fee,jdbcType=DECIMAL},",
            "status = #{status,jdbcType=INTEGER}",
            "where id = #{id,jdbcType=BIGINT}"
    })
    int updateByPrimaryKeyAndOpLock(UsdkOrderDO record);

    @Update({
            "update trade_usdk_order",
            "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
            "gmt_modified = now(),",
            "unfilled_amount = unfilled_amount - #{filledAmount,jdbcType=DECIMAL},",
            "status = #{status,jdbcType=INTEGER}",
            "where id = #{orderId,jdbcType=BIGINT} and unfilled_amount - #{filledAmount,jdbcType=DECIMAL} >= 0"
    })
    int updateByFilledAmount(@Param("orderId") Long orderId, @Param("status") Integer status, @Param("filledAmount") BigDecimal filledAmount);

    @Update({
            "update trade_usdk_order",
            "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
            "gmt_modified = now(),",
            "user_id = #{userId,jdbcType=BIGINT},",
            "asset_id = #{assetId,jdbcType=INTEGER},",
            "asset_name = #{assetName,jdbcType=VARCHAR},",
            "order_direction = #{orderDirection,jdbcType=TINYINT},",
            "order_type = #{orderType,jdbcType=TINYINT},",
            "total_amount = #{totalAmount,jdbcType=DECIMAL},",
            "unfilled_amount = #{unfilledAmount,jdbcType=DECIMAL},",
            "price = #{price,jdbcType=DECIMAL},",
            "fee = #{fee,jdbcType=DECIMAL},",
            "status = #{status,jdbcType=INTEGER}",
            "where id = #{id,jdbcType=BIGINT} and user_id = #{userId,jdbcType=BIGINT} and gmt_modified = #{gmtModified,jdbcType=TIMESTAMP}"
    })
    int updateByOpLock(UsdkOrderDO record);


    List<UsdkOrderDO> notMatchOrderList(@Param("placeOrder") Integer placeOrder, @Param("partialSuccess") Integer partialSuccess,
                                        @Param("contractOrderIndex") Long contractOrderIndex, @Param("orderDirection") Integer orderDirection);
}