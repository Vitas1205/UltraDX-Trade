package com.fota.trade.util;

/**
 * Created by Swifree on 2018/9/6.
 * Code is the law
 */
public class ThreadContextUtil {
    private static ThreadLocal<Profiler> prifiler = new ThreadLocal<>();
    private static ThreadLocal<Runnable> postTask = new ThreadLocal<>();

    public static Profiler getPrifiler() {
        return prifiler.get();
    }

    public static void setPrifiler(Profiler prifiler) {
        ThreadContextUtil.prifiler.set(prifiler);
    }

    public static Runnable getPostTask() {
        return postTask.get();
    }

    public static void setPostTask(Runnable postTask) {
        ThreadContextUtil.postTask.set(postTask);
    }
    public static void clear(){
        prifiler.remove();
        postTask.remove();
    }
}
