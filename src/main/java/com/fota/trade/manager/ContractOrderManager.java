package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.ContractDealer;
import com.fota.asset.domain.UserContractDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.ContractService;
import com.fota.common.Result;
import com.fota.common.utils.CommonUtils;
import com.fota.match.service.ContractMatchedOrderService;
import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.common.BizException;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.common.UpdatePositionResult;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.*;
import com.fota.trade.mapper.*;
import com.fota.trade.service.ContractAccountService;
import com.fota.trade.util.ContractUtils;
import com.fota.trade.util.Profiler;
import com.fota.trade.util.ThreadContextUtil;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;

import static com.fota.trade.client.constants.MatchedOrderStatus.VALID;
import static com.fota.trade.common.Constant.DEFAULT_LEVER;
import static com.fota.trade.common.ResultCodeEnum.*;
import static com.fota.trade.domain.enums.ContractStatusEnum.PROCESSING;
import static com.fota.trade.domain.enums.OrderStatusEnum.CANCEL;
import static com.fota.trade.domain.enums.OrderStatusEnum.PART_CANCEL;
import static com.fota.trade.util.ContractUtils.computeAveragePrice;
import static java.util.stream.Collectors.*;
import static org.springframework.transaction.annotation.Propagation.REQUIRED;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Component
public class ContractOrderManager {
    private static final Logger log = LoggerFactory.getLogger(ContractOrderManager.class);
    private static final Logger tradeLog = LoggerFactory.getLogger("trade");


    private static BigDecimal contractFee = BigDecimal.valueOf(0.0005);

    @Autowired
    private ContractOrderMapper contractOrderMapper;

    @Autowired
    private ContractLeverManager contractLeverManager;

    @Resource
    private UserContractLeverMapper userContractLeverMapper;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private ContractCategoryMapper contractCategoryMapper;

    @Autowired
    private RocketMqManager rocketMqManager;

    @Autowired
    private AssetService assetService;

    @Autowired
    private ContractService contractService;

    @Autowired
    private ContractMatchedOrderService contractMatchedOrderService;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Autowired
    private RealTimeEntrust realTimeEntrust;

    @Autowired
    private ContractAccountService contractAccountService;

    Random random = new Random();

    private AssetService getAssetService() {
        return assetService;
    }

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


    public ResultCode cancelOrderByContractId(Long contractId, Map<String, String> userInfoMap) throws Exception {
        if (Objects.isNull(contractId)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }

        ResultCode resultCode = ResultCode.success();
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByContractId(contractId);
        if (!CollectionUtils.isEmpty(list)) {
            Map<Long, List<Long>> orderMap = list.stream()
                    .collect(groupingBy(ContractOrderDO::getUserId, mapping(ContractOrderDO::getId, toList())));

            for (Map.Entry<Long, List<Long>> entry : orderMap.entrySet()) {
                sendCancelMessage(entry.getValue(), entry.getKey());
            }
        }

        return resultCode;
    }

    public ResultCode cancelOrderByOrderType(long userId, List<Integer> orderTypes, Map<String, String> userInfoMap) throws Exception {
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.listByUserIdAndOrderType(userId, orderTypes);
        if (!CollectionUtils.isEmpty(list)) {
            Predicate<ContractOrderDO> isNotEnforce = contractOrderDO -> contractOrderDO.getOrderType() != OrderTypeEnum.ENFORCE.getCode();
            Predicate<ContractOrderDO> isCommit = contractOrderDO -> contractOrderDO.getStatus() == OrderStatusEnum.COMMIT.getCode();
            Predicate<ContractOrderDO> isPartMatch = contractOrderDO -> contractOrderDO.getStatus() == OrderStatusEnum.PART_MATCH.getCode();
            List<Long> orderDOList = list.stream()
                    .filter(isCommit.or(isPartMatch).and(isNotEnforce))
                    .map(ContractOrderDO::getId)
                    .collect(toList());

            sendCancelMessage(orderDOList, userId);
        }

        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }


//    @Transactional(rollbackFor = Throwable.class)
    public Result<Long> placeOrder(ContractOrderDTO contractOrderDTO, Map<String, String> userInfoMap) throws Exception{

        Profiler profiler = null == ThreadContextUtil.getPrifiler() ?
                new Profiler("ContractOrderManager.placeOrder"): ThreadContextUtil.getPrifiler();
        profiler.complelete("start transaction");
        ContractOrderDO contractOrderDO = com.fota.trade.common.BeanUtils.copy(contractOrderDTO);
        String username = StringUtils.isEmpty(userInfoMap.get("username")) ? "" : userInfoMap.get("username");
        String ipAddress = StringUtils.isEmpty(userInfoMap.get("ip")) ? "" : userInfoMap.get("ip");
        com.fota.common.Result<Long> result = new com.fota.common.Result<Long>();
        Map<String, Object> newMap = new HashMap<>();
        if (contractOrderDTO.getOrderContext() !=null){
            newMap = contractOrderDTO.getOrderContext();
        }
        newMap.put("username", username);
        contractOrderDTO.setOrderContext(newMap);
        profiler.complelete("before json serialization");
        contractOrderDO.setOrderContext(JSONObject.toJSONString(contractOrderDTO.getOrderContext()));
        profiler.complelete("json serialization");
        Long orderId = com.fota.trade.util.CommonUtils.generateId();
        long transferTime = System.currentTimeMillis();
        contractOrderDO.setGmtCreate(new Date(transferTime));
        contractOrderDO.setGmtModified(new Date(transferTime));
        contractOrderDO.setStatus(8);
        contractOrderDO.setFee(Constant.FEE_RATE);
        contractOrderDO.setId(orderId);
        contractOrderDO.setUnfilledAmount(contractOrderDO.getTotalAmount());

        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        profiler.complelete("select contract category");
        if (contractCategoryDO == null) {
            log.error("Contract Is Null");
            throw new RuntimeException("Contract Is Null");
        }
        if (contractCategoryDO.getStatus() == ContractStatusEnum.ROOLING_BACK.getCode()){
            result.setCode(ResultCodeEnum.CONTRACT_IS_ROLLING_BACK.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_IS_ROLLING_BACK.getMessage());
            result.setData(orderId);
            return result;
        }else if(contractCategoryDO.getStatus() == ContractStatusEnum.DELIVERYING.getCode()){
            result.setCode(ResultCodeEnum.CONTRACT_IS_DELIVERYING.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_IS_DELIVERYING.getMessage());
            result.setData(orderId);
            return result;
        }else if(contractCategoryDO.getStatus() == ContractStatusEnum.DELIVERED.getCode()){
            result.setCode(ResultCodeEnum.CONTRACT_HAS_DELIVERIED.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_HAS_DELIVERIED.getMessage());
            result.setData(orderId);
            return result;
        }else if(contractCategoryDO.getStatus() == PROCESSING.getCode()){
        }else {
            result.setCode(ResultCodeEnum.CONTRACT_STATUS_ILLEGAL.getCode());
            result.setMessage(ResultCodeEnum.CONTRACT_STATUS_ILLEGAL.getMessage());
            result.setData(orderId);
            return result;
        }
        if (contractOrderDO.getCloseType() == null){
            contractOrderDO.setCloseType(OrderCloseTypeEnum.MANUAL.getCode());
        }
        contractOrderDO.setContractName(contractCategoryDO.getContractName());
        if (contractOrderDO.getOrderType() == null){
            contractOrderDO.setOrderType(OrderTypeEnum.LIMIT.getCode());
        }
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            insertOrderRecord(contractOrderDO);
        } else {
            Boolean judegRet = judegOrderAvailable(contractOrderDO.getUserId(), contractOrderDO);
            profiler.complelete("judeg order available");
            if (!judegRet) {
                throw new BizException(ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getCode(), ResultCodeEnum.CONTRACT_ACCOUNT_AMOUNT_NOT_ENOUGH.getMessage());
            }
            insertOrderRecord(contractOrderDO);
            orderId = contractOrderDO.getId();
            profiler.complelete("insert record");

        }
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO );
        contractOrderDTO.setCompleteAmount(BigDecimal.ZERO);
        contractOrderDTO.setContractId(contractOrderDO.getContractId());
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            // 强平单
            JSONObject jsonObject = JSONObject.parseObject(contractOrderDO.getOrderContext());
            // 日志系统需要
            if (jsonObject != null && !jsonObject.isEmpty()) {
                username = jsonObject.get("username") == null ? "" : jsonObject.get("username").toString();
            }
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    2, contractOrderDTO.getContractName(), username, ipAddress, contractOrderDTO.getTotalAmount(),
                    System.currentTimeMillis(), 3, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 2);
        } else {
            tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                    2, contractOrderDTO.getContractName(), username, ipAddress, contractOrderDTO.getTotalAmount(),
                    System.currentTimeMillis(), 2, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 1);
        }
        Runnable runnable = () -> {
            sendPlaceOrderMessage(contractOrderDO, contractCategoryDO.getContractType(), contractCategoryDO.getAssetName());
            profiler.complelete("send MQ message");
        };
        ThreadContextUtil.setPostTask(runnable);
        result.setCode(0);
        result.setMessage("success");
        result.setData(orderId);
        return result;
    }

    private void sendPlaceOrderMessage(ContractOrderDO contractOrderDO, Integer contractType, String assetName){
        //推送MQ消息
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setAmount(contractOrderDO.getUnfilledAmount());
        if (contractOrderDO.getPrice() != null){
            orderMessage.setPrice(contractOrderDO.getPrice());
        }
        orderMessage.setOrderDirection(contractOrderDO.getOrderDirection());
        orderMessage.setOrderType(contractOrderDO.getOrderType());
        orderMessage.setTransferTime(contractOrderDO.getGmtCreate().getTime());
        orderMessage.setOrderId(contractOrderDO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.PLACE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId());
        orderMessage.setSubjectName(contractOrderDO.getContractName());
        orderMessage.setContractType(contractType);
        orderMessage.setContractMatchAssetName(assetName);
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder",String.valueOf(contractOrderDO.getId()), orderMessage);
        if (!sendRet) {
            log.error("Send RocketMQ Message Failed ");
        }
    }

    public ResultCode cancelOrder(Long userId, Long orderId, Map<String, String> userInfoMap) throws Exception {
        if (Objects.isNull(userId) || Objects.isNull(orderId)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByPrimaryKey(orderId);
        if (Objects.isNull(contractOrderDO)) {
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), ResultCodeEnum.ILLEGAL_PARAM.getMessage());
        }
        if (contractOrderDO.getOrderType() == OrderTypeEnum.ENFORCE.getCode()) {
            return ResultCode.error(ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getCode(),
                    ResultCodeEnum.ENFORCE_ORDER_CANNOT_BE_CANCELED.getMessage());
        }
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
            return ResultCode.error(BIZ_ERROR.getCode(),"contract is null, id="+contractOrderDO.getContractId());
        }
        if (contractCategoryDO.getStatus() != PROCESSING.getCode()){
            log.error("contract status illegal,can not cancel{}", contractCategoryDO);
            return ResultCode.error(BIZ_ERROR.getCode(),"illegal status, id="+contractCategoryDO.getId() + ", status="+ contractCategoryDO.getStatus());
        }
        ResultCode resultCode = ResultCode.success();
        List<Long> orderIdList = Collections.singletonList(orderId);
        sendCancelMessage(orderIdList, userId);
        return resultCode;
    }

    /**
     * 根据撮合发出的MQ消息撤单
     * @param orderId 委托单ID
     * @param status 撮合队列撤单结果 1-成功 0-失败
     */
//    @Transactional(rollbackFor = Throwable.class)
    public ResultCode cancelOrderByMessage(long orderId, int status) {
        if (status == 1) {
            for (int i = 0;i<3;i++) {
                ResultCode resultCode = doCancelOrder(orderId);
                if (resultCode.getCode().equals(CONCURRENT_PROBLEM.getCode())) {
                    randomSleep();
                    continue;
                }
                return resultCode;
            }
            return ResultCode.error(CONCURRENT_PROBLEM.getCode(), "update db failed, likely concurrent problem");
        } else {
            log.warn("match failed to cancel order {}", orderId);
        }
        return ResultCode.success();
    }

    private void randomSleep(){
        try {
            Thread.sleep(com.fota.trade.util.CommonUtils.randomInt(5));
        } catch (InterruptedException e) {
            log.error("sleep exception", e);
        }
    }
    public ResultCode doCancelOrder(long orderId) {
        ContractOrderDO contractOrderDO = contractOrderMapper.selectByPrimaryKey(orderId);
        if (Objects.isNull(contractOrderDO)) {
            return ResultCode.error(ILLEGAL_PARAM.getCode(), "contract order does not exist, id="+orderId);
        }

        ResultCode resultCode = new ResultCode();
        Integer status = contractOrderDO.getStatus();
        Integer toStatus;

        if (status == OrderStatusEnum.COMMIT.getCode()){
           toStatus = CANCEL.getCode();
        } else if (status == OrderStatusEnum.PART_MATCH.getCode()) {
            toStatus = PART_CANCEL.getCode();
        } else {
            return ResultCode.error(BIZ_ERROR.getCode(),"illegal order status, id="+contractOrderDO.getId() + ", status="+ contractOrderDO.getStatus());
        }
        Long transferTime = System.currentTimeMillis();
        int ret = contractOrderMapper.cancelByOpLock(orderId, status, contractOrderDO.getUnfilledAmount(), toStatus);
        if (ret > 0) {
        } else {
            return ResultCode.error(CONCURRENT_PROBLEM.getCode(),"cancel failed, id="+ contractOrderDO.getId());
        }
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO);
        contractOrderDTO.setCompleteAmount(contractOrderDTO.getTotalAmount().subtract(contractOrderDTO.getUnfilledAmount()));
        contractOrderDTO.setContractId(contractOrderDO.getContractId());
        JSONObject jsonObject = JSONObject.parseObject(contractOrderDO.getOrderContext());
        // 日志系统需要
        String username = "";
        if (jsonObject != null && !jsonObject.isEmpty()) {
            username = jsonObject.get("username") == null ? "" : jsonObject.get("username").toString();
        }
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractOrderDO.getContractId());
        if (contractCategoryDO == null){
            return ResultCode.error(BIZ_ERROR.getCode(),"contract is null, id="+contractOrderDO.getContractId());
        }
        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, contractOrderDTO.getContractName(), username, "", contractOrderDTO.getUnfilledAmount(),
                System.currentTimeMillis(), 1, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(), 1);
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setAmount(contractOrderDTO.getUnfilledAmount());
        orderMessage.setPrice(contractOrderDTO.getPrice());
        orderMessage.setTransferTime(transferTime);
        orderMessage.setOrderId(contractOrderDTO.getId());
        orderMessage.setEvent(OrderOperateTypeEnum.CANCLE_ORDER.getCode());
        orderMessage.setUserId(contractOrderDTO.getUserId());
        orderMessage.setSubjectId(contractOrderDO.getContractId());
        orderMessage.setSubjectName(contractOrderDO.getContractName());
        orderMessage.setOrderDirection(contractOrderDO.getOrderDirection());
        orderMessage.setContractType(contractCategoryDO.getContractType());
        orderMessage.setContractMatchAssetName(contractCategoryDO.getAssetName());
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractOrder", "contract_doCanceled_"+ orderId, orderMessage);
        if (!sendRet) {
            log.error("send canceled message failed, message={}", orderMessage);
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }

    public void sendCancelMessage(List<Long> orderIdList, Long userId) {
        if (CollectionUtils.isEmpty(orderIdList)) {
            log.error("empty orderList");
            return;
        }
        //发送MQ消息到match
        Map<String, Object> map = new HashMap<>();
        map.putIfAbsent("userId", userId);
        map.putIfAbsent("idList", orderIdList);
        Boolean sendRet = rocketMqManager.sendMessage("order", "ContractCancel",
                Joiner.on(",").join(orderIdList), map);
        if (BooleanUtils.isNotTrue(sendRet)){
            log.error("failed to send cancel contract mq, {}", userId);
        }
    }

    public ResultCode cancelAllOrder(Long userId, Map<String, String> userInfoMap) throws Exception {
        ResultCode resultCode = new ResultCode();
        List<ContractOrderDO> list = contractOrderMapper.selectUnfinishedOrderByUserId(userId);
        List<ContractOrderDO> listFilter = new ArrayList<>();
        for (ContractOrderDO temp : list){
            ContractCategoryDO contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(temp.getContractId());
            if (contractCategoryDO.getStatus() != PROCESSING.getCode()){
                log.error("contract status illegal,can not cancel{}", contractCategoryDO);
                continue;
            }
            if (temp.getOrderType() != OrderTypeEnum.ENFORCE.getCode()){
                listFilter.add(temp);
            }
        }
        if (listFilter != null){
            List<Long> orderIdList = list.stream()
                    .map(ContractOrderDO::getId)
                    .collect(toList());

            sendCancelMessage(orderIdList, userId);
        }
        resultCode.setCode(0);
        resultCode.setMessage("success");
        return resultCode;
    }

    //获取实时持仓保证金、实时浮盈亏金
    public Map<String, BigDecimal> getAccountMsg(long userId) {
        Map<String, BigDecimal> resultMap = new HashMap<String, BigDecimal>();
        //获取所有合约类型列表
        BigDecimal positionMargin = BigDecimal.ZERO;
        BigDecimal floatingPL = BigDecimal.ZERO;
        List<ContractCategoryDO> queryList = contractCategoryMapper.getAllContractCategory();
        List<UserPositionDO> positionlist = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        List<CompetitorsPriceDTO> competitorsPriceList = realTimeEntrust.getContractCompetitorsPrice();

        if (queryList != null && queryList.size() != 0 && positionlist != null && positionlist.size() != 0) {
            for (ContractCategoryDO contractCategoryDO : queryList) {
                long contractId = contractCategoryDO.getId();
                List<UserPositionDO> userPositionDOlist = new ArrayList<>();
                if (positionlist != null && positionlist.size() != 0) {
                    userPositionDOlist = positionlist.stream().filter(userPosition -> userPosition.getContractId().equals(contractCategoryDO.getId()))
                            .limit(1).collect(toList());
                    if (userPositionDOlist != null && userPositionDOlist.size() != 0) {
                        UserPositionDO userPositionDO = userPositionDOlist.get(0);
                        //获取买一卖一价
                        BigDecimal askCurrentPrice = BigDecimal.ZERO;
                        BigDecimal bidCurrentPrice = BigDecimal.ZERO;

                        BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId, contractId));
                        Integer positionType = userPositionDO.getPositionType();
                        BigDecimal positionUnfilledAmount = userPositionDO.getUnfilledAmount();
                        BigDecimal positionAveragePrice = userPositionDO.getAveragePrice();
                        try {
                            if (positionType == PositionTypeEnum.OVER.getCode()) {
                                bidCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.BID.getCode() &&
                                        competitorsPrice.getId() == contractId).findFirst().get().getPrice();
                                log.info("bidCurrentPrice:"+bidCurrentPrice);
                                BigDecimal bidPositionEntrustAmount = positionUnfilledAmount.multiply(bidCurrentPrice).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                positionMargin = positionMargin.add(bidPositionEntrustAmount);
                                floatingPL = floatingPL.add((bidCurrentPrice.subtract(positionAveragePrice)).multiply(positionUnfilledAmount)).setScale(8, BigDecimal.ROUND_DOWN);
                            } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
                                askCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.ASK.getCode() &&
                                        competitorsPrice.getId() == contractId).findFirst().get().getPrice();
                                log.info("askCurrentPrice:"+askCurrentPrice);
                                BigDecimal askPositionEntrustAmount = positionUnfilledAmount.multiply(askCurrentPrice).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                positionMargin = positionMargin.add(askPositionEntrustAmount);
                                floatingPL = floatingPL.add((positionAveragePrice.subtract(askCurrentPrice)).multiply(positionUnfilledAmount)).setScale(8, BigDecimal.ROUND_DOWN);
                            }
                        }catch (Exception e){
                            log.error("get ContractCompetitorsPrice failed{}", contractId, e);
                            resultMap.put(Constant.POSITION_MARGIN, null);
                            resultMap.put(Constant.FLOATING_PL, null);
                            return resultMap;
                        }

                    }
                }
            }
        }
        resultMap.put(Constant.POSITION_MARGIN, positionMargin);
        resultMap.put(Constant.FLOATING_PL, floatingPL);
        log.info("positionMargin:"+positionMargin);
        log.info("floatingPL:"+floatingPL);
        return resultMap;
    }



    public  ContractAccount computeContractAccount(long userId) {
        return computeContractAccount(userId, null);
    }
    /**
     *
     * @param userId
     * @param newContractOrderDO
     * @return
     */
    public  ContractAccount computeContractAccount(long userId, ContractOrderDO newContractOrderDO) {
        ContractAccount contractAccount = new ContractAccount();
        contractAccount.setMarginCallRequirement(BigDecimal.ZERO)
                .setFrozenAmount(BigDecimal.ZERO)
                .setFloatingPL(BigDecimal.ZERO)
                .setUserId(userId);

        UserContractDTO userContractDTO = assetService.getContractAccount(userId);
        if (null == userContractDTO) {
            log.error("null userContractDTO, userId={}", userId);
            return null;
        }

        List<ContractCategoryDO> categoryList = contractCategoryMapper.getAllContractCategory();
        if (CollectionUtils.isEmpty(categoryList)) {
            log.error("empty categoryList");
             return contractAccount.setAccountEquity(new BigDecimal(userContractDTO.getAmount()));
        }
        List<UserPositionDO> allPositions = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        List<CompetitorsPriceDTO> competitorsPrices = realTimeEntrust.getContractCompetitorsPrice();
        List<UserContractLeverDO> contractLeverDOS = userContractLeverMapper.listUserContractLever(userId);

        List<ContractOrderDO> allContractOrders = contractOrderMapper.selectNotEnforceOrderByUserId(userId);
        if (null == allContractOrders) {
            allContractOrders = new ArrayList<>();
        }
        if (null != newContractOrderDO) {
            allContractOrders.add(newContractOrderDO);
        }

        for (ContractCategoryDO contractCategoryDO : categoryList) {

            long contractId = contractCategoryDO.getId();
            BigDecimal lever = findLever(contractLeverDOS, userId, contractCategoryDO.getAssetId());
            BigDecimal positionMargin = BigDecimal.ZERO;
            BigDecimal floatingPL = BigDecimal.ZERO;
            BigDecimal entrustMargin = BigDecimal.ZERO;
            BigDecimal positionUnfilledAmount= BigDecimal.ZERO;
            int positionType = PositionTypeEnum.EMPTY.getCode();


            List<ContractOrderDO> orderList = null;
            if (!CollectionUtils.isEmpty(allContractOrders)) {
                orderList = allContractOrders.stream().filter(contractOrder -> contractOrder.getContractId().equals(contractId))
                        .collect(toList());
            }


            Optional<UserPositionDO> userPositionDOOptional = allPositions.stream().filter(userPosition -> userPosition.getContractId().equals(contractCategoryDO.getId()))
                    .findFirst();

            //计算保证金，浮动盈亏
            if (userPositionDOOptional.isPresent()) {
                UserPositionDO userPositionDO = userPositionDOOptional.get();
                positionType = userPositionDO.getPositionType();
                positionUnfilledAmount = userPositionDO.getUnfilledAmount();
                BigDecimal positionAveragePrice = userPositionDO.getAveragePrice();

                int dire = ContractUtils.toDirection(userPositionDO.getPositionType());

                BigDecimal price = computePrice(competitorsPrices, userPositionDO.getPositionType(), contractId);
                if (null == price) {
                    return null;
                }
                floatingPL = price.subtract(positionAveragePrice).multiply(positionUnfilledAmount).multiply(new BigDecimal(dire));

                positionMargin = positionUnfilledAmount.multiply(price).divide(lever, CommonUtils.scale, BigDecimal.ROUND_UP);

            }

            //计算委托额外保证金
            if (!CollectionUtils.isEmpty(orderList)) {
                List<ContractOrderDO> bidList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.BID.getCode()).collect(toList());
                List<ContractOrderDO> askList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.ASK.getCode()).collect(toList());
                entrustMargin = getExtraEntrustAmount(bidList, askList, positionType, positionUnfilledAmount, positionMargin, lever);
            }

            contractAccount.setMarginCallRequirement(contractAccount.getMarginCallRequirement().add(positionMargin))
                    .setFrozenAmount(contractAccount.getFrozenAmount().add(entrustMargin))
                    .setFloatingPL(contractAccount.getFloatingPL().add(floatingPL));

        }
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        contractAccount.setAvailableAmount(amount.add(contractAccount.getFloatingPL())
                .subtract(contractAccount.getMarginCallRequirement())
                .subtract(contractAccount.getFrozenAmount()));
        contractAccount.setAccountEquity(amount.add(contractAccount.getFloatingPL()));
        return contractAccount;
    }

    public BigDecimal findLever(List<UserContractLeverDO> contractLeverDOS, long userId, long assetId){
        if(CollectionUtils.isEmpty(contractLeverDOS)) {
            return DEFAULT_LEVER;
        }
        Optional<UserContractLeverDO> leverDO = contractLeverDOS.stream().filter(x -> x.getUserId().equals(userId) && Long.valueOf(x.getAssetId()).equals(assetId)).findFirst();
        if (leverDO.isPresent()) {
            return new BigDecimal(leverDO.get().getLever());
        }
        return DEFAULT_LEVER;
    }

    public BigDecimal computePrice(List<CompetitorsPriceDTO> competitorsPriceList, int type, long contractId) {
        if (CollectionUtils.isEmpty(competitorsPriceList)) {
            log.error("empty competitorsPriceList");
            return null;
        }
        Optional<CompetitorsPriceDTO>  competitorsPriceDTOOptional = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == type &&
                competitorsPrice.getId() == contractId).findFirst();

        if (competitorsPriceDTOOptional.isPresent()) {
            return competitorsPriceDTOOptional.get().getPrice();
        }
        log.error("there is no competitorsPrice, contractId={}, type={}", contractId, type);
        return null;
    }

    //获取实时委托冻结
    public BigDecimal getEntrustMargin(long userId) {
        Map<String, BigDecimal> resultMap = new HashMap<String, BigDecimal>();
        //获取所有合约类型列表
        BigDecimal entrustMargin = BigDecimal.ZERO;
        List<ContractCategoryDO> queryList = contractCategoryMapper.getAllContractCategory();
        List<UserPositionDO> positionlist = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        List<ContractOrderDO> contractOrderlist = contractOrderMapper.selectNotEnforceOrderByUserId(userId);

        if (queryList != null && queryList.size() != 0 && contractOrderlist != null && contractOrderlist.size() != 0) {
            log.info("selectUnfinishedOrderByUserId {} contractOrderlist size {}, contractOrderlist {}", userId, contractOrderlist.size(), contractOrderlist.get(0).toString());

            for (ContractCategoryDO contractCategoryDO : queryList) {
                BigDecimal entrustLockAmount = BigDecimal.ZERO;
                long contractId = contractCategoryDO.getId();
                List<ContractOrderDO> orderList = contractOrderlist.stream().filter(contractOrder -> contractOrder.getContractId().equals(contractCategoryDO.getId()))
                        .collect(toList());
                if (orderList != null && orderList.size() != 0) {
                    List<ContractOrderDO> bidList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.BID.getCode()).collect(toList());
                    List<ContractOrderDO> askList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.ASK.getCode()).collect(toList());
                    List<UserPositionDO> userPositionDOlist = new ArrayList<>();
                    if (positionlist != null && positionlist.size() != 0) {
                        userPositionDOlist = positionlist.stream().filter(userPosition -> userPosition.getContractId().equals(contractCategoryDO.getId()))
                                .limit(1).collect(toList());
                        if (userPositionDOlist != null && userPositionDOlist.size() != 0) {
                            UserPositionDO userPositionDO = userPositionDOlist.get(0);
                            BigDecimal totalAskExtraEntrustAmount = BigDecimal.ZERO;
                            BigDecimal totalBidExtraEntrustAmount = BigDecimal.ZERO;
                            //获取买一卖一价
                            BigDecimal askCurrentPrice = BigDecimal.ZERO;
                            BigDecimal bidCurrentPrice = BigDecimal.ZERO;
                            List<CompetitorsPriceDTO> competitorsPriceList = realTimeEntrust.getContractCompetitorsPrice();
                            BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId, contractId));
                            Integer positionType = userPositionDO.getPositionType();
                            BigDecimal positionUnfilledAmount = userPositionDO.getUnfilledAmount();
                            if (positionType == PositionTypeEnum.OVER.getCode()) {
                                try{
                                    bidCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.BID.getCode() &&
                                            competitorsPrice.getId() == contractId).findFirst().get().getPrice();
                                    log.info("bidCurrentPrice:{}",bidCurrentPrice);
                                    if (bidCurrentPrice.compareTo(BigDecimal.ZERO) == 0){
                                        log.error("bidCurrentPrice not exist");
                                        return null;
                                    }
                                }catch (Exception e){
                                    log.error("getContractBuyPriceSellPriceDTO failed{}",e);
                                    return null;
                                }
                                BigDecimal bidPositionEntrustAmount = positionUnfilledAmount.multiply(bidCurrentPrice).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                totalAskExtraEntrustAmount = totalAskExtraEntrustAmount.add(getExtraEntrustAmount(bidList, askList, positionType, positionUnfilledAmount, bidPositionEntrustAmount, lever));
                            } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
                                try{
                                    askCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.ASK.getCode() &&
                                            competitorsPrice.getId() == contractId).findFirst().get().getPrice();
                                    log.info("askCurrentPrice:{}",askCurrentPrice);
                                    if (askCurrentPrice.compareTo(BigDecimal.ZERO) == 0){
                                        log.error("askCurrentPrice not exist");
                                        return null;
                                    }
                                }catch (Exception e){
                                    log.error("getContractBuyPriceSellPriceDTO failed{}",e);
                                    return null;
                                }
                                BigDecimal askPositionEntrustAmount = positionUnfilledAmount.multiply(askCurrentPrice).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                totalBidExtraEntrustAmount = totalBidExtraEntrustAmount.add(getExtraEntrustAmount(bidList, askList, positionType, positionUnfilledAmount, askPositionEntrustAmount, lever));
                            }
                            entrustLockAmount = entrustLockAmount.add(totalBidExtraEntrustAmount.add(totalAskExtraEntrustAmount));
                        }
                    }
                    if (positionlist == null || positionlist.size() == 0 || userPositionDOlist == null || userPositionDOlist.size() == 0) {
                        BigDecimal lever = new BigDecimal(contractLeverManager.getLeverByContractId(userId, contractId));
                        BigDecimal orderValue;
                        BigDecimal orderFee;
                        BigDecimal toltalBidEntrustAmount = BigDecimal.ZERO;
                        BigDecimal toltalAskEntrustAmount = BigDecimal.ZERO;
                        if (bidList != null && bidList.size() != 0) {
                            for (ContractOrderDO bidOrder : bidList) {
                                orderValue = bidOrder.getPrice().multiply(bidOrder.getUnfilledAmount()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                orderFee = orderValue.multiply(lever).multiply(Constant.FEE_RATE);
                                toltalBidEntrustAmount = toltalBidEntrustAmount.add(orderValue).add(orderFee);
                            }
                        }
                        if (askList != null && askList.size() != 0) {
                            for (ContractOrderDO askOrder : askList) {
                                orderValue = askOrder.getPrice().multiply(askOrder.getUnfilledAmount()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                                orderFee = orderValue.multiply(lever).multiply(Constant.FEE_RATE);
                                toltalAskEntrustAmount = toltalAskEntrustAmount.add(orderValue).add(orderFee);
                            }
                        }
                        if (toltalBidEntrustAmount.compareTo(toltalAskEntrustAmount) > 0) {
                            entrustLockAmount = toltalBidEntrustAmount;
                        } else {
                            entrustLockAmount = toltalAskEntrustAmount;
                        }
                    }
                }
                entrustMargin = entrustMargin.add(entrustLockAmount);
            }
        }
        return entrustMargin;
    }

    //todo 判断持仓反方向的"仓加挂"大于是否该合约持仓保证金
    public Boolean judgeOrderResult(List<ContractOrderDO> filterOrderList,Integer positionType,
                                            BigDecimal positionUnfilledAmount, BigDecimal positionEntrustAmount, BigDecimal lever) {
        if (null == positionUnfilledAmount) {
            log.error("null positionUnfilledAmount");
            positionUnfilledAmount = BigDecimal.ZERO;
        }
        BigDecimal totalEntrustAmount = BigDecimal.ZERO;
        BigDecimal entrustAmount = BigDecimal.ZERO;
        if (filterOrderList != null && filterOrderList.size() != 0) {
            List<ContractOrderDO> sortedList = new ArrayList<>();
            if (positionType == PositionTypeEnum.OVER.getCode()){
                sortedList = sortListEsc(filterOrderList);
            }else {
                sortedList = sortListDesc(filterOrderList);
            }
            for (int i = 0; i < sortedList.size(); i++) {
                positionUnfilledAmount = positionUnfilledAmount.subtract(sortedList.get(i).getUnfilledAmount());
                if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal restAmount = positionUnfilledAmount.negate().multiply(sortedList.get(i).getPrice()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                    BigDecimal restFee = restAmount.multiply(lever).multiply(Constant.FEE_RATE);
                    BigDecimal totalRest = restAmount.add(restFee);
                    for (int j = i + 1; j < sortedList.size(); j++) {
                        BigDecimal orderAmount = sortedList.get(j).getPrice().multiply(sortedList.get(j).getUnfilledAmount()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                        BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        entrustAmount = entrustAmount.add(orderAmount.add(orderFee));
                    }
                    totalEntrustAmount = totalRest.add(entrustAmount);
                    break;
                }
            }
            if (totalEntrustAmount.compareTo(positionEntrustAmount) <= 0){
                return true;
            }
        }
        return false;
    }


    //获取多空仓额外保证金
    public BigDecimal getExtraEntrustAmount(List<ContractOrderDO> bidList, List<ContractOrderDO> askList, Integer positionType,
                                            BigDecimal positionUnfilledAmount, BigDecimal positionEntrustAmount, BigDecimal lever) {
        if (null == positionUnfilledAmount) {
            log.error("null positionUnfilledAmount");
            positionUnfilledAmount = BigDecimal.ZERO;
        }
        BigDecimal max1 = BigDecimal.ZERO;
        BigDecimal max2 = BigDecimal.ZERO;
        BigDecimal totalAskEntrustAmount = BigDecimal.ZERO;
        BigDecimal totalBidEntrustAmount = BigDecimal.ZERO;
        BigDecimal askEntrustAmount = BigDecimal.ZERO;
        BigDecimal bidEntrustAmount = BigDecimal.ZERO;
        if (positionType == PositionTypeEnum.OVER.getCode()) {
            if (askList != null && askList.size() != 0) {
                List<ContractOrderDO> sortedAskList = sortListEsc(askList);
                for (int i = 0; i < sortedAskList.size(); i++) {
                    positionUnfilledAmount = positionUnfilledAmount.subtract(sortedAskList.get(i).getUnfilledAmount());
                    if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0) {
                        BigDecimal restAmount = positionUnfilledAmount.negate().multiply(sortedAskList.get(i).getPrice()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                        BigDecimal restFee = restAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        BigDecimal totalRest = restAmount.add(restFee);
                        for (int j = i + 1; j < sortedAskList.size(); j++) {
                            BigDecimal orderAmount = sortedAskList.get(j).getPrice().multiply(sortedAskList.get(j).getUnfilledAmount()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                            BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                            askEntrustAmount = askEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalAskEntrustAmount = totalRest.add(askEntrustAmount);
                        break;
                    }
                }
                if (totalAskEntrustAmount.compareTo(positionEntrustAmount) > 0) {
                    max1 = totalAskEntrustAmount.subtract(positionEntrustAmount);
                }
            }
            if (bidList != null && bidList.size() != 0) {
                for (int i = 0; i < bidList.size(); i++) {
                    BigDecimal orderAmount = bidList.get(i).getPrice().multiply(bidList.get(i).getUnfilledAmount()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                    totalBidEntrustAmount = totalBidEntrustAmount.add(orderAmount.add(orderFee));
                }
                if (totalBidEntrustAmount.compareTo(max1) > 0) {
                    max2 = totalBidEntrustAmount;
                    return max2;
                }
            }
            return max1;
        } else if (positionType == PositionTypeEnum.EMPTY.getCode()) {
            if (bidList != null && bidList.size() != 0) {
                List<ContractOrderDO> sortedBidList = sortListDesc(bidList);
                for (int i = 0; i < sortedBidList.size(); i++) {
                    positionUnfilledAmount = positionUnfilledAmount.subtract(sortedBidList.get(i).getUnfilledAmount());
                    if (positionUnfilledAmount.compareTo(BigDecimal.ZERO) < 0) {
                        BigDecimal restAmount = positionUnfilledAmount.negate().multiply(sortedBidList.get(i).getPrice()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                        BigDecimal restFee = restAmount.multiply(lever).multiply(Constant.FEE_RATE);
                        BigDecimal totalRest = restAmount.add(restFee);
                        for (int j = i + 1; j < sortedBidList.size(); j++) {
                            BigDecimal orderAmount = sortedBidList.get(j).getPrice().multiply(sortedBidList.get(j).getUnfilledAmount()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                            BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                            bidEntrustAmount = bidEntrustAmount.add(orderAmount.add(orderFee));
                        }
                        totalBidEntrustAmount = totalRest.add(bidEntrustAmount);
                        break;
                    }
                }
                if (totalBidEntrustAmount.compareTo(positionEntrustAmount) > 0) {
                    max1 = totalBidEntrustAmount.subtract(positionEntrustAmount);
                }
            }
            if (askList != null && askList.size() != 0) {
                for (int i = 0; i < askList.size(); i++) {
                    BigDecimal orderAmount = askList.get(i).getPrice().multiply(askList.get(i).getUnfilledAmount()).divide(lever, 8, BigDecimal.ROUND_DOWN);
                    BigDecimal orderFee = orderAmount.multiply(lever).multiply(Constant.FEE_RATE);
                    totalAskEntrustAmount = totalAskEntrustAmount.add(orderAmount.add(orderFee));
                }
                if (totalAskEntrustAmount.compareTo(max1) > 0) {
                    max2 = totalAskEntrustAmount;
                    return max2;
                }
            }
            return max1;
        } else {
            throw new RuntimeException("positionType illegal");
        }
    }


    public void insertOrderRecord(ContractOrderDO contractOrderDO){

        int insertContractOrderRet = contractOrderMapper.insert(contractOrderDO);
        if (insertContractOrderRet <= 0) {
            log.error("insert contractOrder failed");
            throw new RuntimeException("insert contractOrder failed");
        }
    }



    //升序排列
    public List<ContractOrderDO> sortListEsc(List<ContractOrderDO> list) {
        List<ContractOrderDO> sortedList = list.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice))
                .collect(toList());
        return sortedList;
    }

    //降序排列
    public List<ContractOrderDO> sortListDesc(List<ContractOrderDO> list) {
        List<ContractOrderDO> sortedList = list.stream()
                .sorted(Comparator.comparing(ContractOrderDO::getPrice).reversed())
                .collect(toList());
        return sortedList;
    }


    public BigDecimal getTotalLockAmount(ContractOrderDO contractOrderDO) {
        Integer lever = contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(), contractOrderDO.getContractId());
        BigDecimal totalValue = contractOrderDO.getPrice().multiply(contractOrderDO.getTotalAmount())
                .multiply(new BigDecimal(0.01)).divide(new BigDecimal(lever), 8, BigDecimal.ROUND_DOWN);
        BigDecimal fee = totalValue.multiply(Constant.FEE_RATE).multiply(new BigDecimal(lever));
        return totalValue.add(fee);
    }


    /**
     * 禁止通过内部非Transactional方法调用此方法，否则@Transactional注解会失效
     * @param contractMatchedOrderDTO
     * @return
     */
    @Transactional(rollbackFor = {Throwable.class}, isolation = Isolation.REPEATABLE_READ, propagation = REQUIRED)
    public ResultCode updateOrderByMatch(ContractMatchedOrderDTO contractMatchedOrderDTO) {

        long contractId = contractMatchedOrderDTO.getContractId();
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.getContractCategoryById(contractId);
        if (null == contractCategoryDO){
            return ResultCode.error(BIZ_ERROR.getCode(), "null contractCategoryDO, id="+contractId);
        }
        if(!Objects.equals(PROCESSING.getCode(), contractCategoryDO.getStatus())) {
            return ResultCode.error(BIZ_ERROR.getCode(), "illegal contract status, id="+contractId + ", status="+contractCategoryDO.getStatus());
        }

        ContractOrderDO askContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getAskOrderId());
        ContractOrderDO bidContractOrder = contractOrderMapper.selectByPrimaryKey(contractMatchedOrderDTO.getBidOrderId());

        ResultCode checkResult = checkParam(askContractOrder, bidContractOrder, contractMatchedOrderDTO);
        if (!checkResult.isSuccess()) {
            return checkResult;
        }

        //排序，防止死锁
        List<ContractOrderDO> contractOrderDOS = new ArrayList<>();
        contractOrderDOS.add(askContractOrder);
        contractOrderDOS.add(bidContractOrder);
        Collections.sort(contractOrderDOS, (a, b) -> {
            int c = a.getUserId().compareTo(b.getUserId());
            if (c!=0) {
                return c;
            }
            return a.getId().compareTo(b.getId());
        });
        Profiler profiler = null == ThreadContextUtil.getPrifiler()
                ? new Profiler("ContractOrderManager.updateOrderByMatch"):ThreadContextUtil.getPrifiler();

        ResultCode resultCode = new ResultCode();


        BigDecimal filledAmount = contractMatchedOrderDTO.getFilledAmount();
        BigDecimal filledPrice = new BigDecimal(contractMatchedOrderDTO.getFilledPrice());
        Long transferTime = System.currentTimeMillis();

        //更新委托
        contractOrderDOS.forEach(x -> {
            int lever = contractLeverManager.getLeverByContractId(x.getUserId(), x.getContractId());
            x.setLever(lever);
            x.fillAmount(filledAmount);
            updateContractOrder(x.getId(), filledAmount, filledPrice, new Date(transferTime));
        });
        profiler.complelete("update contract order");

        Map<ContractOrderDO, UpdatePositionResult> resultMap = new HashMap<>();

        //更新持仓
        contractOrderDOS.forEach(x -> {
            UpdatePositionResult result = updatePosition(x, filledAmount, filledPrice);
            resultMap.put(x, result);
        });
        profiler.complelete("update position");


        ContractMatchedOrderDO contractMatchedOrderDO = com.fota.trade.common.BeanUtils.copy(contractMatchedOrderDTO);
        BigDecimal fee = contractMatchedOrderDO.getFilledAmount().
                multiply(contractMatchedOrderDO.getFilledPrice()).
                multiply(Constant.FEE_RATE).
                setScale(CommonUtils.scale, BigDecimal.ROUND_UP);
        contractMatchedOrderDO.setFee(fee);
        contractMatchedOrderDO.setAskUserId(askContractOrder.getUserId());
        contractMatchedOrderDO.setBidUserId(bidContractOrder.getUserId());
        contractMatchedOrderDO.setAskCloseType(askContractOrder.getCloseType().byteValue());
        contractMatchedOrderDO.setBidCloseType(bidContractOrder.getCloseType().byteValue());
        contractMatchedOrderDO.setStatus(VALID);
        contractMatchedOrderDO.setGmtCreate(new Date());
        contractMatchedOrderDO.setGmtModified(contractMatchedOrderDO.getGmtCreate());
        try {
            int ret = contractMatchedOrderMapper.insert(contractMatchedOrderDO);
            if (ret < 1) {
                log.error("保存Contract订单数据到数据库失败({})", contractMatchedOrderDO);
                throw new RuntimeException("contractMatchedOrderMapper.insert failed{}");
            }
        } catch (Exception e) {
            log.error("保存Contract订单数据到数据库失败({})", contractMatchedOrderDO, e);
            throw new RuntimeException("contractMatchedOrderMapper.insert exception{}", e);
        }
        profiler.complelete("persistMatch");

        Runnable postTask = () -> {
            //更新账户余额
            List<ContractDealer> dealers = new LinkedList<>();
            contractOrderDOS.forEach(x -> {
                ContractDealer dealer = calBalanceChange(x, filledAmount, filledPrice, resultMap.get(x));
                if (null != dealer && !CommonUtils.equal(dealer.getAddedTotalAmount(), BigDecimal.ZERO)) {
                    dealers.add(dealer);
                }
            });

            if (!dealers.isEmpty()) {
                ContractDealer[] contractDealers = new ContractDealer[dealers.size()];
                dealers.toArray(contractDealers);
                com.fota.common.Result result = contractService.updateBalances(contractDealers);
                profiler.complelete("update balance");
                if (!result.isSuccess()) {
                    throw new RuntimeException("update balance failed, params="+ dealers);
                }
            }
            postProcessAfterMatch(askContractOrder, bidContractOrder, contractMatchedOrderDO, transferTime, contractMatchedOrderDTO, resultMap);
            profiler.complelete("saveAndNotify");
        };
        ThreadContextUtil.setPostTask(postTask);
        resultCode.setCode(ResultCodeEnum.SUCCESS.getCode());
        return resultCode;
    }

    public ResultCode checkMatchOrderDTO(ContractMatchedOrderDTO contractMatchedOrderDTO) {
        if (contractMatchedOrderDTO == null || null == contractMatchedOrderDTO.getAskUserId() || null == contractMatchedOrderDTO.getBidUserId()
                || null == contractMatchedOrderDTO.getAskOrderId() || null == contractMatchedOrderDTO.getBidOrderId() || null == contractMatchedOrderDTO.getFilledAmount()
                || null == contractMatchedOrderDTO.getFilledPrice()) {
            log.error(ResultCodeEnum.ILLEGAL_PARAM.getMessage());
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        return ResultCode.success();
    }

    public   ResultCode checkParam(ContractOrderDO askContractOrder, ContractOrderDO bidContractOrder, ContractMatchedOrderDTO contractMatchedOrderDTO) {
        if (askContractOrder == null){
            log.error("askContractOrder not exist, matchOrder={}",  contractMatchedOrderDTO);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        if (bidContractOrder == null){
            log.error("bidOrderContext not exist, matchOrder={}", contractMatchedOrderDTO);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }

        String messageKey = Joiner.on("-").join(contractMatchedOrderDTO.getAskOrderId().toString(),
                contractMatchedOrderDTO.getAskOrderStatus(), contractMatchedOrderDTO.getBidOrderId(),
                contractMatchedOrderDTO.getBidOrderStatus());

        if (askContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0) {
            log.error("ask unfilledAmount not enough.order={}, messageKey={}", askContractOrder, messageKey);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
        if (bidContractOrder.getUnfilledAmount().compareTo(contractMatchedOrderDTO.getFilledAmount()) < 0) {
            log.error("bid unfilledAmount not enough.order={}, messageKey={}",bidContractOrder, messageKey);
            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
        }
//        if (askContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && askContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()) {
//            log.error("ask order status illegal{}", askContractOrder);
//            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
//        }
//        if (bidContractOrder.getStatus() != OrderStatusEnum.COMMIT.getCode() && bidContractOrder.getStatus() != OrderStatusEnum.PART_MATCH.getCode()) {
//            log.error("bid order status illegal{}", bidContractOrder);
//            return ResultCode.error(ResultCodeEnum.ILLEGAL_PARAM.getCode(), null);
//        }
        return ResultCode.success();
    }


    /**
     * 打印后台交易撮合监控日志
     * @param contractOrderDO
     * @param completeAmount
     * @param orderContext
     * @param matchId
     */
    private void saveToLog(ContractOrderDO contractOrderDO,  BigDecimal completeAmount,  Map<String, Object> orderContext, long matchId){
        ContractOrderDTO contractOrderDTO = new ContractOrderDTO();
        BeanUtils.copyProperties(contractOrderDO, contractOrderDTO);
        contractOrderDTO.setCompleteAmount(completeAmount);
        contractOrderDTO.setOrderContext(orderContext);
        String userName = "";
        if (orderContext != null){
            userName = orderContext.get("username") == null ? "": String.valueOf(orderContext.get("username"));
        }
        tradeLog.info("match@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
                2, contractOrderDTO.getContractName(), userName, contractOrderDTO.getCompleteAmount(),
                System.currentTimeMillis(), 4, contractOrderDTO.getOrderDirection(), contractOrderDTO.getUserId(),matchId);
    }


    public void postProcessAfterMatch(ContractOrderDO askContractOrder, ContractOrderDO bidContractOrder, ContractMatchedOrderDO contractMatchedOrderDO,
                                     long updateOrderTime, ContractMatchedOrderDTO contractMatchedOrderDTO, Map<ContractOrderDO, UpdatePositionResult> resultMap){


        Map<String, Object> askOrderContext = new HashMap<>();
        Map<String, Object> bidOrderContext = new HashMap<>();
        if (askContractOrder.getOrderContext() != null){
            askOrderContext  = JSON.parseObject(askContractOrder.getOrderContext());
        }
        if (bidContractOrder.getOrderContext() != null){
            bidOrderContext  = JSON.parseObject(bidContractOrder.getOrderContext());
        }

        BigDecimal filledAmount = contractMatchedOrderDO.getFilledAmount();
        //后台交易监控日志打在里面 注释需谨慎
        saveToLog(askContractOrder, filledAmount, askOrderContext, contractMatchedOrderDO.getId());
        saveToLog(bidContractOrder, filledAmount, bidOrderContext, contractMatchedOrderDO.getId());

        // 向MQ推送消息
        // 通过contractId去trade_contract_category表里面获取asset_name和contract_type
        ContractCategoryDO contractCategoryDO = contractCategoryMapper.getContractCategoryById(askContractOrder.getContractId());
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setSubjectId(contractMatchedOrderDO.getContractId());
        orderMessage.setSubjectName(contractMatchedOrderDO.getContractName());
        orderMessage.setTransferTime(updateOrderTime);
        orderMessage.setPrice(contractMatchedOrderDO.getFilledPrice());
        orderMessage.setAmount(filledAmount);
        orderMessage.setEvent(OrderOperateTypeEnum.DEAL_ORDER.getCode());
        orderMessage.setAskOrderId(askContractOrder.getId());
        orderMessage.setBidOrderId(bidContractOrder.getId());
        orderMessage.setAskUserId(askContractOrder.getUserId());
        orderMessage.setBidUserId(bidContractOrder.getUserId());
        orderMessage.setFee(contractMatchedOrderDO.getFee());
        orderMessage.setMatchOrderId(contractMatchedOrderDO.getId());
        orderMessage.setContractMatchAssetName(contractCategoryDO.getAssetName());
        orderMessage.setContractType(contractCategoryDO.getContractType());
        orderMessage.setAskOrderUnfilledAmount(contractMatchedOrderDTO.getAskOrderUnfilledAmount());
        orderMessage.setBidOrderUnfilledAmount(contractMatchedOrderDTO.getBidOrderUnfilledAmount());
        orderMessage.setMatchType(contractMatchedOrderDTO.getMatchType());
        //orderMessage.setAskOrderType(askContractOrderDTO.getOrderType());
        //orderMessage.setBidOrderType(bidContractOrderDTO.getOrderType());
        if (askContractOrder.getPrice() != null){
            orderMessage.setAskOrderEntrustPrice(askContractOrder.getPrice());
        }
        if (bidContractOrder.getPrice() != null){
            orderMessage.setBidOrderEntrustPrice(bidContractOrder.getPrice());
        }
        Boolean sendRet = rocketMqManager.sendMessage("match", "contract", String.valueOf(contractMatchedOrderDO.getId()), orderMessage);
        if (!sendRet) {
            log.error("Send RocketMQ Message Failed ");
        }
        try {
            updateTotalPosition(contractMatchedOrderDO, resultMap);
        }catch (Throwable t) {
            log.error("update total position failed,resultMap={}", JSON.toJSONString(resultMap), t);
        }
    }

    public UpdatePositionResult updatePosition(ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice){
        return internalUpdatePosition(contractOrderDO, filledAmount, filledPrice);
    }

    public UpdatePositionResult internalUpdatePosition(ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice){
        UserPositionDO userPositionDO;
        long userId = contractOrderDO.getUserId();
        long contractId = contractOrderDO.getContractId();

        UpdatePositionResult result = new UpdatePositionResult();

        userPositionDO = userPositionMapper.selectByUserIdAndContractId(userId, contractId);
        if (userPositionDO == null) {
            // 建仓
            userPositionDO = ContractUtils.buildPosition(contractOrderDO, contractOrderDO.getLever(), filledAmount, filledPrice);
            userPositionMapper.insert(userPositionDO);
            return result;
        }

        BigDecimal newTotalAmount;
        int newPositionType=userPositionDO.getPositionType();
        BigDecimal newAveragePrice = null;

        result.setOpenAveragePrice(userPositionDO.getAveragePrice());
        result.setOpenPositionDirection(ContractUtils.toDirection(userPositionDO.getPositionType()));

        if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
            //成交单和持仓是同方向
            newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount);
            newTotalAmount = userPositionDO.getUnfilledAmount().add(filledAmount);
        }
        //成交单和持仓是反方向 （平仓）
        else if (filledAmount.compareTo(userPositionDO.getUnfilledAmount()) <= 0) {
            //不改变仓位方向
            newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount);
            newTotalAmount = userPositionDO.getUnfilledAmount().subtract(filledAmount);
            result.setCloseAmount(filledAmount.min(userPositionDO.getUnfilledAmount()));
        } else {
            //改变仓位方向
            newAveragePrice = computeAveragePrice(contractOrderDO, userPositionDO, filledPrice, filledAmount);
            newPositionType = contractOrderDO.getOrderDirection();
            result.setCloseAmount(filledAmount.min(userPositionDO.getUnfilledAmount()));
            newTotalAmount = filledAmount.subtract(userPositionDO.getUnfilledAmount());
        }
        result.setNewPositionType(newPositionType);
        result.setNewTotalAmount(newTotalAmount);
        boolean suc =  doUpdatePosition(userPositionDO, newAveragePrice, newTotalAmount, newPositionType);
        if (!suc) {
            throw new BizException(CONCURRENT_PROBLEM.getCode(), "doUpdate position failed");
        }
        return result;
    }

    private boolean insertPosition(UserPositionDO userPositionDO){
        try {
            int aff = userPositionMapper.insert(userPositionDO);
            return 1 == aff;
        }catch (Throwable t) {
            log.error("insert position exception", t);
            return false;
        }
    }



    public ContractDealer calBalanceChange(ContractOrderDO contractOrderDO, BigDecimal filledAmount, BigDecimal filledPrice,
                                           UpdatePositionResult positionResult){
        long userId = contractOrderDO.getUserId();
        BigDecimal rate = contractOrderDO.getFee();


        UserContractDTO userContractDTO = assetService.getContractAccount(userId);

        //没有平仓，不用计算
        if (null == positionResult.getCloseAmount()) {
            return null;
        }
        //手续费
        BigDecimal actualFee = filledPrice.multiply(filledAmount).multiply(rate);

        //计算平仓盈亏
        // (filledPrice-openAveragePrice)*closeAmount*contractSize*openPositionDirection - actualFee
        BigDecimal addAmount = filledPrice.subtract(positionResult.getOpenAveragePrice())
                .multiply(positionResult.getCloseAmount())

                .multiply(new BigDecimal(positionResult.getOpenPositionDirection()))
                .subtract(actualFee);

        ContractDealer dealer = new ContractDealer()
                .setUserId(userId)
                .setAddedTotalAmount(addAmount)
                .setTotalLockAmount(BigDecimal.ZERO)
                ;
        dealer.setDealType(ContractDealer.DealType.FORCE);
        return dealer;
    }

//    private void updateContractAccount(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO contractMatchedOrderDTO) {
//        BigDecimal filledAmount = new BigDecimal(contractMatchedOrderDTO.getFilledAmount());
//        BigDecimal filledPrice = new BigDecimal(contractMatchedOrderDTO.getFilledPrice());
//        Long userId = contractOrderDO.getUserId();
//        Long contractId = contractOrderDO.getContractId();
//        UserPositionDO userPositionDO = userPositionMapper.selectByUserIdAndId(userId, contractId);
//
//        BigDecimal contractSize = getContractSize(contractOrderDO.getContractId());
//        Integer lever = new Integer(contractLeverManager.getLeverByContractId(contractOrderDO.getUserId(), contractOrderDO.getContractId()));
//        if (userPositionDO == null) {
//            // 建仓
//            buildPosition(contractOrderDO, contractMatchedOrderDTO, contractSize, lever);
//            return;
//        }
//        long oldPositionAmount = userPositionDO.getUnfilledAmount();
//        if (contractOrderDO.getOrderDirection().equals(userPositionDO.getPositionType())) {
//            //成交单和持仓是同方向
//            long newTotalAmount = userPositionDO.getUnfilledAmount() + filledAmount.longValue();
//            BigDecimal oldTotalPrice = userPositionDO.getAveragePrice().multiply(new BigDecimal(userPositionDO.getUnfilledAmount()));
//            BigDecimal addedTotalPrice = filledPrice.multiply(filledAmount);
//            BigDecimal newTotalPrice = oldTotalPrice.add(addedTotalPrice);
//            BigDecimal newAvaeragePrice = newTotalPrice.divide(new BigDecimal(newTotalAmount), 8, BigDecimal.ROUND_DOWN);
//            doUpdatePosition(userPositionDO, newAvaeragePrice, newTotalAmount);
//            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO, lever);
//            return;
//        }
//
//        //成交单和持仓是反方向 （平仓）
//        if (filledAmount.longValue() - userPositionDO.getUnfilledAmount() <= 0) {
//            //不改变仓位方向
//            long newTotalAmount = userPositionDO.getUnfilledAmount() - filledAmount.longValue();
//            BigDecimal newAvaeragePrice = null;
//            if (newTotalAmount != 0){
//                newAvaeragePrice = userPositionDO.getAveragePrice().setScale(8, BigDecimal.ROUND_DOWN);
//            }
//            doUpdatePosition(userPositionDO, newAvaeragePrice, newTotalAmount);
//            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO, lever);
//        } else {
//            //改变仓位方向
//            long newTotalAmount = filledAmount.longValue() - userPositionDO.getUnfilledAmount();
//            BigDecimal newAvaeragePrice = filledPrice.setScale(8, BigDecimal.ROUND_DOWN);;
//            userPositionDO.setPositionType(contractOrderDO.getOrderDirection());
//            doUpdatePosition(userPositionDO, newAvaeragePrice, newTotalAmount);
//            updateBalance(contractOrderDO, oldPositionAmount, userPositionDO.getUnfilledAmount(), contractMatchedOrderDTO, lever);
//        }
//    }

//    private void buildPosition(ContractOrderDO contractOrderDO, ContractMatchedOrderDTO matchedOrderDTO,
//                               BigDecimal contractSize, Integer lever) {
//        UserPositionDO newUserPositionDO = new UserPositionDO();
//        newUserPositionDO.setPositionType(contractOrderDO.getOrderDirection());
//        newUserPositionDO.setAveragePrice(new BigDecimal(matchedOrderDTO.getFilledPrice()));
//        newUserPositionDO.setUnfilledAmount(matchedOrderDTO.getFilledAmount());
//        newUserPositionDO.setStatus(1);
//        newUserPositionDO.setUserId(contractOrderDO.getUserId());
//        newUserPositionDO.setContractName(contractOrderDO.getContractName());
//        newUserPositionDO.setContractId(contractOrderDO.getContractId());
//        newUserPositionDO.setContractSize(contractSize);
//        newUserPositionDO.setLever(lever);
//        try {
//            int insertRet = userPositionMapper.insert(newUserPositionDO);
//            if (insertRet != 1) {
//                log.error("buildPosition failed");
//            }
//        } catch (Exception e) {
//            log.error("userPositionMapper.insert({})", newUserPositionDO, e);
//        }
//        int updateBalanceRet = updateBalance(contractOrderDO, 0L, matchedOrderDTO.getFilledAmount(), matchedOrderDTO, lever);
//        if (updateBalanceRet != 1) {
//            log.error("buildPosition updateBalance failed");
//        }
//    }

    /**
     * @param userPositionDO  旧的持仓
     * @param newAvaeragePrice 新的开仓均价
     * @param newTotalAmount  新的持仓数量
     * @return
     */
    public boolean doUpdatePosition(UserPositionDO userPositionDO, BigDecimal newAvaeragePrice, BigDecimal newTotalAmount, int positionType) {
        userPositionDO.setAveragePrice(newAvaeragePrice);
        userPositionDO.setUnfilledAmount(newTotalAmount);
        userPositionDO.setPositionType(positionType);
        try{
            int aff = userPositionMapper.updatePositionById(userPositionDO);
            return aff == 1;
        }catch (Throwable t) {
            log.error("update position exception", t);
            return false;
        }
    }


    //合约账户amoutn: + (oldPositionAmount - 当前持仓)*合约价格 - 手续费
    //合约账户冻结：解冻委托价*合约份数 + 手续费
//    private int updateBalance(ContractOrderDO contractOrderDO,
//                              long oldPositionAmount,
//                              long newPositionAmount,
//                              ContractMatchedOrderDTO matchedOrderDTO,
//                              Integer lever) {
//        long filledAmount = matchedOrderDTO.getFilledAmount();
//        BigDecimal filledPrice = new BigDecimal(matchedOrderDTO.getFilledPrice());
//        BigDecimal fee = contractOrderDO.getFee();
//        BigDecimal contractSize = getContractSize(contractOrderDO.getContractId());
//        BigDecimal actualFee = filledPrice.multiply(new BigDecimal(filledAmount)).multiply(fee);
//        BigDecimal addedTotalAmount = new BigDecimal(oldPositionAmount - newPositionAmount)
//                .multiply(filledPrice)
//
//                .divide(new BigDecimal(lever), 8, BigDecimal.ROUND_DOWN)
//                .subtract(actualFee);
//        UserContractDTO userContractDTO = new UserContractDTO();
//        try {
//            userContractDTO = getAssetService().getContractAccount(contractOrderDO.getUserId());
//        } catch (Exception e) {
//            log.error(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
//            throw new RuntimeException(ResultCodeEnum.ASSET_SERVICE_FAILED.getMessage());
//        }
//        BigDecimal lockedAmount = new BigDecimal(userContractDTO.getLockedAmount());
//        BigDecimal totalLockAmount = null;
//        tradeLog.info("match id {}", matchedOrderDTO.getId());
//        try {
//            totalLockAmount = getTotalLockAmount(contractOrderDO.getUserId());
//        } catch (Exception e) {
//            log.error("get totalLockAmount failed", e);
//        }
//        //todo 更新余额
//        BigDecimal addedTotalLocked = totalLockAmount.subtract(lockedAmount);
//
//        ContractDealer dealer = new ContractDealer()
//                .setUserId(contractOrderDO.getUserId())
//                .setAddedTotalAmount(addedTotalAmount)
//                .setAddedLockAmount(addedTotalLocked);
//        dealer.setDealType((null != contractOrderDO.getOrderType() && ENFORCE.getCode() == contractOrderDO.getOrderType()) ? ContractDealer.DealType.FORCE : ContractDealer.DealType.NORMAL);
//
//        com.fota.common.Result result = getContractService().updateBalances(dealer);
//        if (!result.isSuccess()) {
//            log.error("update contract balance failed");
//            throw new RuntimeException("update balance failed");
//        }
//        return 1;
//    }


    public void updateContractOrder(long id, BigDecimal filledAmount, BigDecimal filledPrice, Date gmtModified) {
        int aff;
        try{
            aff = contractOrderMapper.updateAmountAndStatus(id, filledAmount, filledPrice, gmtModified);
        }catch (Throwable t) {
            throw new RuntimeException(String.format("update contract order failed, orderId=%d, filledAmount=%s, filledPrice=%s, gmtModified=%s",
                    id, filledAmount, filledPrice, gmtModified),t);
        }
        if (0 == aff) {
            throw new RuntimeException(String.format("update contract order failed, orderId={}, filledAmount={}, filledPrice={}, gmtModified={}",
                    id, filledAmount, filledPrice, gmtModified));
        }
    }

    public void updateTotalPosition(ContractMatchedOrderDO contractMatchedOrderDO, Map<ContractOrderDO, UpdatePositionResult> resultMap){
        BigDecimal increase;
        Long bidUserId = contractMatchedOrderDO.getBidUserId();
        Long askUserId = contractMatchedOrderDO.getAskUserId();
        if (askUserId.equals(bidUserId) || resultMap == null || resultMap.keySet().isEmpty()) {
            return;
        }
        BigDecimal filledAmount = contractMatchedOrderDO.getFilledAmount();
        BigDecimal bidPositionAmount = BigDecimal.ZERO, askPositionAmount = BigDecimal.ZERO;
        for (ContractOrderDO key : resultMap.keySet()) {
            if (key.getUserId().equals(bidUserId)){
                UpdatePositionResult bidPosition = resultMap.get(key);
                if(bidPosition != null) {
                    bidPositionAmount = bidPosition.getNewTotalAmount().multiply(bidPosition.getNewPositionType() == 1 ? BigDecimal.valueOf(-1) : BigDecimal.ONE);
                } else{
                    log.info("update position find null position userId :{}, order:{}", bidUserId, key.toString());
                }
            } else if (key.getUserId().equals(askUserId)) {
                UpdatePositionResult askPosition = resultMap.get(key);
                if(askPosition != null) {
                    askPositionAmount = askPosition.getNewTotalAmount().multiply(askPosition.getNewPositionType() == 1 ? BigDecimal.valueOf(-1) : BigDecimal.ONE);
                } else {
                    log.info("update position find null position userId :{}, order :{}", askUserId, key.toString());
                }
            }
        }
        BigDecimal formerBidPositionAmount = bidPositionAmount.subtract(filledAmount);
        BigDecimal formerAskPositionAmount = askPositionAmount.add(filledAmount);
        increase = bidPositionAmount.abs().subtract(formerBidPositionAmount.abs()).add(askPositionAmount.abs()).subtract(formerAskPositionAmount.abs());
        Double currentPosition = redisManager.counter(Constant.CONTRACT_TOTAL_POSITION + contractMatchedOrderDO.getContractId(), increase);
        if (BigDecimal.valueOf(currentPosition).compareTo(increase) == 0) {
            BigDecimal position = userPositionMapper.countTotalPosition(contractMatchedOrderDO.getContractId()).multiply(BigDecimal.valueOf(2));
            redisManager.counter(Constant.CONTRACT_TOTAL_POSITION + contractMatchedOrderDO.getContractId(), position.subtract(increase));
        }
        log.info("update total position------contractId :{}   currentPosition :{}", contractMatchedOrderDO.getContractId(), currentPosition);

    }


    /**
     *判断新的合约委托能否下单
     * @param userId
     * @param newContractOrderDO
     * @return
     */
    public Boolean judegOrderAvailable(long userId, ContractOrderDO newContractOrderDO) {
        ContractAccount contractAccount = new ContractAccount();
        contractAccount.setMarginCallRequirement(BigDecimal.ZERO)
                .setFrozenAmount(BigDecimal.ZERO)
                .setFloatingPL(BigDecimal.ZERO)
                .setUserId(userId);
        Long orderContractId = newContractOrderDO.getContractId();
        Integer orderDeriction = newContractOrderDO.getOrderDirection();
        UserContractDTO userContractDTO = assetService.getContractAccount(userId);
        if (null == userContractDTO) {
            log.error("null userContractDTO, userId={}", userId);
            return false;
        }

        List<ContractCategoryDO> categoryList = contractCategoryMapper.getAllContractCategory();
        if (CollectionUtils.isEmpty(categoryList)) {
            log.error("empty categoryList");
            return false;
        }
        List<UserPositionDO> allPositions = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
        List<CompetitorsPriceDTO> competitorsPrices = realTimeEntrust.getContractCompetitorsPrice();
        List<UserContractLeverDO> contractLeverDOS = userContractLeverMapper.listUserContractLever(userId);

        List<ContractOrderDO> allContractOrders = contractOrderMapper.selectNotEnforceOrderByUserId(userId);
        if (null == allContractOrders) {
            allContractOrders = new ArrayList<>();
        }
        if (null != newContractOrderDO) {
            allContractOrders.add(newContractOrderDO);
        }

        for (ContractCategoryDO contractCategoryDO : categoryList) {

            long contractId = contractCategoryDO.getId();
            BigDecimal lever = findLever(contractLeverDOS, userId, contractCategoryDO.getAssetId());
            BigDecimal positionMargin = BigDecimal.ZERO;
            BigDecimal floatingPL = BigDecimal.ZERO;
            BigDecimal entrustMargin = BigDecimal.ZERO;
            BigDecimal positionUnfilledAmount= BigDecimal.ZERO;
            int positionType = PositionTypeEnum.EMPTY.getCode();


            List<ContractOrderDO> orderList = null;
            if (!CollectionUtils.isEmpty(allContractOrders)) {
                orderList = allContractOrders.stream().filter(contractOrder -> contractOrder.getContractId().equals(contractId))
                        .collect(toList());
            }


            Optional<UserPositionDO> userPositionDOOptional = allPositions.stream().filter(userPosition -> userPosition.getContractId().equals(contractCategoryDO.getId()))
                    .findFirst();

            //计算保证金，浮动盈亏
            if (userPositionDOOptional.isPresent()) {
                UserPositionDO userPositionDO = userPositionDOOptional.get();
                positionType = userPositionDO.getPositionType();
                positionUnfilledAmount = userPositionDO.getUnfilledAmount();
                BigDecimal positionAveragePrice = userPositionDO.getAveragePrice();

                int dire = ContractUtils.toDirection(userPositionDO.getPositionType());

                BigDecimal price = computePrice(competitorsPrices, userPositionDO.getPositionType(), contractId);
                if (null == price) {
                    return null;
                }
                floatingPL = price.subtract(positionAveragePrice).multiply(positionUnfilledAmount).multiply(new BigDecimal(dire));

                positionMargin = positionUnfilledAmount.multiply(price).divide(lever, CommonUtils.scale, BigDecimal.ROUND_UP);

                //todo 持仓反方向的"仓加挂"小于该合约持仓保证金，允许下单
                if (contractCategoryDO.getId().equals(orderContractId) && positionType != orderDeriction){
                    //计算委托额外保证金
                    if (!CollectionUtils.isEmpty(orderList)) {
                        List<ContractOrderDO> filterOrderList = orderList.stream().filter(order -> order.getOrderDirection().equals(orderDeriction)).collect(toList());
                        Boolean judgeRet = judgeOrderResult(filterOrderList, positionType, positionUnfilledAmount, positionMargin, lever);
                        if (judgeRet){
                            return true;
                        }
                    }
                }
            }

            //计算委托额外保证金
            if (!CollectionUtils.isEmpty(orderList)) {
                List<ContractOrderDO> bidList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.BID.getCode()).collect(toList());
                List<ContractOrderDO> askList = orderList.stream().filter(order -> order.getOrderDirection() == OrderDirectionEnum.ASK.getCode()).collect(toList());
                entrustMargin = getExtraEntrustAmount(bidList, askList, positionType, positionUnfilledAmount, positionMargin, lever);
            }

            contractAccount.setMarginCallRequirement(contractAccount.getMarginCallRequirement().add(positionMargin))
                    .setFrozenAmount(contractAccount.getFrozenAmount().add(entrustMargin))
                    .setFloatingPL(contractAccount.getFloatingPL().add(floatingPL));

        }
        BigDecimal amount = new BigDecimal(userContractDTO.getAmount());
        BigDecimal availableAmount = amount.add(contractAccount.getFloatingPL())
                .subtract(contractAccount.getMarginCallRequirement())
                .subtract(contractAccount.getFrozenAmount());
        if (availableAmount.compareTo(BigDecimal.ZERO) >= 0 ){
            return true;
        }
        return false;
    }
}
