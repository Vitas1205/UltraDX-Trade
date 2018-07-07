package com.fota.trade.service.impl;

import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.enums.ContractStatusEnum;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.client.service.ContractCategoryService;
import org.apache.commons.lang.StringUtils;
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
@Service("contractCategoryService")
public class ContractCategoryServiceImpl implements ContractCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ContractCategoryService.class);

    @Resource
    private ContractCategoryMapper contractCategoryMapper;

    @Override
    public List<ContractCategoryDO> listActiveContract() {
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
        return result;
    }

    @Override
    public List<ContractCategoryDO> listActiveContractByAssetId(Integer assetId) {
        List<ContractCategoryDO> result = null;
        if (assetId == null || assetId <= 0) {
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
        return result;
    }

    @Override
    public ContractCategoryDO getContractById(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        try {
            return contractCategoryMapper.selectByPrimaryKey(id);
        } catch (Exception e) {
            log.error("contractCategoryMapper.selectByPrimaryKey({})", id, e);
        }
        return null;
    }

    @Override
    public Integer saveContract(ContractCategoryDO contractCategoryDO) {
        int ret = 0;
        try {
            ret = contractCategoryMapper.insert(contractCategoryDO);
        } catch (Exception e) {
            log.error("contractCategoryMapper.insert({})", contractCategoryDO, e);
        }
        return ret;
    }

    @Override
    public Integer updateContract(ContractCategoryDO contractCategoryDO) {
        int ret = 0;
        if (contractCategoryDO == null || contractCategoryDO.getId() == null || contractCategoryDO.getId() <= 0) {
            log.error("illegal param when update contract");
            return ret;
        }
        try {
            ret = contractCategoryMapper.updateByPrimaryKeySelective(contractCategoryDO);
        } catch (Exception e) {
            log.error("contractCategoryMapper.updateByPrimaryKey({})", contractCategoryDO, e);
        }
        return ret;
    }

    @Override
    public Integer removeContract(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        int ret = 0;
        try {
            ret = contractCategoryMapper.deleteByPrimaryKey(id);
        } catch (Exception e) {
            log.error("contractCategoryMapper.deleteByPrimaryKey({})", id, e);
        }
        return ret;
    }

}
