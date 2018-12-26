package com.fota.trade.util;

import java.util.Objects;

/**
 * Created by Swifree on 2018/9/6.
 * Code is the law
 */
public class ThreadContextUtil {

    private static ThreadLocal<Profiler> profiler = new ThreadLocal<>();

    private static ThreadLocal<Runnable> postTask = new ThreadLocal<>();

    public static Profiler getProfiler() {
        return profiler.get();
    }

    public static Profiler getProfiler(String method) {
        Profiler profiler = getProfiler();
        if (Objects.isNull(profiler)) {
            profiler = new Profiler(method);
            setProfiler(profiler);
        }

        return profiler;
    }

    public static void setProfiler(Profiler profiler) {
        ThreadContextUtil.profiler.set(profiler);
    }

    public static Runnable getPostTask() {
        return postTask.get();
    }

    public static void setPostTask(Runnable postTask) {
        ThreadContextUtil.postTask.set(postTask);
    }

    public static void clear(){
        profiler.remove();
        postTask.remove();
    }
}
