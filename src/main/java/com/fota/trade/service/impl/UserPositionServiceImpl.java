package com.fota.trade.service.impl;

import com.fota.common.Page;
import com.fota.common.Result;
import com.fota.risk.client.domain.UserPositionQuantileDTO;
import com.fota.risk.client.manager.RelativeRiskLevelManager;
import com.fota.ticker.entrust.RealTimeEntrust;
import com.fota.ticker.entrust.entity.CompetitorsPriceDTO;
import com.fota.trade.client.constants.MatchedOrderStatus;
import com.fota.trade.common.BeanUtils;
import com.fota.trade.common.Constant;
import com.fota.trade.common.ParamUtil;
import com.fota.trade.common.ResultCodeEnum;
import com.fota.trade.domain.*;
import com.fota.trade.domain.enums.OrderCloseType;
import com.fota.trade.domain.enums.OrderDirectionEnum;
import com.fota.trade.domain.enums.PositionStatusEnum;
import com.fota.trade.domain.query.UserPositionQuery;
import com.fota.trade.manager.ContractOrderManager;
import com.fota.trade.manager.RedisManager;
import com.fota.trade.mapper.sharding.ContractMatchedOrderMapper;
import com.fota.trade.mapper.trade.ContractCategoryMapper;
import com.fota.trade.mapper.trade.UserPositionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.fota.trade.client.constants.Constants.NOT_EXIST;
import static com.fota.trade.common.ResultCodeEnum.NO_LATEST_MATCHED_PRICE;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;
import static com.fota.trade.domain.enums.PositionStatusEnum.DELIVERED;

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

    @Autowired
    private RealTimeEntrust realTimeEntrust;

    @Autowired
    private ContractOrderManager contractOrderManager;

    @Autowired
    private RelativeRiskLevelManager relativeRiskLevelManager;

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
        List<CompetitorsPriceDTO> contractCompetitorsPrice = realTimeEntrust.getContractCompetitorsPriceOrder();
        try {
            userPositionDOList = userPositionMapper.listByQuery(ParamUtil.objectToMap(userPositionQuery));
            if (!CollectionUtils.isEmpty(userPositionDOList)) {
                UserPositionQuantileDTO userPositionQuantileDTO = new UserPositionQuantileDTO();
                List<UserPositionQuantileDTO.UserPositionDTO> positionDTOList = userPositionDOList.stream()
                        .map(userPositionDO -> {
                            UserPositionQuantileDTO.UserPositionDTO userPositionDTO = new UserPositionQuantileDTO.UserPositionDTO();
                            userPositionDTO.setPositionType(userPositionDO.getPositionType());
                            userPositionDTO.setContractId(userPositionDO.getContractId());

                            return userPositionDTO;
                        }).collect(Collectors.toList());
                userPositionQuantileDTO.setUserPositions(positionDTOList);
                userPositionQuantileDTO.setUserId(userId);
                Map<Long, Long> quantiles = relativeRiskLevelManager.quantiles(userPositionQuantileDTO);
                for (UserPositionDO tmp : userPositionDOList) {
                    UserPositionDTO userPositionDTO = BeanUtils.copy(tmp);
                    Long quantile = quantiles.get(userPositionDTO.getContractId());
                    if (Objects.isNull(quantile)) {
                        quantile = Constant.DEFAULT_POSITION_QUANTILE;
                        log.warn("user:{} contract:{}/{} quantile miss", userId, userPositionDTO.getContractId(),
                                tmp.getPositionType());
                    }
                    userPositionDTO.setQuantile(quantile);
                    BigDecimal computePrice = contractOrderManager.computePrice(contractCompetitorsPrice, tmp.getPositionType(), tmp.getContractId());
                    userPositionDTO.setCurrentPrice(computePrice.multiply(tmp.getUnfilledAmount()));
                    list.add(userPositionDTO);
                }
            }
        } catch (Exception e) {
            log.error("userPositionMapper.listByQuery({})", userPositionQuery, e);
            return page;
        }
        page.setData(list);
        return page;
    }



    @Override
    public BigDecimal getTotalPositionByContractId(long contractId) {
        Object result = redisManager.get(Constant.CONTRACT_TOTAL_POSITION + contractId);
        if (result != null) {
            return new BigDecimal(result.toString());
        }
        BigDecimal totalPosition = userPositionMapper.countTotalPosition(contractId);
        if (totalPosition == null) {
            return BigDecimal.ZERO;
        }
        return totalPosition.multiply(BigDecimal.valueOf(2));
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
            // todo 交割时传买卖手续费
            record.setFilledPrice(deliveryCompletedDTO.getPrice());
            record.setFilledAmount(deliveryCompletedDTO.getAmount());
            record.setStatus(MatchedOrderStatus.VALID);
            record.setUserId(deliveryCompletedDTO.getUserId());
            record.setFee(deliveryCompletedDTO.getFee());
            //和持仓方向相反成交
            record.setOrderDirection(ASK.getCode() + BID.getCode() - deliveryCompletedDTO.getOrderDirection());
            record.setCloseType( OrderCloseType.DELIVERY.getCode());

            record.setOrderId(NOT_EXIST);
            record.setMatchId(NOT_EXIST);
            record.setMatchType((int)NOT_EXIST);
            record.setMatchUserId(NOT_EXIST);

            int insertRet = contractMatchedOrderMapper.insert(Arrays.asList(record));
            UserPositionDO userPositionDO = new UserPositionDO();
            userPositionDO.setId(deliveryCompletedDTO.getUserPositionId());
            userPositionDO.setStatus(DELIVERED.getCode());
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
        BigDecimal totalPosition  = getTotalPositionByContractId(contractId);
        if (totalPosition.stripTrailingZeros().compareTo(BigDecimal.ZERO) == 0){
            return  result.success(BigDecimal.ZERO);
        }
        BigDecimal oneWayPosition = totalPosition.divide(BigDecimal.valueOf(2));
        BigDecimal lever = new BigDecimal("10");
        ContractCategoryDO contractCategoryDO = new ContractCategoryDO();
        try {
            contractCategoryDO = contractCategoryMapper.selectByPrimaryKey(contractId);
        }catch (Exception e){
            log.error("contractCategoryMapper.selectByPrimaryKey() failed {}{}", contractId,e);
            return result.error(-1,"getPositionMargin failed");
        }
        //获取买一卖一价
        BigDecimal askCurrentPrice;
        BigDecimal bidCurrentPrice;
        try{
            List<CompetitorsPriceDTO> competitorsPriceList = realTimeEntrust.getContractCompetitorsPrice();
//            bidCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.BID.getCode() &&
//                    competitorsPrice.getId() == contractId.longValue()).findFirst().get().getPrice();
//            askCurrentPrice = competitorsPriceList.stream().filter(competitorsPrice -> competitorsPrice.getOrderDirection() == OrderDirectionEnum.ASK.getCode() &&
//                    competitorsPrice.getId() == contractId.longValue()).findFirst().get().getPrice();
            bidCurrentPrice = contractOrderManager.computePrice(competitorsPriceList, OrderDirectionEnum.BID.getCode(), contractId);
            if (null == bidCurrentPrice) {
                return result.error(NO_LATEST_MATCHED_PRICE.getCode(), NO_LATEST_MATCHED_PRICE.getMessage());
            }
            askCurrentPrice = contractOrderManager.computePrice(competitorsPriceList, OrderDirectionEnum.ASK.getCode(), contractId);
            if (null == askCurrentPrice) {
                return result.error(NO_LATEST_MATCHED_PRICE.getCode(), NO_LATEST_MATCHED_PRICE.getMessage());
            }
        }catch (Exception e){
            log.error("get competiorsPrice failed {}{}", contractId,e);
            return result.error(-1,"getPositionMargin failed");
        }
        BigDecimal askPositionMargin = askCurrentPrice.multiply(oneWayPosition)
                .divide(lever, 8, BigDecimal.ROUND_DOWN);
        BigDecimal bidPositionMargin = bidCurrentPrice.multiply(oneWayPosition)
                .divide(lever, 8,BigDecimal.ROUND_DOWN);
        BigDecimal totalPositionMargin = askPositionMargin.add(bidPositionMargin);
        result.success(totalPositionMargin);
        return result;
    }

    @Override
    public Page<UserPositionDTO> listPositionByUserIdAndContractId(Long userId, Long contractId, Integer pageNo, Integer pageSize) {
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
        page.setData(list);
        return page;
    }
}
