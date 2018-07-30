package com.fota.trade.service.impl;

import com.alibaba.fastjson.JSON;
import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.client.common.Page;
import com.fota.client.common.ResultCodeEnum;
import com.fota.client.domain.CompetitorsPriceDTO;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.BusinessException;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.ContractLeverManager;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.ContractOrderService;
import com.fota.trade.util.PriceUtil;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午11:16 2018/7/5
 * @Modified:
 */
public class ContractOrderServiceImpl implements ContractOrderService {

    private static final Logger log = LoggerFactory.getLogger(ContractOrderServiceImpl.class);

    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Autowired
    private ContractOrderManager contractOrderManager;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private ContractLeverManager contractLeverManager;

    @Autowired
    private AssetService assetService;
    private AssetService getAssetService() { return assetService; }

    @Autowired
    private ContractService contractService;
    private ContractService getContractService() {
        return contractService;
    }


    @Override
    public com.fota.common.Page<ContractOrderDTO> listContractOrderByQuery(BaseQuery contractOrderQueryDTO) {
        com.fota.common.Page<ContractOrderDTO> contractOrderDTOPageRet = new com.fota.common.Page<>();
        if (contractOrderQueryDTO.getUserId() <= 0) {
            return null;
        }
        com.fota.common.Page<ContractOrderDTO> contractOrderDTOPage = new com.fota.common.Page<>();
        if (contractOrderQueryDTO.getPageNo() <= 0) {
            contractOrderQueryDTO.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        contractOrderDTOPage.setPageNo(contractOrderQueryDTO.getPageNo());
        if (contractOrderQueryDTO.getPageSize() <= 0
                || contractOrderQueryDTO.getPageSize() > Constant.DEFAULT_MAX_PAGE_SIZE) {
            contractOrderQueryDTO.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        contractOrderDTOPage.setPageNo(contractOrderQueryDTO.getPageNo());
        contractOrderDTOPage.setPageSize(contractOrderQueryDTO.getPageSize());
        contractOrderQueryDTO.setStartRow((contractOrderQueryDTO.getPageNo() - 1) * contractOrderQueryDTO.getPageSize());
        contractOrderQueryDTO.setEndRow(contractOrderQueryDTO.getPageSize());

        Map<String, Object> paramMap = null;

        int total = 0;
        try {
            paramMap = ParamUtil.objectToMap(contractOrderQueryDTO);
            paramMap.put("contractId", contractOrderQueryDTO.getSourceId());
            total = contractOrderMapper.countByQuery(paramMap);
        } catch (Exception e) {
            log.error("contractOrderMapper.countByQuery({})", contractOrderQueryDTO, e);
            return contractOrderDTOPageRet;
        }
        contractOrderDTOPage.setTotal(total);
        if (total == 0) {
            return contractOrderDTOPageRet;
        }
        List<ContractOrderDO> contractOrderDOList = null;
        List<com.fota.trade.domain.ContractOrderDTO> list = new ArrayList<>();
        try {
            contractOrderDOList = contractOrderMapper.listByQuery(paramMap);
            if (contractOrderDOList != null && contractOrderDOList.size() > 0) {

                for (ContractOrderDO contractOrderDO : contractOrderDOList) {
                    list.add(BeanUtils.copy(contractOrderDO));
                }
            }
        } catch (Exception e) {
            log.error("contractOrderMapper.listByQuery({})", contractOrderQueryDTO, e);
            return contractOrderDTOPageRet;
        }
        List<ContractOrderDTO> contractOrderDTOList = null;
//        try {
//            contractOrderDTOList = BeanUtils.copyList(contractOrderDOList, ContractOrderDTO.class);
//        } catch (Exception e) {
//            log.error("bean copy exception", e);
//            return contractOrderDTOPageRet
//        }
        contractOrderDTOPage.setData(list);
        return contractOrderDTOPage;
    }

    /*@Override
    public ResultCode order(ContractOrderDTO contractOrderDTO) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.placeOrder(BeanUtils.copy(contractOrderDTO));
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("Contract order() failed", e);
        }
        return resultCode;
    }*/

    @Override
    public ResultCode order(ContractOrderDTO contractOrderDTO) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.placeOrder(BeanUtils.copy(contractOrderDTO));
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("Contract order() failed", e);
        }
        return resultCode;
    }

    @Override
    public ResultCode cancelOrder(long userId, long orderId) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.cancelOrder(userId, orderId);
            return resultCode;
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("Contract cancelOrder() failed", e);
        }
        return resultCode;
    }

    @Override
    public ResultCode cancelAllOrder(long userId) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.cancelAllOrder(userId);
            return resultCode;
        }catch (Exception e){
            if (e instanceof BusinessException){
                BusinessException businessException = (BusinessException) e;
                resultCode.setCode(businessException.getCode());
                resultCode.setMessage(businessException.getMessage());
                return resultCode;
            }
            log.error("Contract cancelAllOrder() failed", e);
        }
        return resultCode;
    }

    @Override
    public ResultCode updateOrderByMatch(ContractMatchedOrderDTO contractMatchedOrderDTO) {
        ResultCode resultCode = new ResultCode();
        resultCode = contractOrderManager.updateOrderByMatch(contractMatchedOrderDTO);
        return resultCode;
    }

    private void updateContractAccount(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO contractMatchedOrderDTO) {
    }

    private void buildPosition(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO matchedOrderDTO) {
    }

    private int updateUserPosition(UserPositionDO userPositionDO, BigDecimal oldTotalPrice, BigDecimal addedTotalPrice, long newTotalAmount) {
        return 0;
    }


    //todo 合约账户amoutn: + (oldPositionAmount - 当前持仓)*合约价格 - 手续费
    //todo 合约账户冻结：解冻委托价*合约份数 + 手续费
    private int updateBalance(ContractOrderDO contractOrderDO,
                              long oldPositionAmount,
                              long newPositionAmount,
                              ContractMatchedOrderDTO matchedOrderDTO){
        return 0;
    }

    /**
     * 如果撮合的量等于unfilled的量，则更新状态为已成
     * 如果撮合的量小于unfilled的量并且状态为已报，增更新状态为部成，
     * 更新unfilledAmount为减去成交量后的值
     * @param contractOrderDO
     * @param filledAmount
     * @return
     */
    private int updateSingleOrderByFilledAmount(ContractOrderDO contractOrderDO, long filledAmount, String filledPrice) {

        /*if (contractOrderDO.getUnfilledAmount() == filledAmount) {
            contractOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
        } else if (contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode()) {
            contractOrderDO.setStatus(OrderStatusEnum.PART_MATCH.getCode());
        }*/
        //contractOrderDO.setUnfilledAmount(contractOrderDO.getUnfilledAmount() - filledAmount);
        int ret = -1;
        try {
            log.info("打印的内容----------------------"+contractOrderDO);
            BigDecimal averagePrice = PriceUtil.getAveragePrice(contractOrderDO.getAveragePrice(),
                    new BigDecimal(contractOrderDO.getTotalAmount()).subtract(new BigDecimal(contractOrderDO.getUnfilledAmount())),
                    new BigDecimal(filledAmount),
                    new BigDecimal(filledPrice));
            ret = contractOrderMapper.updateByFilledAmount(contractOrderDO.getId(), contractOrderDO.getStatus(), filledAmount, averagePrice);
        }catch (Exception e){
            log.error("失败({})", contractOrderDO, e);
        }
        return ret;
    }
}
