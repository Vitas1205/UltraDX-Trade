package com.fota.trade.service.impl;

import com.fota.common.Page;
import com.fota.common.Result;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.UserPositionDTO;
import com.fota.trade.domain.enums.PositionStatusEnum;
import com.fota.trade.domain.query.UserPositionQuery;
import com.fota.trade.mapper.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */

@Slf4j
public class UserPositionServiceImpl implements com.fota.trade.service.UserPositionService {

    @Resource
    private UserPositionMapper userPositionMapper;

    @Override
    public Page<UserPositionDTO> listPositionByQuery(long userId, long contractId, int pageNo, int pageSize) {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setPageNo(pageNo);
        userPositionQuery.setPageSize(pageSize);
        userPositionQuery.setContractId(contractId);
        userPositionQuery.setUserId(userId);
        userPositionQuery.setStatus(PositionStatusEnum.UNDELIVERED.getCode());
        Page<UserPositionDTO> page = new Page<UserPositionDTO>();
        if (userPositionQuery.getPageNo() <= 0) {
            userPositionQuery.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        if (userPositionQuery.getPageSize() <= 0
                || userPositionQuery.getPageSize() > 50) {
            userPositionQuery.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        page.setPageNo(userPositionQuery.getPageNo());
        page.setPageSize(userPositionQuery.getPageSize());
        userPositionQuery.setStartRow((userPositionQuery.getPageNo() - 1) * userPositionQuery.getPageSize());
        userPositionQuery.setEndRow(userPositionQuery.getPageSize());
        int total = 0;
        try {
            total = userPositionMapper.countByQuery(ParamUtil.objectToMap(userPositionQuery));
        } catch (Exception e) {
            log.error("userPositionMapper.countByQuery({})", userPositionQuery, e);
            return page;
        }
        page.setTotal(total);
        if (total == 0) {
            return page;
        }
        List<UserPositionDO> userPositionDOList = null;
        List<UserPositionDTO> list = new ArrayList<>();
        try {
            userPositionDOList = userPositionMapper.listByQuery(ParamUtil.objectToMap(userPositionQuery));
            if (userPositionDOList != null && userPositionDOList.size() > 0) {
                for (UserPositionDO tmp : userPositionDOList) {
                    list.add(BeanUtils.copy(tmp));
                }
            }
        } catch (Exception e) {
            log.error("userPositionMapper.listByQuery({})", userPositionQuery, e);
            return page;
        }
//        List<UserPositionDTO> userPositionDTOList = null;
//        try {
//            userPositionDTOList = BeanUtils.copyList(userPositionDOList, UserPositionDTO.class);
//        } catch (Exception e) {
//            log.error("bean copy exception", e);
//            return userPositionDTOPage;
//        }
        page.setData(list);
        return page;
    }



    @Override
    public long getTotalPositionByContractId(long contractId) {
        long totalPosition = 0L;
        List<UserPositionDO> userPositionDOList = userPositionMapper.selectByContractId(contractId,  PositionStatusEnum.UNDELIVERED.getCode());
        if (userPositionDOList != null && userPositionDOList.size() > 0) {
            for (UserPositionDO userPositionDO : userPositionDOList) {
                if (userPositionDO.getContractId().equals(contractId) && userPositionDO.getPositionType() == 1) {
                    totalPosition += userPositionDO.getUnfilledAmount();
                }
            }
        }
        return totalPosition*2;
    }

    /**
     * * * @荆轲
     * @param id
     * @return
     */
    @Override
    public ResultCode deliveryPosition(long id) {
        UserPositionDO userPositionDO = new UserPositionDO();
        userPositionDO.setId(id);
        userPositionDO.setStatus(2);
        int updateRet = 0;
        try {
            updateRet = userPositionMapper.updateByPrimaryKeySelective(userPositionDO);
            if (updateRet > 0) {
                return ResultCode.success();
            }
        } catch (Exception e) {
            log.error("userPositionMapper.updateByPrimaryKeySelective({})", userPositionDO, e);
        }
        return ResultCode.error(ResultCodeEnum.DATABASE_EXCEPTION.getCode(), "position delivery failed");
    }


    /**
     * * * @王冕
     * @param userId
     * @return
     */
    @Override
    public List<UserPositionDTO> listPositionByUserId(long userId) {
        try {
            List<UserPositionDO> DOlist = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
            List<UserPositionDTO> DTOlist = new ArrayList<>();
            if (DOlist != null && DOlist.size() > 0) {
                for (UserPositionDO tmp : DOlist) {
                    DTOlist.add(BeanUtils.copy(tmp));
                }
            }
            return DTOlist;
        }catch (Exception e){
            log.error("listPositionByUserId failed:{}",userId);
        }
        return null;
    }

    /**
     * *@王冕
     * @param contractId
     * @return
     */
    @Override
    public List<UserPositionDTO> listPositionByContractId(Long contractId) {
        try {
            List<UserPositionDO> DOlist = userPositionMapper.selectByContractId(contractId, PositionStatusEnum.UNDELIVERED.getCode());
            List<UserPositionDTO> DTOlist = new ArrayList<>();
            if (DOlist != null && DOlist.size() > 0) {
                for (UserPositionDO tmp : DOlist) {
                    DTOlist.add(BeanUtils.copy(tmp));
                }
            }
            return DTOlist;
        }catch (Exception e){
            log.error("listPositionByContractId failed:{}",contractId);
        }
        return null;
    }

    @Override
    public Result<BigDecimal> getPositionMarginByContractId(Long contractId) {
        Result<BigDecimal> result = new Result<>();
        result.setData(BigDecimal.ZERO);

        return result;
    }
}
