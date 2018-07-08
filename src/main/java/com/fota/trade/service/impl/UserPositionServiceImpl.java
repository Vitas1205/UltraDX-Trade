package com.fota.trade.service.impl;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.common.ResultCodeEnum;
import com.fota.client.domain.UserPositionDTO;
import com.fota.client.domain.query.UserPositionQuery;
import com.fota.client.service.UserPositionService;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.mapper.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */

@Service
@Slf4j
public class UserPositionServiceImpl implements UserPositionService {

    @Resource
    private UserPositionMapper userPositionMapper;

    @Override
    public Result<Page<UserPositionDTO>> listPositionByQuery(UserPositionQuery userPositionQuery) {
        Result<Page<UserPositionDTO>> result = Result.create();
        if (userPositionQuery == null || userPositionQuery.getUserId() == null || userPositionQuery.getUserId() <= 0) {
            return result.error(ResultCodeEnum.ILLEGAL_PARAM);
        }
        Page<UserPositionDTO> page = new Page<>();
        if (userPositionQuery.getPageNo() == null || userPositionQuery.getPageNo() <= 0) {
            userPositionQuery.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        if (userPositionQuery.getPageSize() == null
                || userPositionQuery.getPageSize() <= 0
                || userPositionQuery.getPageSize() > 50) {
            userPositionQuery.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        page.setPageNo(userPositionQuery.getPageNo());
        page.setPageSize(userPositionQuery.getPageSize());

        int total = 0;
        try {
            total = userPositionMapper.countByQuery(userPositionQuery);
        } catch (Exception e) {
            log.error("userPositionMapper.countByQuery({})", userPositionQuery, e);
            return result.error(ResultCodeEnum.DATABASE_EXCEPTION);
        }
        page.setTotal(total);
        if (total == 0) {
            return result.success(page);
        }
        List<UserPositionDO> userPositionDOList = null;
        try {
            userPositionDOList = userPositionMapper.listByQuery(userPositionQuery);
        } catch (Exception e) {
            log.error("userPositionMapper.listByQuery({})", userPositionQuery, e);
            return result.error(ResultCodeEnum.DATABASE_EXCEPTION);
        }
        List<UserPositionDTO> userPositionDTOList = null;
        try {
            userPositionDTOList = BeanUtils.copyList(userPositionDOList, UserPositionDTO.class);
        } catch (Exception e) {
            log.error("bean copy exception", e);
            return result.error(ResultCodeEnum.BEAN_COPY_EXCEPTION);
        }
        page.setData(userPositionDTOList);
        return result.success(page);
    }
}
