package com.fota.fotatrade;

import com.fota.trade.service.ContractOrderService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class FotaTradeApplicationTests {

	@Autowired
	private ContractOrderService contractOrderService;

	@Test
	public void contextLoads() {
//		Assert.assertNotNull(contractOrderService.listNotMatchOrder(BigInteger.ONE));
	}

}
