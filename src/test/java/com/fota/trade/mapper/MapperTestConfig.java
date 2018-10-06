package com.fota.trade.mapper;

import com.fota.trade.config.DataSourceConfig;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Created by lds on 2018/10/5.
 * Code is the law
 */
@Configuration
@ComponentScan(basePackages = {"com.fota.trade.mapper"})
@EnableAutoConfiguration
@Import(DataSourceConfig.class)
public class MapperTestConfig {
}
