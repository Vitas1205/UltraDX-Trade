package com.fota.trade.service.impl;

import com.alibaba.rocketmq.shade.com.alibaba.fastjson.JSONObject;
import com.fota.client.common.*;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.client.domain.query.UsdkOrderQuery;
import com.fota.client.service.UsdkOrderService;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午3:22 2018/7/6
 * @Modified:
 */
@Service
@Data
public class UsdkOrderServiceImpl implements UsdkOrderService {

    private static final Logger log = LoggerFactory.getLogger(UsdkOrderServiceImpl.class);


    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Autowired
    private UsdkOrderManager usdkOrderManager;



    @Override
    public Result<Page<UsdkOrderDTO>> listUsdkOrderByQuery(UsdkOrderQuery usdkOrderQuery) {
        Result<Page<UsdkOrderDTO>> result = Result.create();
        if (usdkOrderQuery == null || usdkOrderQuery.getUserId() == null || usdkOrderQuery.getUserId() <= 0) {
            return result.error(ResultCodeEnum.ILLEGAL_PARAM);
        }
        Page<UsdkOrderDTO> usdkOrderDTOPage = new Page<>();
        if (usdkOrderQuery.getPageNo() == null || usdkOrderQuery.getPageNo() <= 0) {
            usdkOrderQuery.setPageNo(1);
        }
        usdkOrderDTOPage.setPageNo(usdkOrderQuery.getPageNo());
        if (usdkOrderQuery.getPageSize() == null
                || usdkOrderQuery.getPageSize() <= 0
                || usdkOrderQuery.getPageSize() > Constant.DEFAULT_MAX_PAGE_SIZE) {
            usdkOrderQuery.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        usdkOrderDTOPage.setPageNo(usdkOrderQuery.getPageNo());
        usdkOrderDTOPage.setPageSize(usdkOrderQuery.getPageSize());
        int total = 0;
        try {
            total = usdkOrderMapper.countByQuery(ParamUtil.objectToMap(usdkOrderQuery));
        } catch (Exception e) {
            log.error("usdkOrderMapper.countByQuery({})", usdkOrderQuery, e);
            return result.error(ResultCodeEnum.DATABASE_EXCEPTION);
        }
        usdkOrderDTOPage.setTotal(total);
        if (total == 0) {
            return result.success(usdkOrderDTOPage);
        }
        List<UsdkOrderDO> usdkOrderDOList = null;
        try {
            usdkOrderDOList = usdkOrderMapper.listByQuery(ParamUtil.objectToMap(usdkOrderQuery));
        } catch (Exception e) {
            log.error("usdkOrderMapper.listByQuery({})", usdkOrderQuery, e);
            return result.error(ResultCodeEnum.DATABASE_EXCEPTION);
        }
        List<UsdkOrderDTO> usdkOrderDTOList = null;
        try {
            usdkOrderDTOList = BeanUtils.copyList(usdkOrderDOList, UsdkOrderDTO.class);
        } catch (Exception e) {
            log.error("bean copy exception", e);
            return result.error(ResultCodeEnum.BEAN_COPY_EXCEPTION);
        }
        usdkOrderDTOPage.setData(usdkOrderDTOList);
        return result.success(usdkOrderDTOPage);
    }

    @Override
    public ResultCode order(UsdkOrderDTO usdkOrderDTO) {
        try {
            ResultCode resultCode = usdkOrderManager.order(usdkOrderDTO);
        }catch (Exception e){
            log.error("order() failed", e);
        }
        return null;
    }

    @Override
    public ResultCode cancelOrder(Long userId, Long orderId) {
        return null;
    }

    @Override
    public ResultCode cancelAllOrder(Long userId) {
        return null;
    }
}
