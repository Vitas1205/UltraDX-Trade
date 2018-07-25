package com.fota;

import com.fota.trade.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ImportResource;

import javax.annotation.PostConstruct;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
@EnableAutoConfiguration
@RefreshScope
@Slf4j
@ImportResource("classpath:application-context.xml")
public class FotaTradeApplication {

	@Autowired
	Consumer consumer;

	@PostConstruct
	public void runServer() {
		ThreadFactory nameFactory = new FotaThreadFactory();
		ThreadPoolExecutor singleThreadPool =
				new ThreadPoolExecutor(1,
						1, 0L,
						TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(16), nameFactory);
//		singleThreadPool.execute(new Thread(thriftServer));
	}


	public static void main(String[] args) {
		SpringApplication.run(FotaTradeApplication.class, args);
	}

	@PostConstruct
	public void  runConsumer(){
		try {
			consumer.init();
		} catch (Exception e) {
			log.error("runConsumer failed",e);
		}
	}


	static class FotaThreadFactory implements ThreadFactory {
		private static AtomicLong id = new AtomicLong(0);
		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, "trade-thread-pool-" + id.addAndGet(1));
		}
	}

}
