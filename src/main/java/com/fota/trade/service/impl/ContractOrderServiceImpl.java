package com.fota.trade.service.impl;

import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.common.ResultCodeEnum;
import com.fota.thrift.ThriftJ;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.fota.trade.domain.ResultCode;

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
    @Resource
    private UserPositionMapper userPositionMapper;

    @Autowired
    private ThriftJ thriftJ;
    @Value("${fota.asset.server.thrift.port}")
    private int thriftPort;
    @PostConstruct
    public void init() {
        thriftJ.initService("FOTA-ASSET", thriftPort);
    }

    private ContractService.Client getContractService() {
        ContractService.Client contractService =
                thriftJ.getServiceClient("FOTA-ASSET")
                        .iface(ContractService.Client.class, "contractService");
        return contractService;
    }

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
    public ResultCode order(ContractOrderDTO contractOrderDTO) {
        ResultCode resultCode = new ResultCode();
        try {
            resultCode = contractOrderManager.placeOrder(BeanUtils.copy(contractOrderDTO));
            //return resultCode;
        }catch (Exception e){
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
            log.error("Contract cancelAllOrder() failed", e);
        }
        return resultCode;
    }

    @Override
    public ResultCode updateOrderByMatch(ContractMatchedOrderDTO contractMatchedOrderDTO) {
        ResultCode resultCode = new ResultCode();
        if (contractMatchedOrderDTO == null) {
            resultCode.setCode(ResultCodeEnum.ILLEGAL_PARAM.getCode());
            return resultCode;
        }
        ContractOrderDO askUsdkOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getAskOrderId());
        ContractOrderDO bidUsdkOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getBidOrderId());
        updateContractAccount(askUsdkOrder, contractMatchedOrderDTO);
        updateContractAccount(bidUsdkOrder, contractMatchedOrderDTO);
        resultCode.setCode(ResultCodeEnum.SUCCESS.getCode());
        return resultCode;
    }

    void updateContractAccount(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO contractMatchedOrderDTO) {
        BigDecimal filledAmount = new BigDecimal(contractMatchedOrderDTO.getFilledAmount());
        BigDecimal filledPrice = new BigDecimal(contractMatchedOrderDTO.getFilledPrice());
        Long userId = contractOrderDO.getUserId();
        Long contractId = contractOrderDO.getContractId();
        UserPositionDO userPositionDO = null;
        try {
            userPositionDO = userPositionMapper.selectByUserIdAndId(userId, contractId.intValue());
        } catch (Exception e) {
            log.error("userPositionMapper.selectByUserIdAndId({}, {})", userId, contractId, e);
            return;
        }
        if (userPositionDO == null) {
            // 建仓
            buildPosition(contractOrderDO, contractMatchedOrderDTO);
            return;
        }
        long oldPositionAmount = userPositionDO.getUnfilledAmount();
        if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
            //todo 成交单和持仓是同方向
            long newTotalAmount = userPositionDO.getUnfilledAmount() + filledAmount.longValue();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
            updateUserPosition(userPositionDO, oldTotalPrice, addedTotalPrice, newTotalAmount);
            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO);
            return;
        }

        //成交单和持仓是反方向 （平仓）
        if (filledAmount.longValue() - userPositionDO.getUnfilledAmount() <= 0) {
            //todo 不改变仓位方向
            long newTotalAmount = userPositionDO.getUnfilledAmount() - filledAmount.longValue();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount).negate();
            updateUserPosition(userPositionDO, oldTotalPrice, addedTotalPrice, newTotalAmount);
            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO);
        } else {
            //todo 改变仓位方向
            long newTotalAmount = filledAmount.longValue() - userPositionDO.getUnfilledAmount();
            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount())).negate();
            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
            userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
            updateUserPosition(userPositionDO, oldTotalPrice, addedTotalPrice, newTotalAmount);
            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO);
        }
    }

    private void buildPosition(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO matchedOrderDTO) {
        UserPositionDO newUserPositionDO = new UserPositionDO();
        newUserPositionDO.setPositionType(contractOrderDO.getOrderDirection());
        newUserPositionDO.setAveragePrice(new BigDecimal(matchedOrderDTO.getFilledPrice()));
        newUserPositionDO.setUnfilledAmount(matchedOrderDTO.getFilledAmount());
        newUserPositionDO.setStatus(1);
        newUserPositionDO.setUserId(contractOrderDO.getUserId());
        newUserPositionDO.setContractName(contractOrderDO.getContractName());
        newUserPositionDO.setContractId(contractOrderDO.getContractId());
        //todo fixme 根据接口获取杠杆
        newUserPositionDO.setLever(10);
        try {
            userPositionMapper.insert(newUserPositionDO);
        } catch (Exception e) {
            log.error("userPositionMapper.insert({})", newUserPositionDO, e);
        }
        updateBalance(contractOrderDO, 0L, matchedOrderDTO.getFilledAmount(), matchedOrderDTO);
    }

    private int updateUserPosition(UserPositionDO userPositionDO, BigDecimal oldTotalPrice, BigDecimal addedTotalPrice, long newTotalAmount) {
        BigDecimal newTotalPrice = oldTotalPrice.add(addedTotalPrice);
        BigDecimal newAvaeragePrice = newTotalPrice.divide(new BigDecimal(newTotalAmount));
        userPositionDO.setUnfilledAmount(newTotalAmount);
        userPositionDO.setAveragePrice(newAvaeragePrice);
        int updateRet = 0;
        try {
            updateRet = userPositionMapper.updateByPrimaryKey(userPositionDO);
        } catch (Exception e) {
            log.error("userPositionMapper.insert({})", userPositionDO, e);
        }
        return updateRet;
    }


    //todo 合约账户amoutn: + (oldPositionAmount - 当前持仓)*合约价格 - 手续费
    //todo 合约账户冻结：解冻委托价*合约份数 + 手续费
    private int updateBalance(ContractOrderDO contractOrderDO,
                              long oldPositionAmount,
                              long newPositionAmount,
                              ContractMatchedOrderDTO matchedOrderDTO){
        long filledAmount = matchedOrderDTO.getFilledAmount();
        BigDecimal filledPrice = new BigDecimal(matchedOrderDTO.getFilledPrice());
        BigDecimal fee = contractOrderDO.getFee();
        BigDecimal actualFee = filledPrice.multiply(new BigDecimal(filledAmount)).multiply(fee).multiply(new BigDecimal(0.01));
        BigDecimal addedTotalAmount = new BigDecimal(oldPositionAmount - newPositionAmount)
                .multiply(filledPrice)
                .multiply(new BigDecimal(0.01))
                .multiply(new BigDecimal(0.1))
                .subtract(actualFee);
        BigDecimal entrustFee = contractOrderDO.getPrice().multiply(new BigDecimal(filledAmount)).multiply(fee).multiply(new BigDecimal(0.01));
        BigDecimal addedTotalLocked = new BigDecimal(filledAmount)
                .multiply(contractOrderDO.getPrice())
                .multiply(new BigDecimal(0.01))
                .multiply(new BigDecimal(0.1))
                .add(entrustFee).negate();
        //todo 更新余额s
        try {
            getContractService().updateContractBalance(contractOrderDO.getUserId(),
                    addedTotalAmount.toString(),
                    addedTotalLocked.toString());
        } catch (Exception e) {

        }
        updateSingleOrderByFilledAmount(contractOrderDO, matchedOrderDTO.getFilledAmount());
        return 1;
    }

    /**
     * 如果撮合的量等于unfilled的量，则更新状态为已成
     * 如果撮合的量小于unfilled的量并且状态为已报，增更新状态为部成，
     * 更新unfilledAmount为减去成交量后的值
     * @param contractOrderDO
     * @param filledAmount
     * @return
     */
    private int updateSingleOrderByFilledAmount(ContractOrderDO contractOrderDO, long filledAmount) {
        if (contractOrderDO.getUnfilledAmount() == filledAmount) {
            contractOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
        } else if (contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode()) {
            contractOrderDO.setStatus(OrderStatusEnum.PART_MATCH.getCode());
        }
        contractOrderDO.setUnfilledAmount(contractOrderDO.getUnfilledAmount() - filledAmount);
        return contractOrderMapper.updateByPrimaryKey(contractOrderDO);
    }
}
