package com.fota.trade.service;

import com.fota.client.domain.UsdkOrderDTO;
import com.fota.common.Page;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.domain.BaseQuery;
import com.fota.trade.domain.BaseQuery;
import com.fota.trade.domain.UsdkMatchedOrderDTO;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.UsdkOrderMapper;
import com.fota.trade.service.impl.UsdkOrderServiceImpl;
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
    private UsdkOrderServiceImpl usdkOrderService;
    @Resource
    private UsdkOrderMapper usdkOrderMapper;

    @Test
    public void testListUsdkOrderByQuery() throws Exception {
        BaseQuery usdkOrderQuery = new BaseQuery();
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        usdkOrderQuery.setUserId(9527L);
        com.fota.common.Page<com.fota.trade.domain.UsdkOrderDTO> result = usdkOrderService.listUsdkOrderByQuery(usdkOrderQuery);
        Assert.assertTrue(result != null && result.getData() != null && result.getData() != null);
    }

    @Test
    public void testListUsdkOrderByQueryWithStatus() throws Exception {
        BaseQuery usdkOrderQuery = new BaseQuery();
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        usdkOrderQuery.setUserId(9527L);
        List<Integer> orderStatus = new ArrayList<>();
        orderStatus.add(OrderStatusEnum.COMMIT.getCode());
        usdkOrderQuery.setOrderStatus(orderStatus);
        com.fota.common.Page<com.fota.trade.domain.UsdkOrderDTO> result = usdkOrderService.listUsdkOrderByQuery(usdkOrderQuery);
        Assert.assertTrue(result != null && result.getData() != null && result.getData() != null);
    }

    @Test
    public void testUpdateOrderByMatch() throws Exception {

//        UsdkOrderDO askUsdkOrderDO = usdkOrderMapper.selectByPrimaryKey(8L);
//        UsdkOrderDTO askUsdkOrderDTO = new UsdkOrderDTO();
//        BeanUtils.copy(askUsdkOrderDO, askUsdkOrderDTO);
//        UsdkOrderDO bidUsdkOrderDO = usdkOrderMapper.selectByPrimaryKey(9L);
//        UsdkOrderDTO bidUsdkOrderDTO = new UsdkOrderDTO();
//        BeanUtils.copy(bidUsdkOrderDO, bidUsdkOrderDTO);
//        com.fota.trade.domain.UsdkMatchedOrderDTO usdkMatchedOrderDTO = new UsdkMatchedOrderDTO();
//        usdkMatchedOrderDTO.setFilledAmount(new BigDecimal("1"));
//        usdkMatchedOrderDTO.setFilledPrice(new BigDecimal("11"));
//        usdkMatchedOrderDTO.setBidOrderPrice(new BigDecimal("12"));
//        usdkMatchedOrderDTO.setAssetId(1);
//
//        usdkMatchedOrderDTO.setAskUsdkOrder(askUsdkOrderDTO);
//        usdkMatchedOrderDTO.setBidUsdkOrder(bidUsdkOrderDTO);
//        ResultCode resultCode = usdkOrderService.updateOrderByMatch(usdkMatchedOrderDTO);

//        Assert.assertTrue(resultCode != null && resultCode.isSuccess());
    }

}
