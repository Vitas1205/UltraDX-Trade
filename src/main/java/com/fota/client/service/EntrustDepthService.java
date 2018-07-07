package com.fota.client.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @Author: JianLi.Gao
 * @Descripyion:
 * @Date: Create in 下午2:38 2018/7/7
 * @Modified:
 */
public interface EntrustDepthService {

    Map<String, Map<BigDecimal, BigDecimal>> initContractEntrust(Integer orderDirection);

    Map<String, Map<BigDecimal, BigDecimal>> initUsdkEntrust(Integer orderDirection);

}
