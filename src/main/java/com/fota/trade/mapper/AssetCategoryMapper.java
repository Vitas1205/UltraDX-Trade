package com.fota.trade.mapper;

import com.fota.trade.domain.AssetCategoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午2:50 2018/7/6
 * @Modified:
 */
@Mapper
public interface AssetCategoryMapper {

    int insert(AssetCategoryDO assetCategoryDO);

    List<AssetCategoryDO> getAll();
}
