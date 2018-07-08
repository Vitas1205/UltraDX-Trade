package com.fota.trade.service;

import com.fota.client.common.Page;
import com.fota.client.common.Result;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.UsdkMatchedOrderDTO;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.client.domain.query.UsdkOrderQuery;
import com.fota.client.service.UsdkOrderService;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderStatusEnum;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class UsdkOrderServiceTest {

    @Resource
    private UsdkOrderService usdkOrderService;

    @Test
    public void testListUsdkOrderByQuery() throws Exception {
        UsdkOrderQuery usdkOrderQuery = new UsdkOrderQuery();
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        usdkOrderQuery.setUserId(9527L);
        Result<Page<UsdkOrderDTO>> result = usdkOrderService.listUsdkOrderByQuery(usdkOrderQuery);
        Assert.assertTrue(result != null && result.getData() != null && result.getData().getData() != null);
    }

    @Test
    public void testListUsdkOrderByQueryWithStatus() throws Exception {
        UsdkOrderQuery usdkOrderQuery = new UsdkOrderQuery();
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        usdkOrderQuery.setUserId(9527L);
        List<Integer> orderStatus = new ArrayList<>();
        orderStatus.add(OrderStatusEnum.COMMIT.getCode());
        usdkOrderQuery.setOrderStatus(orderStatus);
        Result<Page<UsdkOrderDTO>> result = usdkOrderService.listUsdkOrderByQuery(usdkOrderQuery);
        Assert.assertTrue(result != null && result.getData() != null && result.getData().getData() != null);
    }

    @Test
    public void testUpdateOrderByMatch() throws Exception {
        UsdkMatchedOrderDTO usdkMatchedOrderDTO = new UsdkMatchedOrderDTO();
        ResultCode resultCode = usdkOrderService.updateOrderByMatch(usdkMatchedOrderDTO);
        Assert.assertTrue(resultCode != null && resultCode.isSuccess());
    }

}
