package com.fota.client.service;

import com.fota.client.common.Page;
import com.fota.client.domain.UserPositionDTO;
import com.fota.client.domain.query.UserPositionQuery;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
public interface UserPositionService {

    /**
     * 查询用户持仓
     * @param userPositionQuery
     * @return
     */
    Page<UserPositionDTO> listPositionByQuery(UserPositionQuery userPositionQuery);

}
