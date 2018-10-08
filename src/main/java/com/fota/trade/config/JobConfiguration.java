package com.fota.trade.config;

import com.dangdang.ddframe.job.config.JobCoreConfiguration;
import com.dangdang.ddframe.job.config.simple.SimpleJobConfiguration;
import com.dangdang.ddframe.job.lite.api.JobScheduler;
import com.dangdang.ddframe.job.lite.config.LiteJobConfiguration;
import com.dangdang.ddframe.job.reg.base.CoordinatorRegistryCenter;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperConfiguration;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperRegistryCenter;
import com.fota.trade.client.SimpleDemoJob;
import com.fota.trade.domain.ContractCategoryDTO;
import com.fota.trade.service.ContractCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

/**
 * Created by Swifree on 2018/9/22.
 * Code is the law
 */
@Configuration
public class JobConfiguration {

    JobScheduler postDealJob = null;

     @Value("${spring.cloud.zookeeper.connect-string}")
     private String registryServerLists;

     @Autowired
     private ContractCategoryService contractCategoryService;

    @PostConstruct
    public void init() {
        postDealJob = new JobScheduler(createRegistryCenter(), createJobConfiguration());
        postDealJob.init();
    }

    @Bean
    public CoordinatorRegistryCenter createRegistryCenter() {
        CoordinatorRegistryCenter regCenter = new ZookeeperRegistryCenter(new ZookeeperConfiguration(registryServerLists, "elastic-job-demo"));
        regCenter.init();
        return regCenter;
    }

    @Bean
    public LiteJobConfiguration createJobConfiguration() {
        // 定义作业核心配置
        JobCoreConfiguration simpleCoreConfig = JobCoreConfiguration.newBuilder("trade-demoSimpleJob", "0/15 * * * * ?", 1).
//                shardingItemParameters("0=a")
                build();
        // 定义SIMPLE类型配置
        SimpleJobConfiguration simpleJobConfig = new SimpleJobConfiguration(simpleCoreConfig, SimpleDemoJob.class.getCanonicalName());
        // 定义Lite作业根配置
        LiteJobConfiguration simpleJobRootConfig = LiteJobConfiguration.newBuilder(simpleJobConfig).
                build();
        return simpleJobRootConfig;
    }
    @PreDestroy
    public void destory(){
        postDealJob.getSchedulerFacade().shutdownInstance();
    }

}
