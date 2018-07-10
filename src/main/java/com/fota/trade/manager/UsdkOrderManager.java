package com.fota.trade.manager;

import com.alibaba.rocketmq.shade.com.alibaba.fastjson.JSONObject;
import com.fota.asset.domain.UserCapitalDTO;
import com.fota.asset.service.AssetService;
import com.fota.asset.service.CapitalService;
import com.fota.client.common.ResultCode;
import com.fota.client.domain.UsdkOrderDTO;
import com.fota.thrift.ThriftJ;
import com.fota.trade.cache.RedisCache;
import com.fota.trade.common.Constant;
import com.fota.trade.domain.UsdkOrderDO;
import com.fota.trade.domain.enums.AssetTypeEnum;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.OrderStatusEnum;
import com.fota.trade.mapper.UsdkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * @Author: Harry Wang
 * @Descripyion:
 * @Date: Create in 2018/7/7
 * @Modified:
 */

@Component
@Slf4j
public class UsdkOrderManager {

    private static BigDecimal usdkFee = BigDecimal.valueOf(0.001);

    @Autowired
    private UsdkOrderMapper usdkOrderMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    RedisManager redisManager;

    @Autowired
    private ThriftJ thriftJ;
    @Value("${fota.asset.server.thrift.port}")
    private int thriftPort;
    @PostConstruct
    public void init() {
        thriftJ.initService("FOTA-ASSET", thriftPort);
    }
    private CapitalService.Client getCapitalService() {
        CapitalService.Client serviceClient =
                thriftJ.getServiceClient("FOTA-ASSET")
                        .iface(CapitalService.Client.class, "capitalService");
        return serviceClient;
    }
    private AssetService.Client getAssetService() {
        AssetService.Client serviceClient =
                thriftJ.getServiceClient("FOTA-ASSET")
                        .iface(AssetService.Client.class, "assetService");
        return serviceClient;
    }

    public List<UsdkOrderDO> listNotMatchOrder(Long contractOrderIndex, Integer orderDirection) {
        List<UsdkOrderDO> notMatchOrderList = null;
        try {
            notMatchOrderList = usdkOrderMapper.notMatchOrderList(
                    OrderStatusEnum.COMMIT.getCode(), OrderStatusEnum.PART_MATCH.getCode(), contractOrderIndex, orderDirection);
        } catch (Exception e) {
            log.error("usdkOrderMapper.notMatchOrderList error", e);
        }
        if (notMatchOrderList == null) {
            notMatchOrderList = new ArrayList<>();
        }
        return notMatchOrderList;
    }

    public ResultCode placeOrder(UsdkOrderDO usdkOrderDO)throws Exception {
        ResultCode resultCode = new ResultCode();
        Integer assetId = usdkOrderDO.getAssetId();
        Long userId = usdkOrderDO.getUserId();
        Integer orderDirection = usdkOrderDO.getOrderDirection();
        BigDecimal price = usdkOrderDO.getPrice();
        BigDecimal totalAmount = usdkOrderDO.getTotalAmount();
        BigDecimal orderValue = totalAmount.multiply(price);
        List<UserCapitalDTO> list = getAssetService().getUserCapital(userId);
        if (orderDirection == OrderDirectionEnum.BID.getCode()){
            //查询usdk账户可用余额
            for(UserCapitalDTO userCapitalDTO : list){
                if (userCapitalDTO.getAssetId() == AssetTypeEnum.USDK.getCode()){
                    BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                    BigDecimal lockedAmount = new BigDecimal(userCapitalDTO.getLockedAmount());
                    BigDecimal availableAmount = amount.subtract(lockedAmount);
                    //判断账户可用余额是否大于orderValue
                    if (availableAmount.compareTo(orderValue) >= 0){
                        Long gmtModified = userCapitalDTO.getGmtModified();
                        Boolean ret = getCapitalService().updateLockedAmount(userId, userCapitalDTO.getAssetId(), String.valueOf(orderValue), gmtModified);
                        if (!ret){
                            resultCode = ResultCode.error(3,"update USDKCapital LockedAmount failed");
                            return resultCode;
                        }
                    }else {
                        resultCode = ResultCode.error(4,"USDK Capital Amount Not Enough");
                        return resultCode;
                    }
                }
            }
        }else if (orderDirection == OrderDirectionEnum.ASK.getCode()){
            //查询对应资产账户可用余额
            for (UserCapitalDTO userCapitalDTO : list){
                if (assetId == userCapitalDTO.getAssetId()){
                    BigDecimal amount = new BigDecimal(userCapitalDTO.getAmount());
                    BigDecimal lockedAmount = new BigDecimal(userCapitalDTO.getLockedAmount());
                    BigDecimal availableAmount = amount.subtract(lockedAmount);
                    //断账户可用余额是否大于tatalValue
                    if (availableAmount.compareTo(usdkOrderDO.getTotalAmount()) >= 0){
                        Long gmtModified = userCapitalDTO.getGmtModified();
                        Boolean ret = getCapitalService().updateLockedAmount(userId, userCapitalDTO.getAssetId(), String.valueOf(usdkOrderDO.getTotalAmount()), gmtModified);
                        if (!ret){
                            resultCode = ResultCode.error(5,"update CoinCapital LockedAmount failed");
                            return resultCode;
                        }
                    }else {
                        resultCode = ResultCode.error(6, "Coin Capital Amount Not Enough");
                        return resultCode;
                    }
                }
            }
        }
        usdkOrderDO.setFee(usdkFee);
        usdkOrderDO.setStatus(OrderStatusEnum.COMMIT.getCode());
        usdkOrderDO.setUnfilledAmount(usdkOrderDO.getTotalAmount());
        int ret = usdkOrderMapper.insertSelective(usdkOrderDO);
        UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
        BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
        if (ret > 0){
            resultCode = ResultCode.success();
            Long count = redisManager.getCount(Constant.REDIS_KEY);
            String key = Constant.USDK_ORDER_HEAD + count;
            usdkOrderDTO.setMatchAmount(BigDecimal.ZERO);
            String usdkOrderDTOStr = JSONObject.toJSONString(usdkOrderDTO);
            redisManager.set(key,usdkOrderDTOStr);
            //todo 发送RocketMQ*/

        }else {
            resultCode = ResultCode.error(7,"Create UsdkOrder failed");
        }
        return resultCode;
    }

    @Transactional(rollbackFor={RuntimeException.class, Exception.class})
    public ResultCode cancelOrder(Long userId, Long orderId) throws Exception{
        ResultCode resultCode = null;
        UsdkOrderDO usdkOrderDO = usdkOrderMapper.selectByIdAndUserId(orderId, userId);
        Integer status = usdkOrderDO.getStatus();
        if (status == OrderStatusEnum.COMMIT.getCode() || status == OrderStatusEnum.CANCEL.getCode()){
            usdkOrderDO.setStatus(OrderStatusEnum.CANCEL.getCode());
        }else if (status == OrderStatusEnum.PART_MATCH.getCode() || status == OrderStatusEnum.PART_CANCEL.getCode()){
            usdkOrderDO.setStatus(OrderStatusEnum.PART_CANCEL.getCode());
        }else if (status == OrderStatusEnum.MATCH.getCode()){
            usdkOrderDO.setStatus(OrderStatusEnum.MATCH.getCode());
        }else {
            resultCode = ResultCode.error(8,"usdkOrder status illegal");
        }
        int ret = usdkOrderMapper.updateByOpLock(usdkOrderDO);
        if (ret > 0){
            //解冻对应账户的冻结资产
            Integer orderDirection = usdkOrderDO.getOrderDirection();
            Integer assetId = 0;
            BigDecimal unlockAmount = BigDecimal.ZERO;
            if (orderDirection == OrderDirectionEnum.BID.getCode()){
                assetId = AssetTypeEnum.USDK.getCode();
                BigDecimal unfilledAmount = usdkOrderDO.getUnfilledAmount();
                BigDecimal price = usdkOrderDO.getPrice();
                unlockAmount = unfilledAmount.multiply(price);
                //解冻USDK钱包账户
                Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,AssetTypeEnum.USDK.getCode(),unlockAmount.negate().toString(), 0L);
                if (!updateLockedAmountRet){
                    resultCode = ResultCode.error(9,"Update USDK LockedAmount Failed");
                    throw new RuntimeException("update Locked Amount failed");
                }
            }else if (orderDirection == OrderDirectionEnum.ASK.getCode()){
                assetId = usdkOrderDO.getAssetId();
                unlockAmount = usdkOrderDO.getUnfilledAmount();
                //解冻Coin钱包账户
                Boolean updateLockedAmountRet = getCapitalService().updateLockedAmount(userId,assetId,unlockAmount.negate().toString(), 0L);
                if (!updateLockedAmountRet){
                    resultCode = ResultCode.error(9,"Update Coin LockedAmount Failed");
                    return resultCode;
                }
            }
            UsdkOrderDTO usdkOrderDTO = new UsdkOrderDTO();
            BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
            BigDecimal matchAmount = usdkOrderDTO.getTotalAmount().subtract(usdkOrderDTO.getUnfilledAmount());
            usdkOrderDTO.setMatchAmount(matchAmount);
            Long count = redisManager.getCount(Constant.REDIS_KEY);
            String key = Constant.USDK_ORDER_HEAD + count;
            String usdkOrderDTOStr = JSONObject.toJSONString(usdkOrderDTO);
            redisManager.set(key,usdkOrderDTOStr);
            //todo 发送RocketMQ
            resultCode = ResultCode.success();
        }else {
            resultCode = ResultCode.error(3,"usdkOrder update failed");
        }

        return resultCode;
    }

    @Transactional(rollbackFor={RuntimeException.class, Exception.class})
    public ResultCode cancelAllOrder(Long userId) throws Exception{
        ResultCode resultCode = null;
        List<UsdkOrderDO> list = usdkOrderMapper.selectByUserId(userId);
        UsdkOrderManager usdkOrderManager = new UsdkOrderManager();
        int ret = 0;
        UsdkOrderDTO usdkOrderDTO = null;
        for(UsdkOrderDO usdkOrderDO : list){
            Long orderId = usdkOrderDO.getId();
            resultCode =  usdkOrderManager.cancelOrder(userId, orderId);
            ret = resultCode.getCode();
            if (ret != ResultCode.success().getCode()){
                throw new RuntimeException("cancelAllOrder failed");
            }else {
                //todo 放入缓存
                BeanUtils.copyProperties(usdkOrderDO,usdkOrderDTO);
                String jsonStr = JSONObject.toJSONString(usdkOrderDTO);
                //redisCache.set(jsonStr);
                //todo 发送RocketMQ
            }
        }
        resultCode = ResultCode.success();
        return resultCode;
    }

}



