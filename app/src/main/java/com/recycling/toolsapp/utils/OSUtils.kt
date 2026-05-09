package com.recycling.toolsapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * @author:
 * @created on: 2022/10/19 9:26
 * @description:
 */

object OSUtils {
    const val ROM_MIUI = "MIUI"
    const val ROM_EMUI = "EMUI"
    const val ROM_FLYME = "FLYME"
    const val ROM_OPPO = "OPPO"
    const val ROM_SMARTISAN = "SMARTISAN"
    const val ROM_VIVO = "VIVO"
    const val ROM_QIKU = "QIKU"
    private const val KEY_VERSION_MIUI = "ro.miui.ui.version.name"
    private const val KEY_VERSION_EMUI = "ro.build.version.emui"
    private const val KEY_VERSION_OPPO = "ro.build.version.opporom"
    private const val KEY_VERSION_SMARTISAN = "ro.smartisan.version"
    private const val KEY_VERSION_VIVO = "ro.vivo.os.version"
    private var sName: String? = null
    private var sVersion: String? = null
    fun isEmui(): Boolean {
        return check(ROM_EMUI)
    }

    fun isMiui(): Boolean {
        return check(ROM_MIUI)
    }

    fun isVivo(): Boolean {
        return check(ROM_VIVO)
    }

    fun isOppo(): Boolean {
        return check(ROM_OPPO)
    }

    fun isFlyme(): Boolean {
        return check(ROM_FLYME)
    }

    fun is360(): Boolean {
        return check(ROM_QIKU) || check("360")
    }

    fun isSmartisan(): Boolean {
        return check(ROM_SMARTISAN)
    }

    fun getName(): String? {
        if (sName == null) {
            check("")
        }
        return sName
    }

    fun getVersion(): String? {
        if (sVersion == null) {
            check("")
        }
        return sVersion
    }

    fun check(rom: String): Boolean {
        if (sName != null) {
            return sName == rom
        }
        if (!TextUtils.isEmpty(getProp(KEY_VERSION_MIUI).also {
                sVersion = it
            })) {
            sName = ROM_MIUI
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_EMUI).also {
                sVersion = it
            })) {
            sName = ROM_EMUI
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_OPPO).also {
                sVersion = it
            })) {
            sName = ROM_OPPO
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_VIVO).also {
                sVersion = it
            })) {
            sName = ROM_VIVO
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_SMARTISAN).also {
                sVersion = it
            })) {
            sName = ROM_SMARTISAN
        } else {
            sVersion = Build.DISPLAY
            if (sVersion!!.toUpperCase().contains(ROM_FLYME)) {
                sName = ROM_FLYME
            } else {
                sVersion = Build.UNKNOWN
                sName = Build.MANUFACTURER.toUpperCase()
            }
        }
        return sName == rom
    }

    fun getProp(name: String): String? {
        var line: String? = null
        var input: BufferedReader? = null
        try {
            val p = Runtime.getRuntime().exec("getprop $name")
            input = BufferedReader(InputStreamReader(p.inputStream), 1024)
            line = input.readLine()
            input.close()
        } catch (ex: IOException) {
            return null
        } finally {
            if (input != null) {
                try {
                    input.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return line
    }

    fun restartAppFrontDesk(activity: AppCompatActivity) {
        // 2. 创建重启Intent
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

        // 3. 使用Handler延迟一点再重启，确保finishAffinity完成
        Handler(Looper.getMainLooper()).postDelayed({
            activity.finishAffinity()
            activity.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }, 3000)  // 100ms延迟足够，不需要5秒
    }

    fun fullRestart(context: Context) {
        val packageName = context.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)

        launchIntent?.let { intent ->
            // 使用 FLAG_ACTIVITY_NEW_TASK 确保在新任务中启动
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

            // 创建 PendingIntent
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(), // 唯一请求码
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 设置 AlarmManager 延迟启动
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.RTC,
                System.currentTimeMillis() + 2000, // 100ms 延迟
                pendingIntent
            )

            // 立即杀死当前进程
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

}

