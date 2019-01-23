package com.fota.trade.manager;

import com.fota.asset.domain.ContractAccountAddAmountDTO;
import com.fota.trade.mapper.asset.UserContractMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Gavin Shen
 * @Date 2019/1/21
 */
@Slf4j
@Component
public class ContractManager {

    private static final Logger assetSuccessLog = LoggerFactory.getLogger("assetOperateSuccess");
    private static final Logger assetFailedLog = LoggerFactory.getLogger("assetOperateFailed");

    @Autowired
    private UserContractMapper userContractMapper;

    public boolean addContractAmount(ContractAccountAddAmountDTO contractAccountAddAmountDTO, String refId, Integer sourceId) {
        try {
            int result = userContractMapper.updateBalanceUnlimit(contractAccountAddAmountDTO.getUserId(), contractAccountAddAmountDTO.getAddAmount());
            if (result > 0) {
                assetSuccessLog.info("success add Contract amount! sourceId: {}, refId:{}, requestData:{}",
                        sourceId, refId, contractAccountAddAmountDTO);
                return true;
            }else {
                assetFailedLog.error("fail to add Capital amount! sourceId: {}, refId:{}, requestData:{}",
                        sourceId, refId, contractAccountAddAmountDTO);
            }
        }catch (Exception e) {
            assetFailedLog.error("addContractAmount failed with Exception,sourceId: {}, refId:{},  requesetData:{}"
                    ,sourceId, refId,  contractAccountAddAmountDTO, e);
        }
        return false;
    }

}
