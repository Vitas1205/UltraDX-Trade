package com.fota.trade.util;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.net.URL;
import java.net.URLConnection;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * @Author huangtao 2018/7/12 下午4:04
 * @Description 时间工具
 */
public class DateUtil {

    public static final String webUrl1 = "http://www.baidu.com";

    /**
     * 获取 网络 时间
     * @return
     */
    public static Date getWebsiteDatetime() {
        try {
            URL url = new URL("http://www.baidu.com");// 取得资源对象
            URLConnection uc = url.openConnection();// 生成连接对象
            uc.connect();// 发出连接
            long ld = uc.getDate();// 读取网站日期时间
            Date date = new Date(ld);// 转换为标准时间对象
            return date;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Date();
    }

    /**
     * 日期加上天数
     * @param num
     * @param currdate
     * @return
     * @throws ParseException
     */
    public static Long plusDay(int num, Long currdate) throws ParseException {
        Date currdateD = LongTurntoDate(currdate);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currdateD);
        calendar.add(Calendar.DATE, num);
        currdateD = calendar.getTime();
        return currdateD.getTime();
    }

    /**
     * long转date
     * @param str
     * @return
     * @throws ParseException
     */
    public static Date LongTurntoDate(long str){
        return new Date(str);
    }

    /**
     * 将时间的时分秒清零
     * @param dateLong
     * @return
     */
    public static Long cleanTime(Long dateLong){
        Date date = new Date(dateLong);
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date);
        // 将时分秒,毫秒域清零
        cal1.set(Calendar.HOUR_OF_DAY, 0);
        cal1.set(Calendar.MINUTE, 0);
        cal1.set(Calendar.SECOND, 0);
        cal1.set(Calendar.MILLISECOND, 0);
        return cal1.getTime().getTime();
    }

    /**
     * 获取查询的开始时间，
     * 如果传null，则返回当前时间的前一天
     * 如果传0，返回0
     * 如果不等于0，则返回当天时间的00：00：00
     * @param startTime
     * @return
     */
    public static Long startTime(Long startTime){
        if (null == startTime){
            try{
                startTime = DateUtil.plusDay(-1, System.currentTimeMillis())/1000;
            }catch (Exception e){
                e.printStackTrace();
            }
        }else if (startTime != 0){
            startTime = DateUtil.cleanTime(startTime)/1000;
        }
        return startTime;
    }

    /**
     * 获取查询的结束时间
     * 如果传null，则返回当前时间
     * 如果传0，返回0
     * 如果不等于0，则返回后一天的00：00：00
     * @param endTime
     * @return
     */
    public static Long endTime(Long endTime) {
        if (null == endTime || 0 == endTime) {
            endTime = System.currentTimeMillis() / 1000;
        }else if (endTime != 0){
            // 清零
            endTime = DateUtil.cleanTime(endTime);
            // +1
            try {
                endTime = DateUtil.plusDay(1, endTime) / 1000;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return endTime;
    }

}
