package com.fota.trade.manager;

import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.common.Result;
import com.fota.common.enums.FotaApplicationEnum;
import com.fota.match.domain.TradeUsdkOrder;
import com.fota.match.service.UsdkMatchedOrderService;
import com.fota.trade.client.PlaceCoinOrderDTO;
import com.fota.trade.client.PlaceOrderRequest;
import com.fota.trade.client.PlaceOrderResult;
import com.fota.trade.client.UserLevelEnum;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderTypeEnum;
import com.fota.trade.mapper.UsdkOrderMapper;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

/**
 * @author huangtao 2018/8/23 下午6:41
 */
@RunWith(SpringRunner.class)
public class UsdkOrderManagerTest {

    private UsdkMatchedOrderService usdkMatchedOrderService;

    private RedisManager redisManager;

    @Mock
    private UsdkOrderMapper usdkOrderMapper;

    @InjectMocks
    private UsdkOrderManager usdkOrderManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @Ignore
    public void getJudegRetTest(){
        TradeUsdkOrder tradeUsdkOrder = new TradeUsdkOrder();
        tradeUsdkOrder.setId(444L);
        tradeUsdkOrder.setAssetId(2);
        tradeUsdkOrder.setOrderDirection(2);
//        usdkMatchedOrderService.cancelOrderUsdk(tradeUsdkOrder);
    }

    @Test
    public void test_batch_exceed_200_limit() throws Exception {
        List<PlaceCoinOrderDTO> placeCoinOrderDTOS = new ArrayList<>();
        PlaceCoinOrderDTO placeCoinOrderDTO = new PlaceCoinOrderDTO();
        placeCoinOrderDTO.setSubjectId((long) AssetTypeEnum.BTC.getCode());
        placeCoinOrderDTO.setPrice(BigDecimal.valueOf(1000));
        placeCoinOrderDTO.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        placeCoinOrderDTO.setSubjectName(AssetTypeEnum.BTC.getDesc());
        placeCoinOrderDTO.setExtOrderId("1234");
        placeCoinOrderDTO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        placeCoinOrderDTO.setTotalAmount(BigDecimal.valueOf(1000));
        placeCoinOrderDTOS.add(placeCoinOrderDTO);
        PlaceOrderRequest<PlaceCoinOrderDTO> placeOrderRequest = new PlaceOrderRequest<>();
        placeOrderRequest.setUserId(100L);
        placeOrderRequest.setPlaceOrderDTOS(placeCoinOrderDTOS);
        placeOrderRequest.setCaller(FotaApplicationEnum.TRADE);
        placeOrderRequest.setUserLevel(UserLevelEnum.DEFAULT);
        when(usdkOrderMapper.countByQuery(anyMap())).thenReturn(200);
        Result<List<PlaceOrderResult>> result = usdkOrderManager.batchOrder(placeOrderRequest);
        assertEquals(result.getCode(), ResultCodeEnum.TOO_MUCH_ORDERS.getCode());
    }

    @Test
    @Ignore
    public void test_send_cancel_msg() {
        usdkOrderManager.sendCancelReq(Arrays.asList(715669044238909L, 751658069641829L), 282L);
    }
}
