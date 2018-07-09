package com.fota.trade.service.impl;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.common.ResultCode;
import com.fota.client.common.ResultCodeEnum;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.client.domain.query.ContractOrderQuery;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.client.service.ContractOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午11:16 2018/7/5
 * @Modified:
 */
@Service("ContractOrderService")
public class ContractOrderServiceImpl implements ContractOrderService {

    private static final Logger log = LoggerFactory.getLogger(ContractOrderServiceImpl.class);

    @Resource
    private ContractOrderMapper contractOrderMapper;

    @Override
    public Result<Page<ContractOrderDTO>> listContractOrderByQuery(ContractOrderQuery contractOrderQuery) {

        Result<Page<ContractOrderDTO>> result = Result.create();
        if (contractOrderQuery == null || contractOrderQuery.getUserId() == null || contractOrderQuery.getUserId() <= 0) {
            return result.error(ResultCodeEnum.ILLEGAL_PARAM);
        }
        Page<ContractOrderDTO> contractOrderDTOPage = new Page<>();
        if (contractOrderQuery.getPageNo() == null || contractOrderQuery.getPageNo() <= 0) {
            contractOrderQuery.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        contractOrderDTOPage.setPageNo(contractOrderQuery.getPageNo());
        if (contractOrderQuery.getPageSize() == null
                || contractOrderQuery.getPageSize() <= 0
                || contractOrderQuery.getPageSize() > Constant.DEFAULT_MAX_PAGE_SIZE) {
            contractOrderQuery.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        contractOrderDTOPage.setPageNo(contractOrderQuery.getPageNo());
        contractOrderDTOPage.setPageSize(contractOrderQuery.getPageSize());
        int total = 0;
        try {
            total = contractOrderMapper.countByQuery(ParamUtil.objectToMap(contractOrderQuery));
        } catch (Exception e) {
            log.error("contractOrderMapper.countByQuery({})", contractOrderQuery, e);
            return result.error(ResultCodeEnum.DATABASE_EXCEPTION);
        }
        contractOrderDTOPage.setTotal(total);
        if (total == 0) {
            return result.success(contractOrderDTOPage);
        }
        List<ContractOrderDO> contractOrderDOList = null;
        try {
            contractOrderDOList = contractOrderMapper.listByQuery(ParamUtil.objectToMap(contractOrderQuery));
        } catch (Exception e) {
            log.error("contractOrderMapper.listByQuery({})", contractOrderQuery, e);
            return result.error(ResultCodeEnum.DATABASE_EXCEPTION);
        }
        List<ContractOrderDTO> contractOrderDTOList = null;
        try {
            contractOrderDTOList = BeanUtils.copyList(contractOrderDOList, ContractOrderDTO.class);
        } catch (Exception e) {
            log.error("bean copy exception", e);
            return result.error(ResultCodeEnum.BEAN_COPY_EXCEPTION);
        }
        contractOrderDTOPage.setData(contractOrderDTOList);
        return result.success(contractOrderDTOPage);
    }

    @Override
    public ResultCode order(ContractOrderDTO contractOrderDTO) {

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
