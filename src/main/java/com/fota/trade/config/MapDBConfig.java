//package com.fota.trade.config;
//
//import org.mapdb.DB;
//import org.mapdb.DBMaker;
//import org.mapdb.Serializer;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;
//
///**
// * Created by Swifree on 2018/9/21.
// * Code is the law
// */
//@Configuration
//public class MapDBConfig {
//
//
//    @Bean
//    public DB mapDB(){
//        DB db = DBMaker.fileDB("./mapdb")
//                //.checksumHeaderBypass()
//                .fileMmapEnableIfSupported()//使用mmap
//                .fileMmapPreclearDisable()//对mmap的优化
//                .cleanerHackEnable()// jvm mmap bug处理
//                .closeOnJvmShutdown()
//                .transactionEnable()//开启事务
//                .concurrencyScale(128)
//                .make();
//        return db;
//    }
//    @Bean
//    public ConcurrentMap<String, String> failedMQMap(@Autowired DB db) {
//
//        ConcurrentMap<String, String> map = db.treeMap("failedMQ")
//                .keySerializer(Serializer.STRING)
//                .valueSerializer(Serializer.STRING)
//                .valuesOutsideNodesEnable()
//                .createOrOpen();
//        return map;
//    }
//    @Bean
//    public ConcurrentMap<String, String> failedBalanceMap(@Autowired DB db) {
//        ConcurrentMap<String, String> map = db.treeMap("failedBalance")
//                .keySerializer(Serializer.STRING)
//                .valueSerializer(Serializer.STRING)
//                .valuesOutsideNodesEnable()
//                .createOrOpen();
//        return map;
//    }
//}
//
