package com.recycling.toolsapp.utils

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    const val FORMAT1 = "yyyy-MM-dd HH:mm:ss"
    const val FORMAT2 = "yyyy-MM-dd"
    const val FORMAT3 = "HH:mm:ss"
    const val FORMAT4 = "HH:mm"
    const val FORMAT5 = "mm:ss"
    const val FORMAT6 = "yyyy/MM/dd"


    fun formatDate(time: String, format: String = FORMAT1): String {
        if (time.isEmpty()) return ""
        val date = SimpleDateFormat(format).parse(time)
        val result = SimpleDateFormat(format).format(date)
        return result
    }

    fun formatDate2(time: String, format: String = FORMAT1): String {
        if (time.isEmpty()) return ""
        val date = SimpleDateFormat(FORMAT1).parse(time)
        val result = SimpleDateFormat(format).format(date)
        return result
    }


    /**
     * 时间戳转为时间
     * @param stamp 时间戳
     * @param format 返回值的时间格式
     */
    fun stampToDate(stamp: Long, format: String = FORMAT1): String {
        val date = Date(stamp - TimeZone.getDefault().rawOffset)
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        return sdf.format(date)
    }

    /**
     * 时间转为时间戳
     * @param start 开始时间
     * @param format start 的时间格式
     */
    fun dateToStamp(date: String, format: String = FORMAT1): Long {
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        var stamp: Long = 0
        sdf.parse(date)?.let {
            stamp = it.time
        }
        return stamp
    }

    /**
     * 计算时间差
     * @param start 开始时间
     * @param end 结束时间
     * @param format start、end 的时间格式
     * @return 毫秒
     */
    fun diff(start: String, end: String, format: String = FORMAT1): Long {
        val s = dateToStamp(start, format)
        val e = dateToStamp(end, format)
        return e - s
    }
}