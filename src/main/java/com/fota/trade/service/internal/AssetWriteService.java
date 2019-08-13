package com.fota.trade.service.internal;

import com.fota.asset.domain.CapitalAccountAddAmountDTO;
import com.fota.asset.domain.ContractAccountAddAmountDTO;
import com.fota.common.Result;
import com.fota.common.ResultCodeEnum;
import com.fota.trade.manager.CapitalManager;
import com.fota.trade.manager.ContractManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * @author Gavin Shen
 * @Date 2019/1/21
 */
@Slf4j
@Service
public class AssetWriteService {
    @Resource
    private CapitalManager capitalManager;
    @Resource
    private ContractManager contractManager;

    private static final Logger assetLog = LoggerFactory.getLogger(AssetWriteService.class);

    private static final Logger assetFailedLog = LoggerFactory.getLogger("assetOperateFailed");

    public Result<Boolean> addCapitalAmount(CapitalAccountAddAmountDTO capitalAccountAddAmountDTO, String refId, Integer sourceId) {
        Result<Boolean> ret = new Result<>();
        if (Objects.isNull(capitalAccountAddAmountDTO) || Objects.isNull(refId) || Objects.isNull(sourceId)
                || Objects.isNull(capitalAccountAddAmountDTO.getAssetId())
                || Objects.isNull(capitalAccountAddAmountDTO.getUserId())) {
            assetLog.error("request para is null, sourceId: {}, refId:{}, requestData:{}", sourceId, refId, capitalAccountAddAmountDTO);
            ret.setData(false);
            return ret.error(ResultCodeEnum.ILLEGAL_PARAM);
        }
        boolean result = capitalManager.addCapitalAmount(capitalAccountAddAmountDTO, refId, sourceId);
        if (result) {
            capitalManager.sendAddCapitalAmountMQ(capitalAccountAddAmountDTO);
        }
        return ret.success(result);
    }

    /**
     * 撤单逻辑变更， 撤单的时候，不会校验amount是否为负，可以直接进行撤单
     * @param capitalAccountAddAmountDTO capitalAccountAddAmountDTO
     * @param refId ref id
     * @param sourceId source id
     * @return insert result
     */
    public Result<Boolean> addCapitalAmountWithoutLocked(CapitalAccountAddAmountDTO capitalAccountAddAmountDTO, String refId, Integer sourceId) {
        Result<Boolean> ret = new Result<>();
        if (Objects.isNull(capitalAccountAddAmountDTO) || Objects.isNull(refId) || Objects.isNull(sourceId)
                || Objects.isNull(capitalAccountAddAmountDTO.getAssetId())
                || Objects.isNull(capitalAccountAddAmountDTO.getUserId())) {
            assetLog.error("request para is null, sourceId: {}, refId:{}, requestData:{}", sourceId, refId, capitalAccountAddAmountDTO);
            ret.setData(false);
            return ret.error(ResultCodeEnum.ILLEGAL_PARAM);
        }
        boolean result = capitalManager.addCapitalAmountWithoutLocked(capitalAccountAddAmountDTO, refId, sourceId);
        if (result) {
            capitalManager.sendAddCapitalAmountMQ(capitalAccountAddAmountDTO);
        }
        return ret.success(result);
    }

    public Result<Boolean> batchAddCapitalAmount(List<CapitalAccountAddAmountDTO> list, String refId, Integer sourceId) {
        Result<Boolean> ret = new Result<>();
        if (Objects.isNull(list) || Objects.isNull(refId) || Objects.isNull(sourceId)
                || !capitalManager.checkCapitalAccountAddAmountDTOList(list)) {
            assetLog.error("para is null, sourceId: {}, refId:{}, requestData:{}", sourceId, refId, list);
            ret.setData(false);
            return ret.error(ResultCodeEnum.ILLEGAL_PARAM);
        }
        try {
            capitalManager.batchAddCapitalAmount(list, refId, sourceId);
        }catch (Exception e) {
            assetFailedLog.error("batchAddCapitalAmount failed! rollback batchAddCapitalAmount. sourceId: {}, refId:{}, requestData:{}",sourceId, refId, list, e);
            ret.setData(false);
            return ret.error(ResultCodeEnum.SERVICE_EXCEPTION.getCode(), e.getMessage());
        }
        capitalManager.sendAddCapitalAmountMQ(list);
        return ret.success(true);
    }

    public Result<Boolean> addContractAmount(ContractAccountAddAmountDTO contractAccountAddAmountDTO, String refId, Integer sourceId) {
        Result<Boolean> ret = new Result<>();
        if (Objects.isNull(contractAccountAddAmountDTO) || Objects.isNull(refId) || Objects.isNull(sourceId)
                || Objects.isNull(contractAccountAddAmountDTO.getUserId())
                || Objects.isNull(contractAccountAddAmountDTO.getAddAmount())) {
            assetLog.error("para is null, sourceId: {}, refId:{}, requestData:{}", sourceId, refId, contractAccountAddAmountDTO);
            ret.setData(false);
            return ret.error(ResultCodeEnum.ILLEGAL_PARAM);
        }
        boolean result = contractManager.addContractAmount(contractAccountAddAmountDTO, refId, sourceId);
        return ret.success(result);
    }

}
