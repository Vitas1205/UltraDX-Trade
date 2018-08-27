package com.fota.trade.service;

import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.common.Page;
import com.fota.common.Result;
import com.fota.trade.client.RollbackTask;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderCloseTypeEnum;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.domain.enums.OrderTypeEnum;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.impl.ContractAccountServiceImpl;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import com.fota.trade.util.CommonUtils;
import com.fota.trade.util.PriceUtil;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.fota.trade.domain.enums.OrderStatusEnum.COMMIT;
import static com.fota.trade.domain.enums.OrderStatusEnum.PART_MATCH;

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
    ContractCategoryService contractCategoryService;
    @Resource
    private ContractOrderMapper contractOrderMapper;
    @Resource
    private ContractAccountServiceImpl contractAccountService;
    @Resource
    AssetService assetService;
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
        // 准备数据
        askContractOrder.setCloseType(0);
        askContractOrder.setContractId(contractId);
        askContractOrder.setContractName("BTC0930");
        askContractOrder.setFee(new BigDecimal("0.01"));
        askContractOrder.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        askContractOrder.setPrice(new BigDecimal("6000"));
        askContractOrder.setTotalAmount(100L);
        askContractOrder.setUnfilledAmount(100L);
        askContractOrder.setOrderType(OrderTypeEnum.LIMIT.getCode());
        askContractOrder.setUserId(askUserId);
        askContractOrder.setStatus(OrderStatusEnum.COMMIT.getCode());


        bidContractOrder = new ContractOrderDO();
        bidContractOrder.setCloseType(0);
        bidContractOrder.setContractId(contractId);
        bidContractOrder.setContractName("BTC0930");
        bidContractOrder.setFee(new BigDecimal("0.01"));
        bidContractOrder.setOrderDirection(OrderDirectionEnum.BID.getCode());
        bidContractOrder.setPrice(new BigDecimal("6000"));
        bidContractOrder.setTotalAmount(100L);
        bidContractOrder.setUnfilledAmount(100L);
        bidContractOrder.setOrderType(OrderTypeEnum.LIMIT.getCode());
        bidContractOrder.setUserId(bidUserId);
        bidContractOrder.setStatus(OrderStatusEnum.COMMIT.getCode());

//        contractService.addTotaldAmount(askUserId, new BigDecimal(500000000));
//        contractService.addTotaldAmount(bidUserId, new BigDecimal(500000000));
        int insertRet = contractOrderMapper.insertSelective(askContractOrder);
        int ret2 = contractOrderMapper.insertSelective(bidContractOrder);
        Assert.assertTrue(insertRet > 0 && ret2 > 0);
    }

    @Test
    public void testListContractOrderByQuery() throws Exception {

        BaseQuery contractOrderQuery = new BaseQuery();
        contractOrderQuery.setUserId(282L);
        contractOrderQuery.setPageSize(20);
        contractOrderQuery.setPageNo(1);
        contractOrderQuery.setEndTime(LocalDate.now().plusDays(1).toDate());
        contractOrderQuery.setSourceId(1000);
        contractOrderQuery.setOrderStatus(Arrays.asList(PART_MATCH.getCode(), COMMIT.getCode()));
        Page<ContractOrderDTO> result = contractOrderService.listContractOrderByQuery(contractOrderQuery);
//        Assert.assertTrue(result != null && result.getData() != null);
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



        ContractMatchedOrderDTO contractMatchedOrderDTO = new ContractMatchedOrderDTO();
        contractMatchedOrderDTO.setAskOrderId(askContractOrder.getId());
        contractMatchedOrderDTO.setBidOrderId(bidContractOrder.getId());
        contractMatchedOrderDTO.setContractId(contractId);
        contractMatchedOrderDTO.setContractName(askContractOrder.getContractName());
        contractMatchedOrderDTO.setAssetName("BTC");
        contractMatchedOrderDTO.setFilledPrice(askContractOrder.getPrice().toString());
        contractMatchedOrderDTO.setFilledAmount(1L);

        contractMatchedOrderDTO.setAskOrderPrice(askContractOrder.getPrice().toString());
        contractMatchedOrderDTO.setAskOrderStatus(askContractOrder.getStatus());
        contractMatchedOrderDTO.setBidOrderPrice(bidContractOrder.getPrice().toString());
        contractMatchedOrderDTO.setBidOrderStatus(bidContractOrder.getStatus());
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

    @Test
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
        assert CommonUtils.equal(originAmount, curAmount);
    }

    private void checkContractOrder(ContractOrderDO contractOrderDO) {
        ContractOrderDO curContract = contractOrderMapper.selectByPrimaryKey(contractOrderDO.getId());
        log.info("oldOrder={}", contractOrderDO);
        log.info("curOrder={}", curContract);
        assert curContract.getUnfilledAmount() == contractOrderDO.getUnfilledAmount().longValue()
                && curContract.getStatus().intValue() == contractOrderDO.getStatus()
                && CommonUtils.equal(curContract.getAveragePrice(), contractOrderDO.getAveragePrice());
    }

    private void checkPosition(long userId, long contractId, UserPositionDO userPositionDO) {

        UserPositionDO cur = userPositionMapper.selectByUserIdAndId(userId,
                contractId);
        if (null == userPositionDO) {
            assert cur.getUnfilledAmount() == 0;
            return;
        }
        log.info("oldPostion={}, curPosition={}", userPositionDO, cur);
        assert cur.getUnfilledAmount() == userPositionDO.getUnfilledAmount().longValue()
                && cur.getPositionType() == userPositionDO.getPositionType().intValue()
                && cur.getStatus() == userPositionDO.getStatus().intValue()
                && CommonUtils.equal(cur.getAveragePrice(), userPositionDO.getAveragePrice());

    }

    @Test
    public void getTodayFeeTest() {
        BigDecimal ret = contractOrderService.getTodayFee();
        log.info("--------------------------" + ret);
    }

    @Test
    public void getAveragePriceTest() {
        BigDecimal ret = PriceUtil.getAveragePrice(null, new BigDecimal(0), new BigDecimal(1), new BigDecimal(10));
        log.info("--------------------------" + ret);
    }

    @Test
    public void getContractMacthRecordTest() {
        Long userId = 205L;
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

    @Test
    public void contractPlaceOrderTest(){
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        Map<String, String> userInfoMap = new HashMap<>();
        contractOrderDTO.setContractId(1001L);
        contractOrderDTO.setContractName("BTC0201");
        contractOrderDTO.setTotalAmount(10L);
        contractOrderDTO.setOrderType(OrderTypeEnum.ENFORCE.getCode());
        contractOrderDTO.setOrderDirection(OrderDirectionEnum.BID.getCode());
        contractOrderDTO.setUserId(282L);
        contractOrderDTO.setCloseType(OrderCloseTypeEnum.SYSTEM.getCode());
        contractOrderDTO.setFee(new BigDecimal(0.01));
        contractOrderDTO.setUnfilledAmount(10L);
        contractOrderService.order(contractOrderDTO, userInfoMap);
    }

    @Test
    public void getContractAccountTest(){
        Result<ContractAccount> result =contractAccountService.getContractAccount(282L);
        log.info(result.toString());
    }

    @Test
    public void getAccountMsgTest(){
        Map<String, BigDecimal> result = contractOrderManager.getAccountMsg(282L);
        log.info(result.toString());
    }

    @Test
    public void getEntrustMarginTest(){
        BigDecimal ret = contractOrderManager.getEntrustMargin(282L);
        log.info(ret.toString());
    }

}
