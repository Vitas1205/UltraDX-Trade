package com.fota.trade.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gavin Shen
 * @Date 2018/12/20
 */
public class MonitorLog {

    private static final Logger tradeLog = LoggerFactory.getLogger("trade");
    private static final Logger positionStatementInfoLog = LoggerFactory.getLogger("positionStatementInfo");


    public static void coinOrderInfo(String s) {
//        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}");
//        tradeLog.info("order@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}@@@{}",
//                TradeTypeEnum.CONTRACT.getCode(), contractOrderDO.getContractName(), username, contractOrderDO.getTotalAmount(),
//                System.currentTimeMillis(), OperationTypeEnum.ENFORCE_ORDER.getCode(), contractOrderDO.getOrderDirection(), contractOrderDO.getUserId(), 0);



    }

    public static void positionStatementInfo(String s) {

    }

}
