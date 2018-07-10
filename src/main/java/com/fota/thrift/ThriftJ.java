package com.fota.thrift;

import com.cyfonly.thriftj.ThriftClient;
import com.cyfonly.thriftj.constants.Constant;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Gavin Shen
 * @Date 2018/7/9
 */
@RefreshScope
@Component
public class ThriftJ {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThriftJ.class);

    @Autowired
    private EurekaClient eurekaClient;


    private Map<String, ThriftClient> thriftClients;

    @PostConstruct
    public void init() {
        try {
            thriftClients = new HashMap<>(4);
        } catch (Exception e) {
            LOGGER.error("init server error", e);
        }
    }

    public synchronized boolean initService(String appName, int port) {
        try {
            if (!thriftClients.containsKey(appName)) {
                String server = getServers(appName, port);
                ThriftClient thriftClient = new ThriftClient();
                thriftClient.servers(server)
                        .loadBalance(Constant.LoadBalance.RANDOM)
                        .protocalType(Constant.ProtocalType.TYPE_MULTI_PROTOCAL)
                        .connTimeout(3000)
                        .start();
                thriftClients.put(appName, thriftClient);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("initService error " + appName, e);
        }
        return false;
    }

    public ThriftClient getServiceClient(String appName) {
        ThriftClient client = thriftClients.get(appName);
        return client;
    }


    public String getServers(String appName, int port) throws Exception {
        EurekaClientConfig config1 = eurekaClient.getEurekaClientConfig();
        String [] zones = config1.getAvailabilityZones(config1.getRegion());
        List<String> zoneLists = Arrays.asList(zones);
        Applications apps = eurekaClient.getApplications();
        Application app = apps.getRegisteredApplications(appName);
        List<InstanceInfo> infos = app.getInstances();
        String ip = null;
        StringBuilder server = new StringBuilder();
        for(InstanceInfo info : infos) {
            if (zoneLists.isEmpty()) {
                if (!info.getMetadata().containsKey("zone")) {
                    ip = info.getIPAddr() + ":" + port;
                    server.append(ip).append(",");
                }
            } else if(info.getMetadata().containsKey("zone")) {
                if (zoneLists.contains(info.getMetadata().get("zone"))) {
                    ip = info.getIPAddr() + ":" + port;
                    server.append(ip).append(",");
                }
            }
        }
        if (StringUtils.isBlank(ip) ) {
            if (infos.isEmpty()) {
                throw new Exception("fota asset server is empty");
            }
            ip = infos.get(0).getIPAddr() + ":" + port;
            server.append(ip);
        }
        return server.toString();
    }
}