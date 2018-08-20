package com.fota.trade.service.impl;

import com.fota.trade.client.RollbackTask;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.BusinessException;
import com.fota.trade.domain.BaseQuery;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractCategoryDTO;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.enums.ContractStatus;
import com.fota.trade.domain.enums.ContractStatusEnum;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.RollbackManager;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.service.ContractCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.fota.trade.common.ResultCodeEnum.CONTRACT_IS_ROLLING_BACK;
import static com.fota.trade.common.ResultCodeEnum.SYSTEM_ERROR;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
public class ContractCategoryServiceImpl implements ContractCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ContractCategoryServiceImpl.class);

    @Resource
    private ContractCategoryMapper contractCategoryMapper;
    @Resource
    private ContractMatchedOrderMapper matchedOrderMapper;
    @Autowired
    RedisTemplate redisTemplate;
    @Resource
    private RedisManager redisManager;
    @Autowired
    private RollbackManager rollbackManager;

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
     * todo@王冕
     *
     * @param id
     * @param contractStatus
     * @return
     */
    @Override
    public int updateContractStatus(long id, ContractStatus contractStatus) {
        try {
            int ret = contractCategoryMapper.updataStatusById(id, contractStatus.getCode());
            return ret;
        } catch (Exception e) {
            log.error("updateContractStatus failed ({})", id, e);
        }
        return 0;
    }

    /**
     * @param timestamp
     * @param contractId
     * @return
     */
    @Override
    public ResultCode rollback(Long timestamp, Long contractId) {
        log.info("start to rollback, time={}, contractId={}", new Date(timestamp), contractId);
        String rollbackKey = RollbackTask.getContractRollbackLock(contractId);
        //加锁，同一合约只能有一个回滚任务进行
        if (!redisManager.tryLock(rollbackKey)) {
            return ResultCode.error(CONTRACT_IS_ROLLING_BACK.getCode(), CONTRACT_IS_ROLLING_BACK.getMessage());
        }
        try {
            return internalRollBack(timestamp, contractId);
        } catch (Exception e) {
            log.error("rollback failed, time={}, contractId={}", new Date(timestamp), contractId, e);
            if (e instanceof BusinessException) {
                BusinessException bizE = (BusinessException) e;
                return ResultCode.error(bizE.getCode(), bizE.getMessage());
            }
            return ResultCode.error(SYSTEM_ERROR.getCode(), SYSTEM_ERROR.getMessage());
        }finally {
            redisManager.releaseLock(rollbackKey);
        }

    }

    @Transactional(rollbackFor = {Exception.class})
    public ResultCode internalRollBack(long timestamp, long contractId) {
        int pageSize = 100;
        int pageIndex = 1;
        Date startTaskTime = new Date();


        BaseQuery query = new BaseQuery();
        query.setSourceId((int) contractId);
        query.setStartTime(new Date(timestamp));
        query.setEndTime(new Date());
        long count = matchedOrderMapper.count(query);
        if (0 >= count) {
            log.info("there is nothing to rollback");
            return ResultCode.success();
        }

        //分割任务
        int maxPageIndex = ((int) count - 1) / pageSize + 1;;
        List<RollbackTask> tasks = new ArrayList<>();
        while (pageIndex <= maxPageIndex) {
            RollbackTask rollbackTask = new RollbackTask();
            rollbackTask.setContractId(contractId);
            rollbackTask.setPageSize(pageSize);
            rollbackTask.setRollbackPoint(new Date(timestamp));
            rollbackTask.setTaskStartPoint(startTaskTime);
            rollbackTask.setPageSize(pageSize)
                    .setPageIndex(pageIndex);
            tasks.add(rollbackTask);
            pageIndex++;
        }
        tasks.parallelStream().forEach(task ->{
            rollbackManager.rollbackMatchedOrder(task);
        });

        return ResultCode.success();

    }
}
