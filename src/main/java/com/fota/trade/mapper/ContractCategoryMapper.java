package com.fota.trade.mapper;

import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.mapper.common.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ContractCategoryMapper {

    @Insert({
        "insert into trade_contract_category (gmt_create, ",
        "gmt_modified, contract_name, ",
        "asset_id, asset_name, ",
        "total_amount, unfilled_amount, ",
        "delivery_date, status, ",
        "contract_type, price)",
        "values (#{gmtCreate,jdbcType=TIMESTAMP}, ",
        "#{gmtModified,jdbcType=TIMESTAMP}, #{contractName,jdbcType=VARCHAR}, ",
        "#{assetId,jdbcType=INTEGER}, #{assetName,jdbcType=VARCHAR}, ",
        "#{totalAmount,jdbcType=DECIMAL}, #{unfilledAmount,jdbcType=DECIMAL}, ",
        "#{deliveryDate,jdbcType=TIMESTAMP}, #{status,jdbcType=INTEGER}, ",
        "#{contractType,jdbcType=TINYINT}, #{price,jdbcType=DECIMAL})"
    })
    int insert(ContractCategoryDO record);

    int insertSelective(ContractCategoryDO record);

    @Select({
        "select",
        "id, gmt_create, gmt_modified, contract_name, asset_id, asset_name, total_amount, ",
        "unfilled_amount, delivery_date, status, contract_type, price, contract_size",
        "from trade_contract_category",
        "where status = #{status,jdbcType=TINYINT}"
    })
    @ResultMap("BaseResultMap")
    List<ContractCategoryDO> selectByStatus(Integer status);

    @Select({
            "select",
            "id, gmt_create, gmt_modified, contract_name, asset_id, asset_name, total_amount, ",
            "unfilled_amount, delivery_date, status, contract_type, price, contract_size",
            "from trade_contract_category",
            "where id = #{id,jdbcType=BIGINT}"
    })
    @ResultMap("BaseResultMap")
    ContractCategoryDO selectByPrimaryKey(Long id);

    List<ContractCategoryDO> listByQuery(ContractCategoryDO contractCategoryDO);


    @Select({
            "select",
            "id, gmt_create, gmt_modified, contract_name, asset_id, asset_name, total_amount, ",
            "unfilled_amount, delivery_date, status, contract_type, price, contract_size",
            "from trade_contract_category"
    })
    @ResultMap("BaseResultMap")
    List<ContractCategoryDO> getAllContractCategory();

    @Select({
            "select",
            "id, gmt_create, gmt_modified, contract_name, asset_id, asset_name, total_amount, ",
            "unfilled_amount, delivery_date, status, contract_type, price, contract_size",
            "from trade_contract_category where id = #{contractId}"
    })
    @ResultMap("BaseResultMap")
    ContractCategoryDO getContractCategoryById(long contractId);

    int updateByPrimaryKeySelective(ContractCategoryDO record);

    @Update({
        "update trade_contract_category",
        "set gmt_create = #{gmtCreate,jdbcType=TIMESTAMP},",
          "gmt_modified = #{gmtModified,jdbcType=TIMESTAMP},",
          "contract_name = #{contractName,jdbcType=VARCHAR},",
          "asset_id = #{assetId,jdbcType=INTEGER},",
          "asset_name = #{assetName,jdbcType=VARCHAR},",
          "total_amount = #{totalAmount,jdbcType=DECIMAL},",
          "unfilled_amount = #{unfilledAmount,jdbcType=DECIMAL},",
          "delivery_date = #{deliveryDate,jdbcType=TIMESTAMP},",
          "status = #{status,jdbcType=INTEGER},",
          "contract_type = #{contractType,jdbcType=TINYINT},",
          "price = #{price,jdbcType=DECIMAL}",
        "where id = #{id,jdbcType=BIGINT}"
    })
    int updateByPrimaryKey(ContractCategoryDO record);

    @Update({
            "update trade_contract_category",
            "set status = 0",
            "where id = #{id,jdbcType=BIGINT}"
    })
    int deleteByPrimaryKey(Long id);

    @Update({
            "update trade_contract_category",
            "set status = #{status,jdbcType=INTEGER}",
            "set gmt_modified = now()",
            "where id = #{id,jdbcType=BIGINT}"
    })
    int updataStatusById(@Param("id") Long id, @Param("status") Integer status);
}