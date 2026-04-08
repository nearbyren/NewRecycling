package com.serial.port.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * @description: App工具类
 * @since: 1.0.0
 */
@SuppressLint("StaticFieldLeak") object AppUtils {

    private val initLock = Any()
    private var isDebug: Boolean = true // 应用是否属于调试模式
    private var context: Context? = null
    private var application: Application? = null

    //0.api 1.pre 2.grey 3.dev 4.office
    var THE_ENVIRONMENT = 0
    const val APP_HOME_NUMBER: Int = 1000
    const val APP_INDEX_NUMBER: Int = 1001
    const val APP_ORDER_NUMBER: Int = 1002
    const val APP_ME_NUMBER: Int = 1003
    const val APP_STATION_NUMBER: Int = 1004
    const val APP_COUPON_NUMBER: Int = 1005
    const val APP_PAY_NUMBER: Int = 1006
    const val APP_WEB_NUMBER: Int = 1007
    const val APP_LOGIN_NUMBER: Int = 1008
    const val APP_ADVERTISE_NUMBER: Int = 1009

    @JvmStatic fun init(application: Application) {
        synchronized(initLock) {
            context = application.applicationContext
            AppUtils.application = application
        }
    }

    @JvmStatic fun setDebug(isDebug: Boolean) {
        AppUtils.isDebug = isDebug
    }

    @JvmStatic fun isDebug(): Boolean = isDebug

    @JvmStatic fun getContext(): Context {
        if (context == null) {
            context = getApplication().applicationContext
        }
        return context!!
    }

    @JvmStatic fun getApplication(): Application {
        if (application == null) {
            synchronized(initLock) {
                if (application == null) {
                    application = getCurrentApplicationByReflect()
                }
            }
        }
        return application!!
    }

    /**
     * 判断Activity是否销毁
     *
     * @param activity
     * @return true or false
     */
    @JvmStatic fun isActivityDestroy(activity: Activity?): Boolean {
        if (activity == null) {
            return true
        }
        return activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)
    }

    /**
     * 通过反射获取当前应用application实例
     */
    private fun getCurrentApplicationByReflect(): Application? {
        try {
            var application =
                    Class.forName("android.app.ActivityThread")?.getDeclaredMethod("currentApplication")?.invoke(null) as? Application
            if (application != null) {
                return application
            }

            application =
                    Class.forName("android.app.AppGlobals")?.getDeclaredMethod("getInitialApplication")?.invoke(null) as? Application
            if (application != null) {
                return application
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 获取VersionCode
     */
    @JvmStatic fun getVersionCode(): Long {
        var result = 0L
        val context = getContext()
        try {
            val packageInfo = getContext().packageManager.getPackageInfo(context.packageName, 0)
            result = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                packageInfo.versionCode.toLong()
            } else {
                packageInfo.longVersionCode
            }
        } catch (throwable: Throwable) {
        }
        return result
    }

    /**
     * 获取VersionName
     */
    @JvmStatic fun getVersionName(): String {
        var result = ""
        val context = getContext()
        try {
            result = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (throwable: Throwable) {
        }
        return result
    }

    /**
     * 检查App是否安装
     */
    fun checkAppInstalled(context: Context, packageName: String?): Boolean {
        val packageManager = context.packageManager
        var applicationInfo: ApplicationInfo? = null
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName!!, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } catch (ep: Exception) {
            ep.printStackTrace()
        }
        return applicationInfo != null
    }

    /**
     * 获取进程号对应的进程名
     *
     * @param pid 进程号
     * @return 进程名
     */
    public fun getProcessName(pid: Int): String? {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(FileReader("/proc/$pid/cmdline"))
            var processName = reader.readLine()
            if (!TextUtils.isEmpty(processName)) {
                processName = processName.trim { it <= ' ' }
            }
            return processName
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        } finally {
            try {
                reader?.close()
            } catch (exception: IOException) {
                exception.printStackTrace()
            }
        }
        return null
    }

    /***
     * 获取年月日时间
     */
    fun getDateYMD(): String {
        val now = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val formattedNow = formatter.format(now)
        return formattedNow
    }

    /***
     * 获取年月日时分秒
     */
    fun getDateYMDHMS(): String {
        val now = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val formattedNow = formatter.format(now)
        return formattedNow
    }
    /***
     * 获取年月日时分秒
     */
    fun getDateYMDHMS3(): String {
        val now = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
        val formattedNow = formatter.format(now)
        return formattedNow
    }
    /***
     * 获取年月日时分秒
     */
    fun getDateYMDHMS2(): String {
        val now = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd-HH:mm:ss")
        val formattedNow = formatter.format(now)
        return formattedNow
    }

    /***
     * 获取月日时分秒
     */
    fun getDateMDHMS(): String {
        val now = Calendar.getInstance().time
        val formatter = SimpleDateFormat("MM-dd HH:mm:ss")
        val formattedNow = formatter.format(now)
        return formattedNow
    }

    /***
     * 获取时分秒
     */
    fun getDateHMS(): String {
        val now = Calendar.getInstance().time
        val formatter = SimpleDateFormat("HH:mm:ss")
        val formattedNow = formatter.format(now)
        return formattedNow
    }

    /***
     * 获取时分秒
     */
    fun getDateHMS2(): String {
        val now = Calendar.getInstance().time
        val formatter = SimpleDateFormat("HH-mm-ss")
        val formattedNow = formatter.format(now)
        return formattedNow
    }

    /***
     * 获取时分秒
     */
    fun getDateHM(): String {
        val now = Calendar.getInstance().time
        val formatter = SimpleDateFormat("HH:mm")
        val formattedNow = formatter.format(now)
        return formattedNow
    }

    /***
     * 获取UUID
     */
    fun getUUID(): String {
        // 生成UUID
        val uuid = UUID.randomUUID().toString()

        // 获取当前日期和时间
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss")
        val currentDateTime = dateFormat.format(Date())

        // 生成1到10000之间的随机数
        val randomNumber = Random.nextInt(1, 10001)

        // 组合UUID、日期时间和随机数
        val uniqueId = "$uuid-$currentDateTime-$randomNumber"
        Loge.d("测试我来了 Generated Unique ID: $uniqueId")
        return uniqueId
    }

    fun getUUIDYMD(userId: String): String {
        // 获取当前日期和时间
        val dateFormat = SimpleDateFormat("yyyyMMdd")
        val currentDateTime = dateFormat.format(Date())
        val uniqueId = "$userId-$currentDateTime"
        Loge.d("测试我来了 Generated Unique ID: $uniqueId")
        return uniqueId
    }

    /***
     * 获取工单
     */
    fun getWork(): String {

        // 获取当前日期和时间
        val dateFormat = SimpleDateFormat("HHmmss")
        val currentDateTime = dateFormat.format(Date())

        // 生成1到10000之间的随机数
        val randomNumber = Random.nextInt(1, 10001)

        // 组合UUID、日期时间和随机数
        val uniqueId = "$currentDateTime$randomNumber"
        Loge.d("测试我来了 Generated Unique ID: $uniqueId")
        return uniqueId
    }

    /***
     * quTime :取箱子的时间
     * timeLimitInDays :超时天数
     */
    fun matchDays(quTime: String, timeLimitInDays: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date1 = format.parse(quTime)
        val date2 = Date() // 当前时间

        // 计算时间差（毫秒）
        val diffInMillis = date2.time - date1.time

        // 解析 quTime 的超时天数
        val timeLimitInMillis = TimeUnit.DAYS.toMillis(timeLimitInDays)

        return if (diffInMillis > timeLimitInMillis) {
            // 计算超时天数、小时和分钟
            val excessMillis = diffInMillis - timeLimitInMillis
            val excessDays = TimeUnit.MILLISECONDS.toDays(excessMillis)
            val excessHours = TimeUnit.MILLISECONDS.toHours(excessMillis) % 24
            val excessMinutes = TimeUnit.MILLISECONDS.toMinutes(excessMillis) % 60
            "$excessDays 天, $excessHours 小时, $excessMinutes 分"
        } else {
            Loge.d("未超时")
            ""
        }
    }


    fun getFullName(): String {
        // 常见的中文姓氏列表
        val surnames =
                listOf("李", "王", "张", "刘", "陈", "杨", "黄", "吴", "赵", "周", "徐", "孙", "马", "朱", "胡", "林", "郭", "何", "高", "罗")
        // 随机选择一个姓氏
        val randomSurname = surnames[Random.nextInt(surnames.size)]
        // 生成完整的姓名
        return "${randomSurname}工"
    }


}