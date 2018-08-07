package com.fota.trade.service;

import com.fota.trade.domain.ContractCategoryDTO;
import com.fota.trade.domain.enums.ContractStatus;

import java.util.List;

public interface ContractCategoryService {

    /**
     * 获取所有生效的合约
     * @return
     */
    List<ContractCategoryDTO> listActiveContract();

    /**
     * 根据标的物获取生效的合约
     * @param assetId
     * @return
     *
     * @param assetId
     */
    List<ContractCategoryDTO> listActiveContractByAssetId(int assetId);

    /**
     * 根据合约id获取合约详情
     * @param id
     * @return
     *
     * @param id
     */
    ContractCategoryDTO getContractById(long id);

    /**
     * 根据合约id获取合约详情
     * @param id
     * @return
     *
     * @param id
     */
    ContractCategoryDTO getPreviousContract(long id);

    /**
     * 创建合约
     * @param
     * @return
     *
     * @param contractCategoryDTO
     */
    int saveContract(ContractCategoryDTO contractCategoryDTO);

    /**
     * * 修改合约
     *  * @param contractCategoryDO
     * * @return
     *
     * @param contractCategoryDTO
     */
    int updateContract(ContractCategoryDTO contractCategoryDTO);

    /**
     * 删除合约
     * @param id
     * @return
     *
     * @param id
     */
    int removeContract(long id);

    /**
     * 更新合约状态
     * @param id
     * @return
     */
    int updateContractStatus(long id, ContractStatus contractStatus);



}