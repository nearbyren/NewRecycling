package com.recycling.toolsapp

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.hzmct.enjoysdkv2.api.EnjoySDKV2
import com.mc.android.mcinstall.AppRule
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy
import com.recycling.toolsapp.http.TaskDelDateScheduler
import com.recycling.toolsapp.http.TaskDelScheduler
import com.recycling.toolsapp.http.TaskLightsScheduler
import com.recycling.toolsapp.http.TaskRestartScheduler
import com.recycling.toolsapp.utils.CrashHandlerManager
import com.serial.port.t.SerialPortSdk
import com.serial.port.utils.AppUtils
import com.serial.port.utils.Loge
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nearby.lib.netwrok.response.CorHttp
import nearby.lib.netwrok.response.SPreUtil
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException


/**
 * global param init
heightPixels: 1232px
widthPixels: 800px
xdpi: 150.518dpi
ydpi: 150.518dpi
densityDpi: 160dpi
density: 1.0
scaledDensity: 1.0
heightDP: 1232.0dp
widthDP: 800.0dp
surfaceRotationDegrees: 0
dp: 55.555573
 *
 */
@HiltAndroidApp class FaceApplication : Application() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    /***
     * false 在后台
     * true 不在后台
     */
    val isAppForeground = MutableLiveData<Boolean>()
    var baseActivity: AppCompatActivity? = null

    companion object {
        var BASE_URL = BuildConfig.httpIP
        lateinit var enjoySDK: EnjoySDKV2
        private lateinit var instance: FaceApplication
        lateinit var RESOURCE_DIR: String  //资源下载路径
        lateinit var AUDIO_DIR: String  //音频资源下载路径
        lateinit var DEFAULT_DIR: String  //默认下载路径
        lateinit var APK_DIR: String  //apk升级
        lateinit var BIN_DIR: String  //固件升级
        fun getInstance() = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initLog()
        AppUtils.init(this)
        initEnjoySDK()
        createDir()
        initSerialPort()
        // activity栈管理
        registerActivityLifecycleCallbacks(mActivityLifecycleCallbacks)
        initCrash()
        initTimingTask()
        initHttp()
    }

    /****
     * 异常崩溃重启
     */
    private fun initCrash() {
        SPreUtil.put(AppUtils.getContext(), SPreUtil.crash, 1)
        CrashHandlerManager(this).init()
    }

    /****
     * 日志输出
     */
    private fun initLog() {
        val loggerBuild =
                PrettyFormatStrategy.newBuilder().showThreadInfo(true).tag("cabinet").build()
        Logger.addLogAdapter(object : AndroidLogAdapter(loggerBuild) {
            override fun isLoggable(priority: Int, @Nullable tag: String?): Boolean {
                //debug 才显示日志
                return BuildConfig.DEBUG
            }
        })
    }

    /****
     * 定时刷新用户信息
     */
    private fun initTimingTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ioScope.launch {
                delay(500)
                TaskLightsScheduler.cancelAllTasks(AppUtils.getContext())
//                TaskRestartScheduler.scheduleDaily(this@FaceApplication, "06:30","restart")
//                TaskRestartScheduler.scheduleTimeRange(this@FaceApplication,"03:00","03:10" ,"restart")
                TaskDelScheduler.scheduleDaily(this@FaceApplication, "21:30", "del")
                TaskDelDateScheduler.scheduleDaily(this@FaceApplication, "21:35", "delDate")
            }
        }
    }

    /***
     * 网络请求
     */
    private fun initHttp() {
         CorHttp.getInstance().init(this, true, baseIp = BASE_URL, codes = arrayListOf(401), tData = mapOf("code" to "code", "message" to "msg", "data" to "data"),)

    }

    /***
     * 迈冲sdk 状态栏 导航栏
     */
    private fun initEnjoySDK() {
        //初始化迈冲sdk 控制状态栏导航栏显示
        enjoySDK = EnjoySDKV2(this)
        enjoySDK.setSecurePasswd("Abc12345", "Abc12345")
        enjoySDK.registSafeProgram("Abc12345")
        //控制系统状态栏的隐藏/显示
        enjoySDK.setStatusBarShowStatus(0)
        //控制系统导航栏的隐藏/显示。
        enjoySDK.setNavigationBarShowStatus(0)
        //设置静默安装状态
        enjoySDK.silentInstallRulesSwitch(true)
        val appRule = AppRule("com.recycling.toolsapp", true)
//        val startPackName = enjoySDK.homePackage
//        enjoySDK.setHomePackage("com.android.launcher3")
//        enjoySDK.setHomePackage("com.recycling.toolsapp")
        //添加APP至 静默安装列表中
        enjoySDK.addSilentInstallRules(appRule)
        //获取 静默安装开关状态
        val isRules = enjoySDK.isEnableSilentInstallRules
        //获取静默安装列表
        val startRules = enjoySDK.silentInstallList
        //获取 是否打开了 安装后启动功能
        val autoStartRules = enjoySDK.isEnableInstallAutoStartRules
        //设置 安装后启动 开关状态
        enjoySDK.installAutoStartSwitch(true)
        //添加应用至 安装后启动 应用列表中
        enjoySDK.addInstallAutoStartRules(appRule)
//        从 安装后启动 应用列表中删除APP
//        enjoySDK.removeInstallAutoStartRules(appRule)
        //清空 安装后启动 应用列表
//        enjoySDK.clearInstallAutoStartRules()
        //时间配置参数
//        enjoySDK.switchAutoDateAndTime(true)
        //自动配置时区功能开关函数。
//        enjoySDK.switchAutoTimeZone(true)
        //设置系统时间显示格式 24小时制/12小时制。
//        enjoySDK.setTimeFormat(McStateCode.TIME_HOUR_24)
        Loge.d("安装 静默安装状态：$isRules | 静默安装个数：${startRules.size} | 安装启动状态：$autoStartRules | 安装启动个数：${enjoySDK.getInstallAutoStartRules()} |  ")
    }

    /***
     * 获取进程号对应的进程名
     * @param pid 进程号
     * @return 进程名
     */
    private fun getProcessName(pid: Int): String? {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(FileReader("/proc/$pid/cmdline"))
            var processName: String = reader.readLine()
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

    private val mActivityList: MutableList<Activity> = ArrayList()
    private val mActivityLifecycleCallbacks: ActivityLifecycleCallbacks =
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    mActivityList.add(activity)
                    when (activity) {

                    }
                }

                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {
                    mActivityList.remove(activity)
                }
            }

    open fun finishAllActivity() {
        for (activity in mActivityList) {
            activity.finish()
        }
    }

    /***
     * 创建资源文件下载路径
     */
    private fun createDir() {
        //存储资源文件信息
        RESOURCE_DIR = filesDir.path + "/res"
        val res = File(RESOURCE_DIR)
        if (!res.exists()) res.mkdirs()
        AUDIO_DIR = filesDir.path + "/audio"
        val autdio = File(AUDIO_DIR)
        if (!autdio.exists()) autdio.mkdirs()
        DEFAULT_DIR = filesDir.path + "/def"
        val def = File(DEFAULT_DIR)
        if (!def.exists()) def.mkdirs()
        APK_DIR = filesDir.path + "/apk"
        val apk = File(APK_DIR)
        if (!apk.exists()) apk.mkdirs()
        BIN_DIR = filesDir.path + "/bin"
        val bin = File(BIN_DIR)
        if (!bin.exists()) bin.mkdirs()


    }

    /**
     * 串口初始化
     */
    private fun initSerialPort() {
        ioScope.launch {
//            CabinetSdk.init()
            SerialPortSdk.init()
        }
    }

}