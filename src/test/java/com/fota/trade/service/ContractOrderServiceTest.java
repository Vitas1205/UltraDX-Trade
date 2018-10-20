package com.fota.trade.service;

import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.common.Page;
import com.fota.common.Result;
import com.fota.trade.client.RecoveryMetaData;
import com.fota.trade.client.RecoveryQuery;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderCloseTypeEnum;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.domain.enums.OrderTypeEnum;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.impl.ContractAccountServiceImpl;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import com.fota.trade.util.BasicUtils;
import com.fota.trade.util.DateUtil;
import com.fota.trade.util.PriceUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.fota.ticker.entrust.entity.enums.OrderStatusEnum.CANCEL;

/**
 * @author Gavin Shen
 * @Date 2018/7/8
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class ContractOrderServiceTest {

    @Resource
    private ContractOrderServiceImpl contractOrderService;
    @Resource
    private ContractCategoryService contractCategoryService;
    @Resource
    private ContractOrderMapper contractOrderMapper;
    @Resource
    private ContractAccountServiceImpl contractAccountService;
    @Resource
    private AssetService assetService;
    @Resource
    private UserPositionMapper userPositionMapper;

    @Resource
    private ContractService contractService;

    ContractOrderDO askContractOrder = new ContractOrderDO();
    ContractOrderDO bidContractOrder = new ContractOrderDO();

    UserContractDTO originAskBalance;
    UserContractDTO originBidBalance;

    UserPositionDO askPositionDO;
    UserPositionDO bidPositionDO;
    @Resource
    private ContractOrderManager contractOrderManager;


    long askUserId = 1;
    long bidUserId = 2;
    long contractId = 1002;

    Date checkPoint;


    @Before
    public void init() {

    }

    @Test
    public void testListContractOrderByQuery() throws Exception {
        BaseQuery baseQuery = new BaseQuery();
        baseQuery.setPageNo(1);
        baseQuery.setUserId(askUserId);
        baseQuery.setPageSize(1000);
        List<Integer> orderStatus = new ArrayList<>();
        Page<ContractOrderDTO> contractOrderDTOPage = null;
        contractOrderDTOPage = contractOrderService.listContractOrderByQuery(baseQuery);
        log.info(String.valueOf(contractOrderDTOPage));
    }

    @Test
    public void testUpdateOrderByMatch() {


//        public long id;
//        public long askOrderId;
//        public String askOrderPrice;
//        public long bidOrderId;
//        public String bidOrderPrice;
//        public String filledPrice;
//        public long filledAmount;
//        public long contractId;
//        public String contractName;
//        public long gmtCreate;
//        public int matchType;
//        public String assetName;
//        public int contractType;

        BigDecimal amount = new BigDecimal("0.01");

        // 准备数据
        askContractOrder.setId(BasicUtils.generateId());
        askContractOrder.setCloseType(0);
        askContractOrder.setContractId(contractId);
        askContractOrder.setContractName("BTC0930");
        askContractOrder.setFee(new BigDecimal("0.01"));
        askContractOrder.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        askContractOrder.setPrice(new BigDecimal("6000"));
        askContractOrder.setTotalAmount(amount);
        askContractOrder.setUnfilledAmount(amount);
        askContractOrder.setOrderType(OrderTypeEnum.LIMIT.getCode());
        askContractOrder.setUserId(askUserId);
        askContractOrder.setStatus(OrderStatusEnum.CANCEL.getCode());
        askContractOrder.setGmtCreate(new Date(System.currentTimeMillis()));


        bidContractOrder = new ContractOrderDO();
        bidContractOrder.setId(BasicUtils.generateId());
        bidContractOrder.setCloseType(0);
        bidContractOrder.setContractId(contractId);
        bidContractOrder.setContractName("BTC0930");
        bidContractOrder.setFee(new BigDecimal("0.01"));
        bidContractOrder.setOrderDirection(OrderDirectionEnum.BID.getCode());
        bidContractOrder.setPrice(new BigDecimal("6000"));
        bidContractOrder.setTotalAmount(amount);
        bidContractOrder.setUnfilledAmount(amount);
        bidContractOrder.setOrderType(OrderTypeEnum.LIMIT.getCode());
        bidContractOrder.setUserId(bidUserId);
        bidContractOrder.setStatus(OrderStatusEnum.COMMIT.getCode());
        bidContractOrder.setGmtCreate(new Date());

//        contractService.addTotaldAmount(askUserId, new BigDecimal(500000000));
//        contractService.addTotaldAmount(bidUserId, new BigDecimal(500000000));
        int insertRet = contractOrderMapper.insert(askContractOrder);
//        System.out.println();
        int ret2 = contractOrderMapper.insert(bidContractOrder);
        Assert.assertTrue(insertRet > 0 && ret2 > 0);


        ContractMatchedOrderDTO contractMatchedOrderDTO = new ContractMatchedOrderDTO();
        contractMatchedOrderDTO.setId(1L);

        contractMatchedOrderDTO.setAskUserId(askUserId);
        contractMatchedOrderDTO.setAskOrderId(askContractOrder.getId());
        contractMatchedOrderDTO.setAskOrderPrice(askContractOrder.getPrice().toString());
        contractMatchedOrderDTO.setAskOrderStatus(askContractOrder.getStatus());
        contractMatchedOrderDTO.setAskOrderUnfilledAmount(BigDecimal.ZERO);

        contractMatchedOrderDTO.setBidUserId(bidUserId);
        contractMatchedOrderDTO.setBidOrderId(bidContractOrder.getId());
        contractMatchedOrderDTO.setBidOrderPrice(bidContractOrder.getPrice().toString());
        contractMatchedOrderDTO.setBidOrderStatus(bidContractOrder.getStatus());
        contractMatchedOrderDTO.setBidOrderUnfilledAmount(BigDecimal.ZERO);

        contractMatchedOrderDTO.setContractId(contractId);
        contractMatchedOrderDTO.setContractName(askContractOrder.getContractName());
        contractMatchedOrderDTO.setAssetName("BTC");
        contractMatchedOrderDTO.setFilledPrice(askContractOrder.getPrice().toString());
        contractMatchedOrderDTO.setFilledAmount(amount);
        contractMatchedOrderDTO.setMatchType(1);

        ResultCode resultCode = contractOrderService.updateOrderByMatch(contractMatchedOrderDTO);
        Assert.assertTrue(resultCode.isSuccess());
    }

    private UserPositionDO cloneObject(UserPositionDO userPositionDO){
        if (null == userPositionDO) {
            return null;
        }
        UserPositionDO newObj = new UserPositionDO();
        BeanUtils.copyProperties(userPositionDO, newObj);
        return newObj;
    }

//    @Test
    public void testRollbackMatchedOrder() throws ParseException {
        checkPoint = new Date();
        originAskBalance = assetService.getContractAccount(askUserId);
        originBidBalance = assetService.getContractAccount(bidUserId);
        //mbatis默认开启一级缓存，防止成交里面的修改操作
        askPositionDO =  cloneObject(userPositionMapper.selectByUserIdAndId(askUserId, contractId));
        bidPositionDO =  cloneObject(userPositionMapper.selectByUserIdAndId(bidUserId, contractId));
        testUpdateOrderByMatch();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date ck = sdf.parse("2018-08-22 15:57:40");
        Result result = contractCategoryService.rollback(checkPoint, contractId);
        assert result.isSuccess();

        //检查委托
        checkContractOrder(askContractOrder);
        checkContractOrder(bidContractOrder);

        //检查持仓
        checkPosition(askUserId, contractId, askPositionDO);
        checkPosition(bidUserId, contractId, bidPositionDO);

        //检查合约账户是否回滚成功
        checkBalance(originAskBalance);
        checkBalance(originBidBalance);

    }

    private void checkBalance(UserContractDTO origin) {
        UserContractDTO cur = assetService.getContractAccount(origin.getUserId());
        log.info("oldBalance={}, curBalance={}", origin, cur);
        BigDecimal originAmount = new BigDecimal(origin.getAmount());
        BigDecimal curAmount = new BigDecimal(cur.getAmount());
        assert com.fota.common.utils.CommonUtils.equal(originAmount, curAmount);
    }

    private void checkContractOrder(ContractOrderDO contractOrderDO) {
        ContractOrderDO curContract = contractOrderMapper.selectByIdAndUserId(contractOrderDO.getUserId(), contractOrderDO.getId());
        log.info("oldOrder={}", contractOrderDO);
        log.info("curOrder={}", curContract);
        /*assert curContract.getUnfilledAmount() == contractOrderDO.getUnfilledAmount().longValue()
                && curContract.getStatus().intValue() == contractOrderDO.getStatus()
                && BasicUtils.equal(curContract.getAveragePrice(), contractOrderDO.getAveragePrice());*/
    }

    private void checkPosition(long userId, long contractId, UserPositionDO userPositionDO) {

        UserPositionDO cur = userPositionMapper.selectByUserIdAndId(userId,
                contractId);
        if (null == userPositionDO) {
            assert cur.getUnfilledAmount().compareTo(BigDecimal.ZERO) == 0;
            return;
        }
        log.info("oldPostion={}, curPosition={}", userPositionDO, cur);
        assert cur.getUnfilledAmount().compareTo(userPositionDO.getUnfilledAmount()) == 0
                && cur.getPositionType() == userPositionDO.getPositionType().intValue()
                && cur.getStatus() == userPositionDO.getStatus().intValue()
                && com.fota.common.utils.CommonUtils.equal(cur.getAveragePrice(), userPositionDO.getAveragePrice());

    }

//    @Test
    public void testCancel(){
        contractOrderManager.cancelOrderByMessage(askContractOrder.getUserId(), askContractOrder.getId(), new BigDecimal(1));
        ContractOrderDO orderDO = contractOrderMapper.selectByIdAndUserId(askContractOrder.getUserId(), askContractOrder.getId());
        assert orderDO.getStatus() == CANCEL.getCode();
    }

    @Test
    public void getTodayFeeTest() {
        //BigDecimal ret = contractOrderService.getTodayFee();
        String platformTotalProfit = String.valueOf(contractOrderService.getTodayFee().multiply(new BigDecimal("0.4")).setScale(2,BigDecimal.ROUND_DOWN));
        log.info("--------------------------" + platformTotalProfit);
    }

    @Test
    public void getAveragePriceTest() {
        BigDecimal ret = PriceUtil.getAveragePrice(null, new BigDecimal(0), new BigDecimal(1), new BigDecimal(10));
        log.info("--------------------------" + ret);
    }

    @Test
    public void testGetMetaData(){
        Result<RecoveryMetaData> result = contractOrderService.getRecoveryMetaData();
        assert result.isSuccess();
        log.info("date={}",result.getData());

    }

    @Test
    public void getContractMacthRecordTest() {
        Long userId = 274L;
        List<Long> contractIds = null;
//        contractIds.add(1000L);
//        contractIds.add(1001L);
        Integer pageNo = 1;
        Integer pageSize = 20;
//        Long startTime = System.currentTimeMillis() - 20000000L;
//        Long endTime = System.currentTimeMillis();
        Long startTime = 0L;
        Long endTime = 0L;
        ContractMatchedOrderTradeDTOPage contractMatchedOrderTradeDTOPage =
                contractOrderService.getContractMacthRecord(userId, contractIds, pageNo, pageSize, startTime, endTime);
        log.info("--------------------------" + contractMatchedOrderTradeDTOPage);
    }

//    @Test
    public void contractPlaceOrderTest(){
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        Map<String, String> userInfoMap = new HashMap<>();
        userInfoMap.put("username", "harry");
        contractOrderDTO.setContractId(1000L);
        contractOrderDTO.setContractName("BTC0102");
        contractOrderDTO.setTotalAmount(BigDecimal.TEN);
        contractOrderDTO.setOrderType(OrderTypeEnum.ENFORCE.getCode());
        contractOrderDTO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        contractOrderDTO.setUserId(274L);
        contractOrderDTO.setPrice(new BigDecimal(1));
        contractOrderDTO.setCloseType(OrderCloseTypeEnum.MANUAL.getCode());
        contractOrderDTO.setFee(new BigDecimal(0.01));
        //Result result = contractOrderService.orderReturnId(contractOrderDTO, userInfoMap);
        //log.info(result.toString());

    }

    @Test
    public void judegOrderAvailableTest(){
        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setContractId(1000L);
        contractOrderDO.setContractName("BTC0102");
        contractOrderDO.setTotalAmount(new BigDecimal("0.05"));
        contractOrderDO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        contractOrderDO.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        contractOrderDO.setUserId(282L);
        contractOrderDO.setPrice(new BigDecimal(100));
        contractOrderDO.setCloseType(OrderCloseTypeEnum.MANUAL.getCode());
        contractOrderDO.setFee(new BigDecimal(0.0005));
        contractOrderDO.setUnfilledAmount(new BigDecimal("0.05"));
//        Pair<Boolean, Map<String, Object>> ret = contractOrderManager.judgeOrderAvailable(282L, contractOrderDO);
//        log.info(ret+"");
    }

    @Test
    public void getContractAccountTest(){
        Result<ContractAccount> result =contractAccountService.getContractAccount(282L);
        log.info(result.toString());
    }

    @Test
    public void getAccountMsgTest(){
        ContractAccount contractAccount = contractOrderManager.computeContractAccount(282L);
        log.info(contractAccount.toString());
    }

    @Test
    public void cancelOrderTest() throws Exception {
//        Map<String, String> usermap = new HashMap<>();
//        ResultCode resultCode = contractOrderManager.cancelOrder(282L, 707427967800392L , usermap);
//        log.info(resultCode.toString());
    }

    @Test
    public void cancelOrderByContractIdTest() throws Exception {
//        Map<String, String> usermap = new HashMap<>();
//        ResultCode resultCode = contractOrderManager.cancelOrderByContractId(1001L, usermap);
//        log.info(resultCode.toString());
    }

    @Test
    public void cancelAllOrderTest() throws Exception {
//        Map<String, String> usermap = new HashMap<>();
//        ResultCode resultCode = contractOrderManager.cancelAllOrder(282L, usermap);
//        log.info(resultCode.toString());
    }

    @Test
    public void testOrderReturnId(){
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        Map<String, String> userInfoMap = new HashMap<>();
        userInfoMap.put("username", "harry");
        contractOrderDTO.setContractId(1001L);
        contractOrderDTO.setContractName("BTC0201");
        contractOrderDTO.setTotalAmount(BigDecimal.TEN);
        contractOrderDTO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        contractOrderDTO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        contractOrderDTO.setUserId(282L);
        contractOrderDTO.setPrice(new BigDecimal(6000));
        contractOrderDTO.setCloseType(OrderCloseTypeEnum.SYSTEM.getCode());
        contractOrderDTO.setFee(new BigDecimal(0.01));
        contractOrderDTO.setUnfilledAmount(BigDecimal.TEN);
        //contractOrderService.orderReturnId(contractOrderDTO, userInfoMap);
    }

    @Test
    public void testCountByQuery() {
        BaseQuery usdkOrderQuery = new BaseQuery();
        usdkOrderQuery.setPageSize(1000);
        usdkOrderQuery.setPageNo(1);
        List<Integer> orderStatus = new ArrayList<>();
        orderStatus.add(OrderStatusEnum.COMMIT.getCode());
        orderStatus.add(OrderStatusEnum.PART_MATCH.getCode());
        usdkOrderQuery.setOrderStatus(orderStatus);
        Integer total = contractOrderService.countContractOrderByQuery4Recovery(usdkOrderQuery);
        Assert.assertTrue(total > 0);
    }

    @Test
    public void testListUsdkOrderByQuery4Recovery() {
        RecoveryQuery recoveryQuery = new RecoveryQuery();
        recoveryQuery.setMaxGmtCreate(DateUtil.parse("2018-10-09 12:04:00"));
        recoveryQuery.setTableIndex(18);
        recoveryQuery.setPageIndex(1);
        recoveryQuery.setPageSize(10);
        Result result  = contractOrderService.listContractOrder4Recovery(recoveryQuery);
        Assert.assertTrue(result.isSuccess());
        log.info("result={}", result);
    }
    @Test
    public void testCancelByContractType() {
        ResultCode resultCode = contractOrderService.cancelOrderByOrderType(274, Arrays.asList(1), new HashMap<>());
        assert resultCode.isSuccess();
    }
    @Test
    public void testCancelByCancelId(){
        ResultCode resultCode = contractOrderService.cancelOrderByContractId(1150L, new HashMap<>());
        assert resultCode.isSuccess();
    }

    @Test
    public void getFeeByDateTest(){
        Date end = new Date();
        Date start = new Date(14000000000L);
        BigDecimal fee = contractOrderService.getFeeByDate(start, end);
        log.info("" + fee);
    }


}
