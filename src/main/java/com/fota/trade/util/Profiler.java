package com.fota.trade.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Swifree on 2018/8/30.
 * Code is the law
 * 性能分析使用
 */
@Slf4j
public class Profiler {

    private static String format = "profile of %s, traceId=%s: { total:%s, detail:%s }";

    private long start;
    private String method;
    private String traceId;
    private List<ProfilePhase> profilePhases = new ArrayList<>();

    public Profiler(String method) {
        this.method = method;
        start();
    }

    public Profiler(String method, String traceId) {
        this.method = method;
        this.traceId = traceId;
        start();
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    private void start() {
        start = System.currentTimeMillis();
    }

    public void complelete(String phase) {
        profilePhases.add(new ProfilePhase(phase, System.currentTimeMillis()));
    }

    @Data
    public static class ProfilePhase {
        private String name;
        private long completeTime;

        public ProfilePhase(String name, long completeTime) {
            this.name = name;
            this.completeTime = completeTime;
        }
    }

    @Override
    public String toString() {
        if (CollectionUtils.isEmpty(profilePhases)) {
            return null;
        }
        int size = profilePhases.size();
        long total = profilePhases.get(size - 1).getCompleteTime() - start;
        StringBuilder detail = new StringBuilder(" { ");


        ProfilePhase cur = profilePhases.get(0);
        detail.append(cur.name)
                .append(":")
                .append(cur.getCompleteTime() - start);
        ProfilePhase pre = cur;
        for (int i=1;i<size;i++) {
            cur = profilePhases.get(i);
            detail.append(",").append(cur.name)
                    .append(":")
                    .append(cur.getCompleteTime() - pre.getCompleteTime());
            pre = cur;
        }
        detail.append(" } ");
        return String.format(format, method, traceId, total, detail);
    }

    public void log() {
        log.info(this.toString());
    }
}
