package com.fota.trade.service;

import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.common.Page;
import com.fota.trade.client.RollbackTask;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.ContractOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import com.fota.trade.service.impl.ContractOrderServiceImpl;
import com.fota.trade.util.CommonUtils;
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
import java.util.Arrays;
import java.util.Date;

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
    AssetService assetService;
    @Resource
    private UserPositionMapper userPositionMapper;

    ContractOrderDO askContractOrder = new ContractOrderDO();
    ContractOrderDO bidContractOrder = new ContractOrderDO();

    UserContractDTO originAskBalance;
    UserContractDTO originBidBalance;

    UserPositionDO askPositionDO;
    UserPositionDO bidPositionDO;
    ContractOrderManager contractOrderManager;


    long askUserId = 1;
    long bidUserId = 2;
    long contractId = 1001;

    Date checkPoint;


    @Before
    public void init() {
        // 准备数据
        askContractOrder.setCloseType(0);
        askContractOrder.setContractId(contractId);
        askContractOrder.setContractName("BTC0930");
        askContractOrder.setFee(new BigDecimal("0.01"));
        askContractOrder.setLever(10);
        askContractOrder.setOrderDirection(OrderDirectionEnum.ASK.getCode());
        askContractOrder.setPrice(new BigDecimal("6000.1"));
        askContractOrder.setTotalAmount(100L);
        askContractOrder.setUnfilledAmount(100L);
        askContractOrder.setUserId(askUserId);
        askContractOrder.setStatus(OrderStatusEnum.COMMIT.getCode());


        bidContractOrder = new ContractOrderDO();
        bidContractOrder.setCloseType(0);
        bidContractOrder.setContractId(contractId);
        bidContractOrder.setContractName("BTC0930");
        bidContractOrder.setFee(new BigDecimal("0.01"));
        bidContractOrder.setLever(10);
        bidContractOrder.setOrderDirection(OrderDirectionEnum.BID.getCode());
        bidContractOrder.setPrice(new BigDecimal("6000.1"));
        bidContractOrder.setTotalAmount(100L);
        bidContractOrder.setUnfilledAmount(100L);
        bidContractOrder.setUserId(bidUserId);
        bidContractOrder.setStatus(OrderStatusEnum.COMMIT.getCode());

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

        checkPoint = new Date();
        originAskBalance = assetService.getContractAccount(askUserId);
        originBidBalance = assetService.getContractAccount(bidUserId);
        //mbatis默认开启一级缓存，防止一级缓存隐藏
        askPositionDO =  cloneObject(userPositionMapper.selectByUserIdAndId(askUserId, contractId));
        bidPositionDO =  cloneObject(userPositionMapper.selectByUserIdAndId(bidUserId, contractId));

        ContractMatchedOrderDTO contractMatchedOrderDTO = new ContractMatchedOrderDTO();
        contractMatchedOrderDTO.setAskOrderId(askContractOrder.getId());
        contractMatchedOrderDTO.setBidOrderId(bidContractOrder.getId());
        contractMatchedOrderDTO.setContractId(contractId);
        contractMatchedOrderDTO.setContractName(askContractOrder.getContractName());
        contractMatchedOrderDTO.setAssetName("BTC");
        contractMatchedOrderDTO.setFilledPrice(askContractOrder.getPrice().toString());
        contractMatchedOrderDTO.setFilledAmount(5l);

        contractMatchedOrderDTO.setAskOrderPrice(askContractOrder.getPrice().toString());
        contractMatchedOrderDTO.setAskOrderStatus(askContractOrder.getStatus());
        contractMatchedOrderDTO.setBidOrderPrice(bidContractOrder.getPrice().toString());
        contractMatchedOrderDTO.setBidOrderStatus(bidContractOrder.getStatus());
        contractMatchedOrderDTO.setMatchType(1);
        contractMatchedOrderDTO.setGmtCreate(new Date());
        ResultCode resultCode = contractOrderService.updateOrderByMatch(contractMatchedOrderDTO);
        Assert.assertTrue(resultCode.isSuccess());
    }

    private UserPositionDO cloneObject(UserPositionDO userPositionDO){
        UserPositionDO newObj = new UserPositionDO();
        BeanUtils.copyProperties(userPositionDO, newObj);
        return newObj;
    }

    @Test
    public void testRollbackMatchedOrder() throws ParseException {
        testUpdateOrderByMatch();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date ck = sdf.parse("2018-08-20 08:27:11");
        ResultCode resultCode = contractCategoryService.rollback(checkPoint.getTime(), contractId);
        assert resultCode.isSuccess();

        //检查委托
        checkContractOrder(askContractOrder);
        checkContractOrder(bidContractOrder);

        //检查持仓
        checkPosition(askPositionDO);
        checkPosition(bidPositionDO);

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
                && CommonUtils.equal(curContract.getAveragePrice(), contractOrderDO.getAveragePrice());
    }

    private void checkPosition(UserPositionDO userPositionDO) {

        UserPositionDO cur = userPositionMapper.selectByUserIdAndId(userPositionDO.getUserId(),
                userPositionDO.getContractId());
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

}
