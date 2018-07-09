package com.fota.trade.manager;

import com.fota.client.common.ResultCode;
import com.fota.client.domain.ContractOrderDTO;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.trade.domain.ContractOrderDO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.UserPositionDO;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Component
@Slf4j
public class ContractOrderManager {

    private static BigDecimal contractFee = BigDecimal.valueOf(0.001);

    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Autowired
    private UserPositionMapper userPositionMapper;

    public List<ContractOrderDO> listNotMatchOrder(Long contractOrderIndex, Integer orderDirection) {
        List<ContractOrderDO> notMatchOrderList = null;
        try {
            notMatchOrderList = contractOrderMapper.notMatchOrderList(
                    OrderStatusEnum.COMMIT.getCode(), OrderStatusEnum.PART_MATCH.getCode(), contractOrderIndex, orderDirection);
        } catch (Exception e) {
            log.error("contractOrderMapper.notMatchOrderList error", e);
        }
        if (notMatchOrderList == null) {
            notMatchOrderList = new ArrayList<>();
        }
        return notMatchOrderList;
    }

    @Transactional(rollbackFor = {Exception.class ,RuntimeException.class})
    public ResultCode placeOrder(ContractOrderDTO contractOrderDTO){
        ResultCode resultCode = new ResultCode();
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        BeanUtils.copyProperties(contractOrderDTO,contractOrderDO);
        if (contractOrderDO == null){
            throw new RuntimeException("contractOrder is required not null ,placeOrder failed");
        }
        Integer contractId = contractOrderDTO.getContractId();
        Long userId = contractOrderDTO.getUserId();
        Long totalAmount = contractOrderDTO.getTotalAmount();
        BigDecimal price = contractOrderDTO.getPrice();
        Integer orderDirection = contractOrderDTO.getOrderDirection();
        BigDecimal orderValue = BigDecimal.valueOf(totalAmount).multiply(price);
        BigDecimal totalFeeValue = orderValue.multiply(contractFee);
        BigDecimal totalValue = orderValue.add(totalFeeValue);
        UserPositionDO userPositionDO = new UserPositionDO();
        //查询持仓表
        userPositionDO = userPositionMapper.selectByUserIdAndId(userId,contractId);
        if (userPositionDO != null){

        }

        //插入合约订单
        int insertContractOrderRet = contractOrderMapper.insertSelective(contractOrderDO);
        if (insertContractOrderRet <= 0){
            throw new RuntimeException("insert contractOrder failed");
        }
        resultCode = ResultCode.success();
        return resultCode;
    }


    public ResultCode cancelOrder(Long userId, Long orderId){
        ResultCode resultCode = new ResultCode();
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByIdAndUserId(orderId, userId);
        Integer status = contractOrderDO.getStatus();
        if (status == OrderStatusEnum.COMMIT.getCode() || status == OrderStatusEnum.CANCEL.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.CANCEL.getCode());
        }else if (status == OrderStatusEnum.PART_MATCH.getCode() || status == OrderStatusEnum.PART_CANCEL.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.PART_CANCEL.getCode());
        }else if (status == OrderStatusEnum.MATCH.getCode()){
            contractOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
        }else {
            resultCode = ResultCode.error(2,"contractOrder status illegal");
        }
        int ret = contractOrderMapper.updateByOpLock(contractOrderDO);
        if (ret > 0){
            //解冻对应
        }
        return resultCode;
    }




}
