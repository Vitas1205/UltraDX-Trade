package com.fota.trade.service.impl;

import com.alibaba.fastjson.JSON;
import com.fota.common.Page;
import com.fota.common.Result;
import com.fota.trade.client.constants.MatchedOrderStatus;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.*;
import com.fota.trade.domain.dto.CompetitorsPriceDTO;
import com.fota.trade.domain.enums.OrderCloseTypeEnum;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.PositionStatusEnum;
import com.fota.trade.domain.query.UserPositionQuery;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.mapper.ContractCategoryMapper;
import com.fota.trade.mapper.ContractMatchedOrderMapper;
import com.fota.trade.mapper.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */

@Slf4j
public class UserPositionServiceImpl implements com.fota.trade.service.UserPositionService {

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    private ContractCategoryMapper contractCategoryMapper;

    @Autowired
    private ContractMatchedOrderMapper contractMatchedOrderMapper;

    @Override
    public Page<UserPositionDTO> listPositionByQuery(long userId, long contractId, int pageNo, int pageSize) {
        UserPositionQuery userPositionQuery = new UserPositionQuery();
        userPositionQuery.setPageNo(pageNo);
        userPositionQuery.setPageSize(pageSize);
        userPositionQuery.setContractId(contractId);
        userPositionQuery.setUserId(userId);
        userPositionQuery.setStatus(PositionStatusEnum.UNDELIVERED.getCode());
        Page<UserPositionDTO> page = new Page<UserPositionDTO>();
        if (userPositionQuery.getPageNo() <= 0) {
            userPositionQuery.setPageNo(Constant.DEFAULT_PAGE_NO);
        }
        if (userPositionQuery.getPageSize() <= 0
                || userPositionQuery.getPageSize() > 50) {
            userPositionQuery.setPageSize(Constant.DEFAULT_MAX_PAGE_SIZE);
        }
        page.setPageNo(userPositionQuery.getPageNo());
        page.setPageSize(userPositionQuery.getPageSize());
        userPositionQuery.setStartRow((userPositionQuery.getPageNo() - 1) * userPositionQuery.getPageSize());
        userPositionQuery.setEndRow(userPositionQuery.getPageSize());
        int total = 0;
        try {
            total = userPositionMapper.countByQuery(ParamUtil.objectToMap(userPositionQuery));
        } catch (Exception e) {
            log.error("userPositionMapper.countByQuery({})", userPositionQuery, e);
            return page;
        }
        page.setTotal(total);
        if (total == 0) {
            return page;
        }
        List<UserPositionDO> userPositionDOList = null;
        List<UserPositionDTO> list = new ArrayList<>();
        try {
            userPositionDOList = userPositionMapper.listByQuery(ParamUtil.objectToMap(userPositionQuery));
            if (userPositionDOList != null && userPositionDOList.size() > 0) {
                for (UserPositionDO tmp : userPositionDOList) {
                    list.add(BeanUtils.copy(tmp));
                }
            }
        } catch (Exception e) {
            log.error("userPositionMapper.listByQuery({})", userPositionQuery, e);
            return page;
        }
//        List<UserPositionDTO> userPositionDTOList = null;
//        try {
//            userPositionDTOList = BeanUtils.copyList(userPositionDOList, UserPositionDTO.class);
//        } catch (Exception e) {
//            log.error("bean copy exception", e);
//            return userPositionDTOPage;
//        }
        page.setData(list);
        return page;
    }



    @Override
    public long getTotalPositionByContractId(long contractId) {
        long totalPosition = 0L;
        List<UserPositionDO> userPositionDOList = userPositionMapper.selectByContractId(contractId,  PositionStatusEnum.UNDELIVERED.getCode());
        if (userPositionDOList != null && userPositionDOList.size() > 0) {
            for (UserPositionDO userPositionDO : userPositionDOList) {
                if (userPositionDO.getContractId().equals(contractId) && userPositionDO.getPositionType() == 1) {
                    totalPosition += userPositionDO.getUnfilledAmount();
                }
            }
        }
        return totalPosition*2;
    }

    /**
     * @author 荆轲
     * @param deliveryCompletedDTO
     * @return
     */
    @Override
    public ResultCode deliveryPosition(DeliveryCompletedDTO deliveryCompletedDTO) {
        try {
            ContractMatchedOrderDO record = new ContractMatchedOrderDO();
            record.setContractId(deliveryCompletedDTO.getContractId());
            record.setContractName(deliveryCompletedDTO.getContractName());
            record.setFee(deliveryCompletedDTO.getFee());
            record.setFilledPrice(deliveryCompletedDTO.getPrice());
            record.setFilledAmount(deliveryCompletedDTO.getAmount());
            record.setStatus(MatchedOrderStatus.VALID);
            record.setAskOrderId(-1L);
            record.setBidOrderId(-1L);
            record.setMatchType((byte) -1);

            Date now = new Date();
            record.setGmtCreate(now);
            record.setGmtModified(now);
            if (Objects.nonNull(deliveryCompletedDTO.getOrderDirection())) {
                deliveryCompletedDTO.setOrderDirection(deliveryCompletedDTO.getOrderDirection());
                if (deliveryCompletedDTO.getOrderDirection() == OrderDirectionEnum.ASK.getCode()) {
                    record.setAskUserId(deliveryCompletedDTO.getUserId());
                    record.setAskCloseType((byte) OrderCloseTypeEnum.EXPIRED.getCode());
                    record.setBidUserId(-1L);
                    record.setBidCloseType((byte) -1);
                } else if (deliveryCompletedDTO.getOrderDirection() == OrderDirectionEnum.BID.getCode()) {
                    record.setBidUserId(deliveryCompletedDTO.getUserId());
                    record.setBidCloseType((byte) OrderCloseTypeEnum.EXPIRED.getCode());
                    record.setAskUserId(-1L);
                    record.setAskCloseType((byte) -1);
                }
            }
            int insertRet = contractMatchedOrderMapper.insert(record);

            UserPositionDO userPositionDO = new UserPositionDO();
            userPositionDO.setId(deliveryCompletedDTO.getUserPositionId());
            userPositionDO.setStatus(2);
            int updateRet = userPositionMapper.updateByPrimaryKeySelective(userPositionDO);
            if (updateRet > 0 && insertRet > 0) {
                return ResultCode.success();
            }
        } catch (Exception e) {
            log.info("deliveryPosition failed, {}", deliveryCompletedDTO, e);
        }
        return ResultCode.error(ResultCodeEnum.DATABASE_EXCEPTION.getCode(), "position delivery failed");
    }


    /**
     * * * @王冕
     * @param userId
     * @return
     */
    @Override
    public List<UserPositionDTO> listPositionByUserId(long userId) {
        try {
            List<UserPositionDO> DOlist = userPositionMapper.selectByUserId(userId, PositionStatusEnum.UNDELIVERED.getCode());
            List<UserPositionDTO> DTOlist = new ArrayList<>();
            if (DOlist != null && DOlist.size() > 0) {
                for (UserPositionDO tmp : DOlist) {
                    DTOlist.add(BeanUtils.copy(tmp));
                }
            }
            return DTOlist;
        }catch (Exception e){
            log.error("listPositionByUserId failed:{}",userId);
        }
        return null;
    }

    /**
     * *@王冕
     * @param contractId
     * @return
     */
    @Override
    public List<UserPositionDTO> listPositionByContractId(Long contractId) {
        try {
            List<UserPositionDO> DOlist = userPositionMapper.selectByContractId(contractId, PositionStatusEnum.UNDELIVERED.getCode());
            List<UserPositionDTO> DTOlist = new ArrayList<>();
            if (DOlist != null && DOlist.size() > 0) {
                for (UserPositionDO tmp : DOlist) {
                    DTOlist.add(BeanUtils.copy(tmp));
                }
            }
            return DTOlist;
        }catch (Exception e){
            log.error("listPositionByContractId failed:{}",contractId);
        }
        return null;
    }

    @Override
    public Result<BigDecimal> getPositionMarginByContractId(Long contractId) {
        Result<BigDecimal> result = new Result<>();
        result.setData(BigDecimal.ZERO);
        long totalPosition  = getTotalPositionByContractId(contractId);
        BigDecimal oneWayPosition = new BigDecimal(totalPosition/2);
        BigDecimal lever = new BigDecimal("10");
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        try {
            contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractId);
        }catch (Exception e){
            log.error("contractCategoryMapper.selectByPrimaryKey() failed {}{}", contractId,e);
            return result.error(-1,"getPositionMargin failed");
        }
        BigDecimal contractSize = contractCategoryDO.getContractSize();
        //获取买一卖一价
        BigDecimal askCurrentPrice = BigDecimal.ZERO;
        BigDecimal bidCurrentPrice = BigDecimal.ZERO;
        try{
            Object competiorsPriceObj = redisManager.get(Constant.CONTRACT_COMPETITOR_PRICE_KEY);
            List<CompetitorsPriceDTO> competitorsPriceList = JSON.parseArray(competiorsPriceObj.toString(), CompetitorsPriceDTO.class);
            List<CompetitorsPriceDTO> askCurrentPriceList = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.ASK.getCode() &&
                    Long.valueOf(competitorsPrice.getId()).equals(contractId)).limit(1).collect(toList());
            List<CompetitorsPriceDTO> bidCurrentPriceList = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.BID.getCode() &&
                    Long.valueOf(competitorsPrice.getId()).equals(contractId)).limit(1).collect(toList());

            if (askCurrentPriceList != null && askCurrentPriceList.size() != 0) {
                askCurrentPrice = askCurrentPriceList.get(0).getPrice();
            }
            if (bidCurrentPriceList != null && bidCurrentPriceList.size() != 0) {
                bidCurrentPrice = bidCurrentPriceList.get(0).getPrice();
            }
            log.info("askCurrentPriceList-----"+askCurrentPrice);
            log.info("bidCurrentPrice-----"+bidCurrentPrice);
        }catch (Exception e){
            log.error("get competiorsPrice failed {}{}", contractId,e);
            return result.error(-1,"getPositionMargin failed");
        }
        BigDecimal askPositionMargin = askCurrentPrice.multiply(oneWayPosition).multiply(contractSize).divide(lever).setScale(8,BigDecimal.ROUND_DOWN);
        BigDecimal bidPositionMargin = bidCurrentPrice.multiply(oneWayPosition).multiply(contractSize).divide(lever).setScale(8,BigDecimal.ROUND_DOWN);
        BigDecimal totalPositionMargin = askPositionMargin.add(bidPositionMargin);
        result.success(totalPositionMargin);
        return result;
    }
}
