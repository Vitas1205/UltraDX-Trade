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

    public ResultCode placeOrder(ContractOrderDTO contractOrderDTO){
        ResultCode resultCode = new ResultCode();
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        BeanUtils.copyProperties(contractOrderDTO,contractOrderDO);
        Integer contractId = contractOrderDTO.getContractId();
        Long userId = contractOrderDTO.getUserId();
        Long totalAmount = contractOrderDTO.getTotalAmount();
        BigDecimal price = contractOrderDTO.getPrice();
        Integer orderDirection = contractOrderDTO.getOrderDirection();
        BigDecimal orderValue = BigDecimal.valueOf(totalAmount).multiply(price);
        BigDecimal feeValue = orderValue.multiply(contractFee);
        UserPositionDO userPositionDO = new UserPositionDO();
        //查询持仓表
        userPositionDO = userPositionMapper.selectByUserIdAndId(userId,contractId);
        Date gmtModified = userPositionDO.getGmtModified();
        if (userPositionDO != null && orderDirection == 2 && userPositionDO.getPositionType() == 2){
            Long positionUnfilledAmount = userPositionDO.getUnfilledAmount();
            if (totalAmount.compareTo(positionUnfilledAmount) <= 0){
                //更新持仓表

            }
        }

        return resultCode;
    }
}
