package com.fota.trade.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * @author Gavin Shen
 * @Date 2018/7/5
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(@Qualifier("dataSource")DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public PlatformTransactionManager assetTransactionManager(@Qualifier("asset") DataSource assetDataSource) {
        return new DataSourceTransactionManager(assetDataSource);
    }
}
