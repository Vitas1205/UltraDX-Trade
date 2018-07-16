package com.fota.trade.service.impl;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.common.ResultCodeEnum;
import com.fota.client.domain.UserPositionDTO;
import com.fota.client.domain.query.UserPositionQuery;
import com.fota.client.service.UserPositionService;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.UserPositionDTOPage;
import com.fota.trade.domain.UserPositionQueryDTO;
import com.fota.trade.mapper.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */

@Service
@Slf4j
public class UserPositionServiceImpl implements com.fota.trade.service.UserPositionService.Iface {

    @Resource
    private UserPositionMapper userPositionMapper;

    @Override
    public UserPositionDTOPage listPositionByQuery(long userId, long contractId, int pageNo, int pageSize) {
//        Result<Page<UserPositionDTO>> result = Result.create();
        UserPositionDTOPage userPositionDTOPage = new UserPositionDTOPage();
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setPageNo(pageNo);
        userPositionQuery.setPageSize(pageSize);
        userPositionQuery.setContractId(contractId);
        userPositionQuery.setUserId(userId);
        UserPositionDTOPage page = new UserPositionDTOPage();
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
        userPositionQuery.setEndRow(userPositionQuery.getStartRow() + userPositionQuery.getPageSize());
        int total = 0;
        try {
            total = userPositionMapper.countByQuery(ParamUtil.objectToMap(userPositionQuery));
        } catch (Exception e) {
            log.error("userPositionMapper.countByQuery({})", userPositionQuery, e);
            return userPositionDTOPage;
        }
        page.setTotal(total);
        if (total == 0) {
            return userPositionDTOPage;
        }
        List<UserPositionDO> userPositionDOList = null;
        List<com.fota.trade.domain.UserPositionDTO> list = new ArrayList<>();
        try {
            userPositionDOList = userPositionMapper.listByQuery(ParamUtil.objectToMap(userPositionQuery));
            if (userPositionDOList != null && userPositionDOList.size() > 0) {
                for (UserPositionDO tmp : userPositionDOList) {
                    list.add(BeanUtils.copy(tmp));
                }
            }
        } catch (Exception e) {
            log.error("userPositionMapper.listByQuery({})", userPositionQuery, e);
            return userPositionDTOPage;
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
    public long getTotalPositionByContractId(long contractId) throws TException {
        long totalPosition = 0L;
        List<UserPositionDO> userPositionDOList = userPositionMapper.selectByContractId(contractId);
        for (UserPositionDO userPositionDO : userPositionDOList){
            if (userPositionDO.getContractId().equals(contractId) && userPositionDO.getPositionType() == 1){
                totalPosition += userPositionDO.getUnfilledAmount();
            }
        }


        return totalPosition;
    }
}
