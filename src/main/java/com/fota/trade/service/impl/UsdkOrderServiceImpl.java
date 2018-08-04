package com.fota.trade.service.impl;

import com.fota.asset.domain.BalanceTransferDTO;
import com.fota.asset.service.CapitalService;
import com.fota.common.Page;
import com.fota.trade.common.*;
import com.fota.trade.domain.*;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.UsdkOrderMapper;
import com.fota.trade.service.UsdkOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午3:22 2018/7/6
 * @Modified:
 */
public class UsdkOrderServiceImpl implements UsdkOrderService {

    private static final Logger log = LoggerFactory.getLogger(UsdkOrderServiceImpl.class);

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Autowired
    private UsdkOrderManager usdkOrderManager;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private CapitalService capitalService;
    private CapitalService getService() {
        return capitalService;
    }


    @Override
    public Page<UsdkOrderDTO> listUsdkOrderByQuery(BaseQuery usdkOrderQuery) {
        Page<UsdkOrderDTO> usdkOrderDTOPage = new Page<UsdkOrderDTO>();
        if (usdkOrderQuery == null || usdkOrderQuery.getUserId() <= 0) {
            return usdkOrderDTOPage;
        }
        if (usdkOrderQuery.getPageNo() <= 0) {
            usdkOrderQuery.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        usdkOrderDTOPage.setPageNo(usdkOrderQuery.getPageNo());
        if (usdkOrderQuery.getPageSize() <= 0
                || usdkOrderQuery.getPageSize() > Constant.DEFAULT_MAX_PAGE_SIZE) {
            usdkOrderQuery.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        usdkOrderDTOPage.setPageNo(usdkOrderQuery.getPageNo());
        usdkOrderDTOPage.setPageSize(usdkOrderQuery.getPageSize());
        usdkOrderQuery.setStartRow((usdkOrderQuery.getPageNo() - 1) * usdkOrderQuery.getPageSize());
        usdkOrderQuery.setEndRow(usdkOrderQuery.getPageSize());
        Map<String, Object> paramMap = null;
        int total = 0;
        try {
            paramMap = ParamUtil.objectToMap(usdkOrderQuery);
            paramMap.put("assetId", usdkOrderQuery.getSourceId());
            total = usdkOrderMapper.countByQuery(paramMap);
        } catch (Exception e) {
            log.error("usdkOrderMapper.countByQuery({})", usdkOrderQuery, e);
            return usdkOrderDTOPage;
        }
        usdkOrderDTOPage.setTotal(total);
        if (total == 0) {
            return usdkOrderDTOPage;
        }
        List<UsdkOrderDO> usdkOrderDOList = null;
        try {
            usdkOrderDOList = usdkOrderMapper.listByQuery(paramMap);
        } catch (Exception e) {
            log.error("usdkOrderMapper.listByQuery({})", usdkOrderQuery, e);
            return usdkOrderDTOPage;
        }
        List<UsdkOrderDTO> list = new ArrayList<>();
        if (usdkOrderDOList != null && usdkOrderDOList.size() > 0) {
            for (UsdkOrderDO usdkOrderDO : usdkOrderDOList) {
                list.add(BeanUtils.copy(usdkOrderDO));
            }
        }
        usdkOrderDTOPage.setData(list);
        return usdkOrderDTOPage;
    }

    @Override
    public ResultCode order(UsdkOrderDTO usdkOrderDTO) {
        ResultCode resultCode = new ResultCode();
        try {
            return usdkOrderManager.placeOrder(BeanUtils.copy(usdkOrderDTO));
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("USDK order() failed", e);
        }
        resultCode.setCode(ResultCodeEnum.DATABASE_EXCEPTION.getCode());
        resultCode.setMessage(ResultCodeEnum.DATABASE_EXCEPTION.getMessage());
        return resultCode;
    }

    @Override
    public ResultCode cancelOrder(long userId, long orderId) {
        try {
            return usdkOrderManager.cancelOrder(userId, orderId);
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                ResultCode resultCode = new ResultCode();
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("USDK cancelOrder() failed", e);
        }
        ResultCode resultCode = new ResultCode();
        resultCode.setCode(ResultCodeEnum.DATABASE_EXCEPTION.getCode());
        resultCode.setMessage(ResultCodeEnum.DATABASE_EXCEPTION.getMessage());
        return resultCode;
    }

    @Override
    public ResultCode cancelAllOrder(long userId) {
        ResultCode resultCode = new ResultCode();
        try {
            return usdkOrderManager.cancelAllOrder(userId);
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("USDK cancelAllOrder() failed", e);
        }
        resultCode.setCode(ResultCodeEnum.DATABASE_EXCEPTION.getCode());
        resultCode.setMessage(ResultCodeEnum.DATABASE_EXCEPTION.getMessage());
        return resultCode;
    }

    @Override
    public ResultCode updateOrderByMatch(UsdkMatchedOrderDTO usdkMatchedOrderDTO) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode =  usdkOrderManager.updateOrderByMatch(usdkMatchedOrderDTO);
            log.info("resultCode----------------------"+resultCode.toString());
            return resultCode;
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("USDK updateOrderByMatch() failed", e);
        }
        resultCode.setCode(ResultCodeEnum.DATABASE_EXCEPTION.getCode());
        resultCode.setMessage(ResultCodeEnum.DATABASE_EXCEPTION.getMessage());
        return resultCode;
    }

    /**
     * 如果撮合的量等于unfilled的量，则更新状态为已成
     * 如果撮合的量小于unfilled的量并且状态为已报，增更新状态为部成，
     * 更新unfilledAmount为减去成交量后的值
     * @param usdkOrderDO
     * @param filledAmount
     * @return
     */
    private int updateSingleOrderByFilledAmount(UsdkOrderDO usdkOrderDO, BigDecimal filledAmount) {
        if (usdkOrderDO.getUnfilledAmount().compareTo(filledAmount) == 0) {
            usdkOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
        } else if (usdkOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode()) {
            usdkOrderDO.setStatus(OrderStatusEnum.PART_MATCH.getCode());
        }
        usdkOrderDO.setUnfilledAmount(usdkOrderDO.getUnfilledAmount().subtract(filledAmount));
        return usdkOrderMapper.updateByPrimaryKeyAndOpLock(usdkOrderDO);
    }
}
