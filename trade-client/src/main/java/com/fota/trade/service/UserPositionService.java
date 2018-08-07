package com.fota.trade.service;


import com.fota.common.Page;
import com.fota.trade.domain.ResultCode;
import com.fota.trade.domain.UserPositionDTO;

import java.util.List;

public interface UserPositionService {

    public Page<UserPositionDTO> listPositionByQuery(long userId, long contractId, int pageNo, int pageSize);

    public long getTotalPositionByContractId(long contractId);

    ResultCode deliveryPosition(long id);

    List<UserPositionDTO> listPositionByUserId(long userId);

}