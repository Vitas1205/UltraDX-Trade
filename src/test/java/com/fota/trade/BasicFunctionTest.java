package com.fota.trade;

import com.fota.trade.domain.ContractOrderDO;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Swifree on 2018/9/10.
 * Code is the law
 */
public class BasicFunctionTest {
    @Test
    public void testSort(){

        ContractOrderDO contractOrderDO = new ContractOrderDO();
        contractOrderDO.setUserId(314L);
        contractOrderDO.setId(1L);

        ContractOrderDO  contractOrderDO1= new ContractOrderDO();
        contractOrderDO1.setUserId(314L);
        contractOrderDO1.setId(2L);
        List<ContractOrderDO> contractOrderDOS = new ArrayList<>();
        contractOrderDOS.add(contractOrderDO);
        contractOrderDOS.add(contractOrderDO1);

        Collections.sort(contractOrderDOS, (a, b) -> {
            int c = a.getUserId().compareTo(b.getUserId());
            return c;
//            if (c!=0) {
//                return c;
//            }
//            return a.getId().compareTo(b.getId());
        });
        //获取锁
        List<String> locks = contractOrderDOS.stream()
        .map(x -> "POSITION_LOCK_"+ x.getUserId()+"_"+ x.getContractId())
                .distinct()
                .collect(Collectors.toList());
        System.out.println(locks);
    }
//    @Test
//    public void testRedisson(){
//        // 1. Create config object
//        Config config  = new Config();
//
//// 2. Create Redisson instance
//        RedissonClient redisson = Redisson.create(config);
//
//
//        RLock lock = redisson.getLock("myLock");
//
//    }
}
