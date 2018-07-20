package com.fota.client.service;

import com.fota.trade.domain.ContractCategoryDO;

import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
public interface ContractCategoryService {

    /**
     * 获取所有生效的合约
     * @return
     */
    List<ContractCategoryDO> listActiveContract();

    /**
     * 根据标的物获取生效的合约
     * @param assetId
     * @return
     */
    List<ContractCategoryDO> listActiveContractByAssetId(Integer assetId);

    /**
     * 根据合约id获取合约详情
     * @param id
     * @return
     */
    ContractCategoryDO getContractById(Long id);

    /**
     * 根据合约id获取合约详情
     * @param id
     * @return
     */
    ContractCategoryDO getPreviousContract(Long id);

    /**
     * 创建合约
     * @param contractCategoryDO
     * @return
     */
    Integer saveContract(ContractCategoryDO contractCategoryDO);

    /**
     * 修改合约
      * @param contractCategoryDO
     * @return
     */
    Integer updateContract(ContractCategoryDO contractCategoryDO);

    /**
     * 删除合约
     * @param id
     * @return
     */
    Integer removeContract(Long id);

}
