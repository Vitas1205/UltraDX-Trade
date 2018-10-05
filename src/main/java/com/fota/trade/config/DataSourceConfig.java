package com.fota.trade.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
@Configuration
public class DataSourceConfig {
//
//    @Bean(name = "fota")
//    @ConfigurationProperties("spring.datasource.druid.fota")
//    public DataSource fota() {
//        return DataSourceBuilder.create().build();
//    }

    @Bean
    public PlatformTransactionManager transactionManager(@Autowired DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
