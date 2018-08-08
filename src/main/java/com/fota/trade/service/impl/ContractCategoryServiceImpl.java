package com.fota.trade.service.impl;

import com.fota.trade.common.BeanUtils;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractCategoryDTO;
import com.fota.trade.domain.enums.ContractStatus;
import com.fota.trade.domain.enums.ContractStatusEnum;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.service.ContractCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
public class ContractCategoryServiceImpl implements ContractCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ContractCategoryServiceImpl.class);

    @Resource
    private ContractCategoryMapper contractCategoryMapper;

    @Override
    public List<ContractCategoryDTO> listActiveContract() {
        List<ContractCategoryDO> result = null;
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        contractCategoryDO.setStatus(ContractStatusEnum.PROCESSING.getCode());
        try {
            result = contractCategoryMapper.listByQuery(contractCategoryDO);
        } catch (Exception e) {
            log.error("contractCategoryMapper.listByQuery({})", contractCategoryDO, e);
        }
        if (result == null) {
            result = new ArrayList<>();
        }
        List<ContractCategoryDTO> contractCategoryDTOList = new ArrayList<>();
        for (ContractCategoryDO temp : result) {
            contractCategoryDTOList.add(BeanUtils.copy(temp));
        }
        return contractCategoryDTOList;
    }

    @Override
    public List<ContractCategoryDTO> listActiveContractByAssetId(int assetId) {
        List<ContractCategoryDO> result = null;
        if (assetId <= 0) {
            return null;
        }
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        contractCategoryDO.setStatus(ContractStatusEnum.PROCESSING.getCode());
        contractCategoryDO.setAssetId(assetId);
        try {
            result = contractCategoryMapper.listByQuery(contractCategoryDO);
        } catch (Exception e) {
            log.error("contractCategoryMapper.listByQuery({})", contractCategoryDO, e);
        }
        if (result == null) {
            result = new ArrayList<>();
        }
        List<ContractCategoryDTO> contractCategoryDTOList = new ArrayList<>();
        for (ContractCategoryDO temp : result) {
            contractCategoryDTOList.add(BeanUtils.copy(temp));
        }
        return contractCategoryDTOList;
    }

    @Override
    public ContractCategoryDTO getContractById(long id) {
        if (id <= 0) {
            return null;
        }
        try {
            ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(id);
            if (contractCategoryDO != null) {
                return BeanUtils.copy(contractCategoryDO);
            }
        } catch (Exception e) {
            log.error("contractCategoryMapper.selectByPrimaryKey({})", id, e);
        }
        return new ContractCategoryDTO();
    }

    @Override
    public ContractCategoryDTO getPreviousContract(long l) {
        return null;
    }

    @Override
    public int saveContract(ContractCategoryDTO contractCategoryDO) {
        int ret = 0;
        try {
            ret = contractCategoryMapper.insert(BeanUtils.copy(contractCategoryDO));
        } catch (Exception e) {
            log.error("contractCategoryMapper.insert({})", contractCategoryDO, e);
        }
        return ret;
    }

    @Override
    public int updateContract(ContractCategoryDTO contractCategoryDTO) {
        int ret = 0;
        if (contractCategoryDTO == null || contractCategoryDTO.getId() <= 0) {
            log.error("illegal param when update contract");
            return ret;
        }
        try {
            ret = contractCategoryMapper.updateByPrimaryKeySelective(BeanUtils.copy(contractCategoryDTO));
        } catch (Exception e) {
            log.error("contractCategoryMapper.updateByPrimaryKey({})", contractCategoryDTO, e);
        }
        return ret;
    }

    @Override
    public int removeContract(long id) {
        if (id <= 0) {
            return 0;
        }
        int ret = 0;
        try {
            ret = contractCategoryMapper.deleteByPrimaryKey(id);
        } catch (Exception e) {
            log.error("contractCategoryMapper.deleteByPrimaryKey({})", id, e);
        }
        return ret;
    }

    /**
     * 更新合约状态
     *
     * @param id
     * @param contractStatus
     * @return
     */
    @Override
    public int updateContractStatus(long id, ContractStatus contractStatus) {
        return 0;
    }

}
