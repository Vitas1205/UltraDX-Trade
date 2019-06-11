package com.fota.trade.service.impl;

import com.fota.common.Result;
import com.fota.trade.client.RollbackTask;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.BizException;
import com.fota.trade.domain.ContractCategoryDO;
import com.fota.trade.domain.ContractCategoryDTO;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.RollbackResponse;
import com.fota.trade.domain.enums.ContractStatus;
import com.fota.trade.domain.enums.ContractStatusEnum;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.RollbackManager;
import com.fota.trade.mapper.sharding.ContractMatchedOrderMapper;
import com.fota.trade.mapper.trade.ContractCategoryMapper;
import com.fota.trade.service.ContractCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.fota.trade.common.ResultCodeEnum.CONTRACT_IS_ROLLING_BACK;
import static com.fota.trade.common.ResultCodeEnum.SYSTEM_ERROR;
import static com.fota.trade.domain.enums.ContractStatusEnum.DELIVERYING;
import static com.fota.trade.domain.enums.ContractStatusEnum.PROCESSING;

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

    private static final String CONTRACT_LIST_KEY="ALL_VALID_CONTRACT_LIST";
    private static final List<Integer> activeStatusList= Arrays.asList(PROCESSING.getCode(), DELIVERYING.getCode());

    @Override
    public List<ContractCategoryDTO> listActiveContract() {
        List<ContractCategoryDTO> res = getAllValidContract();
        if (null == res) {
            return new ArrayList<>();
        }
        return res.stream().filter(x -> activeStatusList.contains(x.getStatus()))
                .collect(Collectors.toList());
    }

    public List<ContractCategoryDTO> getAllValidContract(){
        List<ContractCategoryDTO> contractCategoryDTOList = redisManager.get(CONTRACT_LIST_KEY);
        if (!CollectionUtils.isEmpty(contractCategoryDTOList)) {
            return contractCategoryDTOList;
        }
        List<Integer> list = new ArrayList<>();
        list.add(PROCESSING.getCode());
        list.add(ContractStatusEnum.DELIVERYING.getCode());
        list.add(ContractStatusEnum.UNOPENED.getCode());
        list.add(ContractStatusEnum.ROOLING_BACK.getCode());
        list.add(ContractStatusEnum.DELIVERED.getCode());
        Map<String, Object> map = new HashMap<>();
        map.put("contractStatus", list);
        try {
            List<ContractCategoryDO> contractCategoryDOS = contractCategoryMapper.listByStatus(map);
            if (CollectionUtils.isEmpty(contractCategoryDOS)) {
                return new ArrayList<>();
            }
            contractCategoryDTOList = contractCategoryDOS.stream().map(x -> BeanUtils.copy(x)).collect(Collectors.toList());
            redisManager.set(CONTRACT_LIST_KEY, contractCategoryDTOList);
            return contractCategoryDTOList;
        } catch (Exception e) {
            log.error("contractCategoryMapper.listByQuery({})", list, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<ContractCategoryDTO> listActiveContractByAssetId(int assetId) {

        List<ContractCategoryDTO> contractCategoryDTOList = listActiveContract();
        if (null == contractCategoryDTOList) {
            return new ArrayList<>();
        }
        return contractCategoryDTOList.stream()
                .filter(x -> x.getAssetId().equals(assetId))
                .collect(Collectors.toList());
    }

    @Override
    public ContractCategoryDTO getContractById(long id) {
        if (id <= 0) {
            return null;
        }
        List<ContractCategoryDTO> contractCategoryDTOS = getAllValidContract();
        if (null == contractCategoryDTOS) {
            return null;
        }
        Optional<ContractCategoryDTO> optional = contractCategoryDTOS.stream()
                .filter(x -> x.getId().equals(id))
                .findFirst();
        if (optional.isPresent()) {
            return optional.get();
        }
        return null;
    }

    /**
     * 根据合约状态获取合约详情
     *
     * @param status
     * @return
     */
    @Override
    public List<ContractCategoryDTO> getContractByStatus(Integer status) {
        if (status <= 0) {
            return null;
        }
        try {
            List<ContractCategoryDO> listDO = contractCategoryMapper.selectByStatus(status);
            List<ContractCategoryDTO> listDTO = new ArrayList<ContractCategoryDTO>();
            if (!CollectionUtils.isEmpty(listDO)){
                for(ContractCategoryDO temp : listDO){
                    listDTO.add(BeanUtils.copy(temp));
                }
            }
            return listDTO;
        } catch (Exception e) {
            log.error("contractCategoryMapper.selectByStatus({})", status, e);
        }
        return new ArrayList<ContractCategoryDTO>();
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
            if (1== ret) {
                invalidCache();
            }
        } catch (Exception e) {
            log.error("contractCategoryMapper.insert({})", contractCategoryDO, e);
        }
        return ret;
    }
    private boolean invalidCache() {
        return redisManager.del(CONTRACT_LIST_KEY);
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
            if (1 == ret) {
                invalidCache();
            }
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
            if (1 == ret) {
                invalidCache();
            }
        } catch (Exception e) {
            log.error("contractCategoryMapper.deleteByPrimaryKey({})", id, e);
        }
        return ret;
    }

    /**
     * 更新合约状态
     * @王冕
     *
     * @param id
     * @param contractStatus
     * @return
     */
    @Override
    public int updateContractStatus(long id, ContractStatus contractStatus) {
        try {
            int ret = contractCategoryMapper.updataStatusById(id, contractStatus.getCode());
            if (1 == ret) {
                invalidCache();
            }
            return ret;
        } catch (Exception e) {
            log.error("updateContractStatus failed ({})", id, e);
        }
        return 0;
    }

    @Override
    public Result<RollbackResponse> rollback(Date safePoint, long contractId) {
        log.info("start to rollback, time={}, contractId={}", safePoint, contractId);
        String rollbackKey = RollbackTask.getContractRollbackLock(contractId);
        Result result = Result.create();
        RollbackResponse response = new RollbackResponse();
        response.setContractId(contractId);
        response.setSafePoint(safePoint);
        result.setData(result);

        //加锁，同一合约只能有一个回滚任务进行
        if (!redisManager.tryLock(rollbackKey, Duration.ofHours(24))) {
            return result.error(CONTRACT_IS_ROLLING_BACK.getCode(), CONTRACT_IS_ROLLING_BACK.getMessage());
        }
        try {
            ResultCode resultCode = rollbackManager.rollBack(safePoint, contractId);
            return result.error(resultCode.getCode(), resultCode.getMessage());
        } catch (Exception e) {
            log.error("rollback failed, time={}, contractId={}", safePoint, contractId, e);
            if (e instanceof BizException) {
                BizException bizE = (BizException) e;
                return result.error(bizE.getCode(), bizE.getMessage());
            }
            return result.error(SYSTEM_ERROR.getCode(), SYSTEM_ERROR.getMessage());
        }finally {
            redisManager.releaseLock(rollbackKey);
        }
    }

    /**
     * @param timestamp
     * @param contractId
     * @return
     */
    @Override
    public ResultCode rollback(Long timestamp, Long contractId) {
        return ResultCode.error(SYSTEM_ERROR.getCode(), SYSTEM_ERROR.getMessage());
    }
}
