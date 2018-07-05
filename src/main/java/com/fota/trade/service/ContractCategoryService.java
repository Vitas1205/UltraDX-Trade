package com.fota.trade.service;

import com.fota.trade.domain.ContractCategoryDO;

import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
public interface ContractCategoryService {

    List<ContractCategoryDO> listActiveContract();

    List<ContractCategoryDO> listActiveContractByAssetName(String assetName);

    ContractCategoryDO getContractById(Long id);

    Integer saveContract(ContractCategoryDO contractCategoryDO);

    Integer updateContract(ContractCategoryDO contractCategoryDO);

    Integer removeContract(Long id);

}
