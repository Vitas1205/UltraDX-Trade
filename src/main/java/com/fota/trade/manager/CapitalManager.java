package com.fota.trade.manager;

import com.alibaba.fastjson.JSON;
import com.fota.asset.domain.CapitalAccountAddAmountDTO;
import com.fota.asset.domain.UserCapitalVariationDTO;
import com.fota.trade.domain.UserCapitalDO;
import com.fota.trade.domain.UserContractDO;
import com.fota.trade.mapper.asset.UserCapitalMapper;
import com.fota.trade.mapper.asset.UserContractMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Gavin Shen
 * @Date 2019/1/21
 */
@Slf4j
@Component
public class CapitalManager {

    private static final Logger assetSuccessLog = LoggerFactory.getLogger("assetOperateSuccess");
    private static final Logger assetFailedLog = LoggerFactory.getLogger("assetOperateFailed");

    @Resource
    private UserCapitalMapper userCapitalMapper;
    @Resource
    private UserContractMapper userContractMapper;
    @Autowired
    private RocketMqManager rocketMQManager;

    public UserCapitalVariationDTO buildCapitalVariationDTO(boolean isCapital, Long userId,
                                                            Integer assetId, String variation, String lockedVariation) {
        UserCapitalVariationDTO userCapitalVariationDTO = new UserCapitalVariationDTO();
        userCapitalVariationDTO.setUserId(userId);
        userCapitalVariationDTO.setAccountType(isCapital ? 1 : 2);
        userCapitalVariationDTO.setAssetId(assetId);
        userCapitalVariationDTO.setVariation(variation);
        userCapitalVariationDTO.setLockedVariation(lockedVariation);
        if (isCapital) {
            UserCapitalDO userCapitalDO = userCapitalMapper.getCapitalByAssetId(userId, assetId);
            if (userCapitalDO != null) {
                userCapitalVariationDTO.setTotalAmount(userCapitalDO.getAmount().toPlainString());
                userCapitalVariationDTO.setTimestamp(userCapitalDO.getGmtModified().getTime());
                userCapitalVariationDTO.setLockedAmount(userCapitalDO.getOrderLockedAmount()
                        .add(userCapitalDO.getWithdrawLockedAmount()).toPlainString());
            }
        } else {
            UserContractDO userContractDO = userContractMapper.getByUserId(userId);
            if (userContractDO != null) {
                userCapitalVariationDTO.setLockedAmount(userContractDO.getLockedAmount().toPlainString());
                userCapitalVariationDTO.setTotalAmount(userContractDO.getAmount().toPlainString());
                userCapitalVariationDTO.setTimestamp(userContractDO.getGmtModified().getTime());
            }
        }
        return userCapitalVariationDTO;
    }

    public boolean addCapitalAmount(CapitalAccountAddAmountDTO capitalAccountAddAmountDTO, String refId, Integer sourceId) {
        try {
            int result = userCapitalMapper.addCapitalAmount(capitalAccountAddAmountDTO);
            if (result > 0) {
                assetSuccessLog.info("success add Capital amount! sourceId: {}, refId:{}, requestData:{}",
                        sourceId, refId, capitalAccountAddAmountDTO);
                return true;
            }else {
                assetFailedLog.error("fail to add Capital amount! sourceId: {}, refId:{}, requestData:{}",
                        sourceId, refId, capitalAccountAddAmountDTO);
            }
        } catch (Exception e) {
            assetFailedLog.error("userCapitalMapper.addCapitalAmount failed with exception, sourceId: {}, refId:{}, requestData: {},"
                    , sourceId, refId, capitalAccountAddAmountDTO, e);
        }
        return false;
    }

    private static final Comparator<CapitalAccountAddAmountDTO> comparator = (a,b) -> {
        if (a.getUserId() > b.getUserId()) {
            return 1;
        }
        if (a.getUserId() < b.getUserId()){
            return -1;
        }
        return a.getAssetId().compareTo(b.getAssetId());
    };

    @Transactional(transactionManager = "assetTransactionManager", rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void batchAddCapitalAmount(List<CapitalAccountAddAmountDTO> list, String refId, Integer sourceId) {
        list.sort(comparator);
        for (int i = 0;i<list.size();i++) {
            CapitalAccountAddAmountDTO capitalAccountAddAmountDTO = list.get(i);
            boolean singleResult = addCapitalAmount(capitalAccountAddAmountDTO, refId, sourceId);
            if (!singleResult) {
                throw new RuntimeException("addCapitalAmount failed, index={}"+ i);
            }
        }
    }

    public void sendAddCapitalAmountMQ(CapitalAccountAddAmountDTO capitalAccountAddAmountDTO) {
        BigDecimal addOrderLock = capitalAccountAddAmountDTO.getAddOrderLocked() == null ? BigDecimal.ZERO : capitalAccountAddAmountDTO.getAddOrderLocked();
        BigDecimal addWithdrawLock = capitalAccountAddAmountDTO.getAddWithdrawLocked() == null ? BigDecimal.ZERO : capitalAccountAddAmountDTO.getAddWithdrawLocked();

        UserCapitalVariationDTO userCapitalVariationDTO =
                buildCapitalVariationDTO(true, capitalAccountAddAmountDTO.getUserId(),
                        capitalAccountAddAmountDTO.getAssetId(),
                        capitalAccountAddAmountDTO.getAddTotal() == null ? "0" : capitalAccountAddAmountDTO.getAddTotal().toPlainString(),
                        addOrderLock.add(addWithdrawLock).toPlainString());
        String json = JSON.toJSONString(userCapitalVariationDTO);
        UUID id = UUID.randomUUID();
        rocketMQManager.sendMQ("user-capital","variation", id.toString(),json);
    }

    public void sendAddCapitalAmountMQ(List<CapitalAccountAddAmountDTO> list) {
        for (CapitalAccountAddAmountDTO capitalAccountAddAmountDTO : list) {
            sendAddCapitalAmountMQ(capitalAccountAddAmountDTO);
        }
    }

    public boolean checkCapitalAccountAddAmountDTOList(List<CapitalAccountAddAmountDTO> list){
        for (CapitalAccountAddAmountDTO capitalAccountAddAmountDTO : list){
            if (Objects.isNull(capitalAccountAddAmountDTO.getUserId())
                    || Objects.isNull(capitalAccountAddAmountDTO.getAssetId())) {
                return false;
            }
        }
        return true;
    }

}