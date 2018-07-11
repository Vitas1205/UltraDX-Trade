package com.fota.trade.service.impl;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.common.ResultCode;
import com.fota.client.common.ResultCodeEnum;
import com.fota.client.domain.ContractMatchedOrderDTO;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.client.domain.query.ContractOrderQuery;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.ContractOrderDTOPage;
import com.fota.trade.domain.ContractOrderQueryDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.client.service.ContractOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
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
public class ContractOrderServiceImpl implements com.fota.trade.service.ContractOrderService.Iface {

    private static final Logger log = LoggerFactory.getLogger(ContractOrderServiceImpl.class);

    @Resource
    private ContractOrderMapper contractOrderMapper;

    @Resource
    private ContractOrderManager contractOrderManager;

    @Override
    public ContractOrderDTOPage listContractOrderByQuery(ContractOrderQueryDTO contractOrderQueryDTO) {
        ContractOrderDTOPage contractOrderDTOPageRet = new ContractOrderDTOPage();
        Result<Page<ContractOrderDTO>> result = Result.create();
        if (contractOrderQueryDTO.getUserId() <= 0) {
            return null;
        }
        ContractOrderDTOPage contractOrderDTOPage = new ContractOrderDTOPage();
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
        contractOrderQueryDTO.setEndRow(contractOrderQueryDTO.getStartRow() + contractOrderQueryDTO.getPageSize());
        int total = 0;
        try {
            total = contractOrderMapper.countByQuery(ParamUtil.objectToMap(contractOrderQueryDTO));
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
            contractOrderDOList = contractOrderMapper.listByQuery(ParamUtil.objectToMap(contractOrderQueryDTO));
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

    @Override
    public com.fota.trade.domain.ResultCode order(com.fota.trade.domain.ContractOrderDTO contractOrderDTO) {
//        try {
//            ResultCode resultCode = new ResultCode();
//            resultCode = contractOrderManager.placeOrder(contractOrderDTO);
//            return resultCode;
//        }catch (Exception e){
//            log.error("Contract order() failed", e);
//        }
//        return null;
        return null;
    }

    @Override
    public com.fota.trade.domain.ResultCode cancelOrder(long userId, long orderId) {
//        try {
//            ResultCode resultCode = new ResultCode();
//            resultCode = contractOrderManager.cancelOrder(userId, orderId);
//            return resultCode;
//        }catch (Exception e){
//            log.error("Contract cancelOrder() failed", e);
//        }
        return null;
    }

    @Override
    public com.fota.trade.domain.ResultCode cancelAllOrder(long userId) {
        return null;
    }



    @Override
    public com.fota.trade.domain.ResultCode updateOrderByMatch(com.fota.trade.domain.ContractMatchedOrderDTO contractMatchedOrderDTO) {
//        if (contractMatchedOrderDTO == null) {
//            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM);
//        }
//        UsdkOrderDTO askUsdkOrder = contractMatchedOrderDTO.getAskUsdkOrder();
//        UsdkOrderDTO bidUsdkOrder = contractMatchedOrderDTO.getBidUsdkOrder();
//        BigDecimal filledAmount = contractMatchedOrderDTO.getFilledAmount();
//        BigDecimal filledPrice = contractMatchedOrderDTO.getFilledPrice();
//        updateSingleOrderByFilledAmount(askUsdkOrder, filledAmount);
//        updateSingleOrderByFilledAmount(bidUsdkOrder, filledAmount);
//

        return null;
    }
}
