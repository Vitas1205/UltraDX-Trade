package com.fota.trade.mapper;

import com.fota.trade.common.BeanUtils;
import com.fota.trade.domain.UsdkMatchedOrderDO;
import com.fota.trade.domain.UsdkMatchedOrderDTO;
import com.fota.trade.domain.UsdkMatchedOrderTradeDTOPage;
import com.fota.trade.service.UsdkOrderService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.fota.trade.common.TestConfig.assetId;
import static com.fota.trade.common.TestConfig.userId;
import static com.fota.trade.domain.enums.OrderDirectionEnum.ASK;
import static com.fota.trade.domain.enums.OrderDirectionEnum.BID;

/**
 * @Author: Carl Zhang
 * @Descripyion:
 * @Date: Create in 下午 21:27 2018/8/23 0023
 * @Modified:
 */
//@RunWith(SpringRunner.class)
//@SpringBootTest
@Slf4j
@Transactional
public class UsdkMatchedOrderMapperTest {
    @Resource
    private UsdkMatchedOrderMapper usdkMatchedOrderMapper;
    @Resource
    private UsdkOrderService usdkOrderService;

    @Before
    public void init(){
        List<UsdkMatchedOrderDO> list = new ArrayList<>();
        UsdkMatchedOrderDTO usdkMatchedOrderDTO = new UsdkMatchedOrderDTO();
        usdkMatchedOrderDTO.setId(1L);
        usdkMatchedOrderDTO.setAskUserId(userId);
        usdkMatchedOrderDTO.setBidUserId(0L);
        usdkMatchedOrderDTO.setAskOrderId(2L);
        usdkMatchedOrderDTO.setBidOrderId(8L);
        usdkMatchedOrderDTO.setFilledAmount("1.2");
        usdkMatchedOrderDTO.setFilledPrice("1.1");
        usdkMatchedOrderDTO.setAssetId(assetId.intValue());
        usdkMatchedOrderDTO.setAskOrderPrice("1.0");
        usdkMatchedOrderDTO.setBidOrderPrice("1.1");
        usdkMatchedOrderDTO.setMatchType(1);
        usdkMatchedOrderDTO.setAssetName("test");
        UsdkMatchedOrderDO ask = BeanUtils.extractUsdtRecord(usdkMatchedOrderDTO, ASK.getCode());
        UsdkMatchedOrderDO bid = BeanUtils.extractUsdtRecord(usdkMatchedOrderDTO, BID.getCode());
        int aff = usdkMatchedOrderMapper.insert(Arrays.asList(ask, bid));
        assert 2 == aff;
    }
//    @Test
    public void testQuery(){
        UsdkMatchedOrderTradeDTOPage usdkMatchedOrderTradeDTOPage = usdkOrderService.getUsdkMatchRecord(userId, Arrays.asList(assetId), 1, 1, 0L, System.currentTimeMillis());
        assert usdkMatchedOrderTradeDTOPage.getData().size() == 1;
        log.info("res={}", usdkMatchedOrderTradeDTOPage);
    }
}
