package com.fota.trade.service;


import com.fota.common.Page;
import com.fota.trade.domain.UserPositionDTO;

public interface UserPositionService {

    public Page<UserPositionDTO> listPositionByQuery(long userId, long contractId, int pageNo, int pageSize);

    public long getTotalPositionByContractId(long contractId);

}