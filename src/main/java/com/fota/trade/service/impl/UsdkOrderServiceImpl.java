package com.fota.trade.service.impl;

import com.fota.client.common.Page;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.client.domain.query.UsdkOrderQuery;
import com.fota.client.service.UsdkOrderService;
import com.fota.trade.manager.UsdkOrderManager;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


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
    public Page<UsdkOrderDTO> listUsdkOrderByQuery(UsdkOrderQuery usdkOrderQuery) {
        return null;
    }

    @Override
    public ResultCode order(UsdkOrderDTO usdkOrderDTO) {
        try {
            ResultCode resultCode = usdkOrderManager.orderImp(usdkOrderDTO);
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
