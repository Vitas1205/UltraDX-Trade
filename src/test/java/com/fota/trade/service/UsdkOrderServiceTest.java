package com.fota.trade.service;

import com.fota.asset.domain.enums.AssetTypeEnum;
import com.fota.common.Page;
import com.fota.common.Result;
import com.fota.common.enums.FotaApplicationEnum;
import com.fota.trade.client.*;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderPriceTypeEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.domain.enums.OrderTypeEnum;
import com.fota.trade.mapper.UsdkOrderMapper;
import com.fota.trade.service.impl.UsdkOrderServiceImpl;
import com.fota.trade.util.BasicUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.fota.trade.common.TestConfig.userId;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@RunWith(SpringRunner.class)
@SpringBootTest
//@Transactional
public class UsdkOrderServiceTest {

    @Resource
    private UsdkOrderServiceImpl usdkOrderService;
    @Resource
    private UsdkOrderMapper usdkOrderMapper;
    UsdkOrderDO usdkOrderDO = new UsdkOrderDO();
    //@Before
    public void init(){
        usdkOrderDO.setId(BasicUtils.generateId());
        usdkOrderDO.setAssetId(2);
        usdkOrderDO.setAssetName("BTC");
        usdkOrderDO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        usdkOrderDO.setUserId(userId);
        usdkOrderDO.setOrderType(OrderPriceTypeEnum.LIMIT.getCode());
        usdkOrderDO.setTotalAmount(new BigDecimal("0.01"));
        usdkOrderDO.setUnfilledAmount(new BigDecimal("0.01"));
        usdkOrderDO.setPrice(new BigDecimal("6000.1"));
        usdkOrderDO.setFee(new BigDecimal("1.1"));
        usdkOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        usdkOrderDO.setGmtModified(new Date(System.currentTimeMillis()));
        usdkOrderDO.setGmtCreate(new Date(System.currentTimeMillis()));
        int insertRet = usdkOrderMapper.insert(usdkOrderDO);
        Assert.assertTrue(insertRet > 0);
    }
    @Test
    public void testListUsdkOrderByQuery() throws Exception {
        BaseQuery usdkOrderQuery = new BaseQuery();
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        usdkOrderQuery.setUserId(userId);
        com.fota.common.Page<com.fota.trade.domain.UsdkOrderDTO> result = usdkOrderService.listUsdkOrderByQuery(usdkOrderQuery);
        Assert.assertTrue(result != null);
    }

    @Test
    public void testCancelOrder() throws Exception {
        Long orderId = 715669044238909L;
        Long userId = 282L;
        Map<String, String> userInfoMap = new HashMap<>();
        //usdkOrderService.cancelOrder(userId, orderId, userInfoMap);
    }

    @Test
    public void testListUsdkOrderByQueryWithStatus() throws Exception {
        BaseQuery usdkOrderQuery = new BaseQuery();
        usdkOrderQuery.setPageSize(20);
        usdkOrderQuery.setPageNo(1);
        usdkOrderQuery.setUserId(200L);
        List<Integer> orderStatus = new ArrayList<>();
        orderStatus.add(OrderStatusEnum.COMMIT.getCode());
        orderStatus.add(OrderStatusEnum.MATCH.getCode());

        usdkOrderQuery.setOrderStatus(orderStatus);
        com.fota.common.Page<com.fota.trade.domain.UsdkOrderDTO> result = usdkOrderService.listUsdkOrderByQuery(usdkOrderQuery);
//        Assert.assertTrue(result != null && result.getData() != null && result.getData() != null);
    }

    @Test
    public void testOrderReturnId(){
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        usdkOrderDTO.setCompleteAmount(new BigDecimal(0));
        usdkOrderDTO.setUserId(282L);
        usdkOrderDTO.setAssetId(1);
        usdkOrderDTO.setAssetName("BTC");
        usdkOrderDTO.setAveragePrice(new BigDecimal(0));
        usdkOrderDTO.setFee(new BigDecimal(0.01));
        usdkOrderDTO.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        usdkOrderDTO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        usdkOrderDTO.setGmtCreate(new Date());
        usdkOrderDTO.setPrice(new BigDecimal(6000));
        usdkOrderDTO.setUnfilledAmount(new BigDecimal(10));
        usdkOrderDTO.setTotalAmount(new BigDecimal(10));
        usdkOrderDTO.setMatchAmount("0");
        //com.fota.common.Result result = usdkOrderService.orderReturnId(usdkOrderDTO, new HashMap<>());
        //System.out.println(result.getData());
       // assert result.isSuccess()
                //&& (long)result.getData() >0;
    }

    @Test
    public void testPlaceOrder(){
        int assetId = 4;
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        usdkOrderDTO.setUserId(userId);
        usdkOrderDTO.setAssetId(assetId);
        usdkOrderDTO.setAssetName("FOTA");
        usdkOrderDTO.setAveragePrice(new BigDecimal(0));
        usdkOrderDTO.setFee(Constant.FEE_RATE);
        usdkOrderDTO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        usdkOrderDTO.setOrderType(OrderTypeEnum.PASSIVE.getCode());
        int scale =AssetTypeEnum.getUsdkPricePrecisionByAssetId(usdkOrderDTO.getAssetId());
        usdkOrderDTO.setPrice(new BigDecimal(0.05).setScale(scale, RoundingMode.DOWN));
        usdkOrderDTO.setTotalAmount(new BigDecimal(2));
        Map<String, Object> map = new HashMap();
        usdkOrderDTO.setOrderContext(map);
        Map<String, String> map2 =  new HashMap<String, String>();
        map2.put("username", "harry");
        com.fota.trade.domain.ResultCode result = usdkOrderService.order(usdkOrderDTO, map2);
        assert result.isSuccess();



    }

//    @Test
    public void testUpdateOrderByMatch() throws Exception {


        UsdkOrderDO askOrderDO = new UsdkOrderDO();
        askOrderDO.setId(BasicUtils.generateId());
        askOrderDO.setAssetId(2);
        askOrderDO.setAssetName("BTC");
        askOrderDO.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        askOrderDO.setUserId(274L);
        askOrderDO.setOrderType(OrderPriceTypeEnum.LIMIT.getCode());
        askOrderDO.setTotalAmount(new BigDecimal("0.01"));
        askOrderDO.setUnfilledAmount(new BigDecimal("0.01"));
        askOrderDO.setPrice(new BigDecimal("6000.1"));
        askOrderDO.setFee(new BigDecimal("1.1"));
        askOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        askOrderDO.setGmtModified(new Date(System.currentTimeMillis()));
        askOrderDO.setGmtCreate(new Date(System.currentTimeMillis()));
        int aff = usdkOrderMapper.insert(askOrderDO);


        UsdkOrderDO bidOrder = new UsdkOrderDO();
        bidOrder.setId(BasicUtils.generateId());
        bidOrder.setAssetId(2);
        bidOrder.setAssetName("BTC");
        bidOrder.setOrderDirection(OrderDirectionEnum.BID.getCode());
        bidOrder.setUserId(274L);
        bidOrder.setOrderType(OrderPriceTypeEnum.LIMIT.getCode());
        bidOrder.setTotalAmount(new BigDecimal("0.01"));
        bidOrder.setUnfilledAmount(new BigDecimal("0.01"));
        bidOrder.setPrice(new BigDecimal("6000.1"));
        bidOrder.setFee(new BigDecimal("1.1"));
        bidOrder.setStatus(OrderStatusEnum.CANCEL.getCode());
        bidOrder.setGmtModified(new Date(System.currentTimeMillis()));
        bidOrder.setGmtCreate(new Date(System.currentTimeMillis()));

        int aff1 = usdkOrderMapper.insert(bidOrder);
        assert aff == 1 && aff1 ==1;

        BigDecimal filledAmount = new BigDecimal("0.01");

        UsdkMatchedOrderDTO usdkMatchedOrderDTO = new UsdkMatchedOrderDTO();
        usdkMatchedOrderDTO.setAskOrderId(askOrderDO.getId());
        usdkMatchedOrderDTO.setAskUserId(askOrderDO.getUserId());
        usdkMatchedOrderDTO.setAskOrderPrice(askOrderDO.getPrice().toString());
        usdkMatchedOrderDTO.setAskOrderStatus(askOrderDO.getStatus());
        usdkMatchedOrderDTO.setAskOrderUnfilledAmount(askOrderDO.getTotalAmount().subtract(filledAmount));

        usdkMatchedOrderDTO.setBidOrderId(bidOrder.getId());
        usdkMatchedOrderDTO.setBidUserId(bidOrder.getUserId());
        usdkMatchedOrderDTO.setBidOrderPrice(bidOrder.getPrice().toString());
        usdkMatchedOrderDTO.setBidOrderStatus(bidOrder.getStatus());
        usdkMatchedOrderDTO.setBidOrderUnfilledAmount(bidOrder.getTotalAmount().subtract(filledAmount));

        usdkMatchedOrderDTO.setFilledAmount(filledAmount.toString());
        usdkMatchedOrderDTO.setFilledPrice(askOrderDO.getPrice().toString());
        usdkMatchedOrderDTO.setAssetId(askOrderDO.getAssetId());
        usdkMatchedOrderDTO.setAskOrderPrice(askOrderDO.getPrice().toString());
        usdkMatchedOrderDTO.setBidOrderPrice(bidOrder.getPrice().toString());

        usdkMatchedOrderDTO.setMatchType(1);
        usdkMatchedOrderDTO.setAssetName("BTC");
        usdkMatchedOrderDTO.setId(BasicUtils.generateId());

        com.fota.trade.domain.ResultCode resultCode = usdkOrderService.updateOrderByMatch(usdkMatchedOrderDTO);

        Assert.assertTrue(resultCode != null && resultCode.isSuccess());
    }

    @Test
    public void getUsdkMatchRecordTest(){
        List<Long> assetIds = new ArrayList<>();
        assetIds.add(2L);
        assetIds.add(3L);
        assetIds.add(4L);
        Integer pageNo = 1;
        Integer pageSize = 100;
        Long startTime  = 1536226652433L;
        Long endTime  = 1536485218068L;
        UsdkMatchedOrderTradeDTOPage usdkMatchedOrderTradeDTOPage =
                usdkOrderService.getUsdkMatchRecord(null, assetIds, pageNo, pageSize, startTime, endTime);
    }


    @Test
    public void testGetMaxGmtCreate(){
        Result<RecoveryMetaData> result = usdkOrderService.getRecoveryMetaData();
        assert result.isSuccess();
    }

    @Test
    public void testListUsdkOrderByQuery4Recovery() {
        RecoveryQuery recoveryQuery = new RecoveryQuery();
        recoveryQuery.setPageSize(10);
        recoveryQuery.setPageIndex(1);
        recoveryQuery.setMaxGmtCreate(usdkOrderMapper.getMaxCreateTime());
        recoveryQuery.setTableIndex(0);
        Result<Page<UsdkOrderDTO>>  result = usdkOrderService.listUsdtOrder4Recovery(recoveryQuery);

        Assert.assertTrue(result.isSuccess());
    }
    @Test
    public void test_send_cancel_msg() {
        ResultCode resultCode = usdkOrderService.cancelAllOrder(274L, new HashMap<>());
        assert resultCode.isSuccess();
    }

    @Test
    public void batchOrderTest() {
        PlaceOrderRequest<PlaceCoinOrderDTO> placeOrderRequest = new PlaceOrderRequest<PlaceCoinOrderDTO>();
        List<PlaceCoinOrderDTO> list = new ArrayList<PlaceCoinOrderDTO>();
        Long userId = 282L;
        String userName = "testName";
        String ip = "testIp";
        UserLevelEnum userLevel = UserLevelEnum.DEFAULT;
        PlaceCoinOrderDTO placeCoinOrderDTO1 = new PlaceCoinOrderDTO();
        placeCoinOrderDTO1.setExtOrderId("000001");
        placeCoinOrderDTO1.setOrderType(OrderTypeEnum.LIMIT.getCode());
        placeCoinOrderDTO1.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        placeCoinOrderDTO1.setPrice(new BigDecimal("0.005"));
        placeCoinOrderDTO1.setSubjectId(Long.valueOf(AssetTypeEnum.FOTA.getCode()));
        placeCoinOrderDTO1.setSubjectName(AssetTypeEnum.FOTA.getDesc());
        placeCoinOrderDTO1.setTotalAmount(new BigDecimal("20"));
        PlaceCoinOrderDTO placeCoinOrderDTO2 = new PlaceCoinOrderDTO();
        placeCoinOrderDTO2.setExtOrderId("000002");
        placeCoinOrderDTO2.setOrderType(OrderTypeEnum.LIMIT.getCode());
        placeCoinOrderDTO2.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        placeCoinOrderDTO2.setPrice(new BigDecimal("200"));
        placeCoinOrderDTO2.setSubjectId(Long.valueOf(AssetTypeEnum.ETH.getCode()));
        placeCoinOrderDTO2.setSubjectName(AssetTypeEnum.ETH.getDesc());
        placeCoinOrderDTO2.setTotalAmount(new BigDecimal("2"));
        list.add(placeCoinOrderDTO1);
        list.add(placeCoinOrderDTO2);

        placeOrderRequest.setIp(ip);
        placeOrderRequest.setCaller(FotaApplicationEnum.TRADE);
        placeOrderRequest.setUserId(userId);
        placeOrderRequest.setPlaceOrderDTOS(list);
        placeOrderRequest.setUserLevel(userLevel);
        placeOrderRequest.setUserName(userName);
        Result<List<PlaceOrderResult>> resultCode = usdkOrderService.batchOrder(placeOrderRequest);
        assert resultCode.isSuccess();
    }

    @Test
    public void batchCancelTest(){
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest();
        cancelOrderRequest.setUserId(282L);
        List<Long> orderList = new ArrayList<>();
        orderList.add(368368540702474L);
        orderList.add(453904378046418L);
        cancelOrderRequest.setOrderIds(orderList);
        Result result =  usdkOrderService.batchCancel(cancelOrderRequest);
        assert result.isSuccess();
    }



}
