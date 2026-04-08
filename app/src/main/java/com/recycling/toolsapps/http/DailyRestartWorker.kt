package com.recycling.toolsapps.http

/**
 * @author: lr
 * @created on: 2025/5/14 上午11:25
 * @description:指定时间重启
 */
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recycling.toolsapps.FaceApplication
import com.serial.port.utils.AppUtils
import com.serial.port.utils.Loge
import kotlinx.coroutines.delay
import nearby.lib.netwrok.response.SPreUtil
import kotlin.system.exitProcess

/***
 * 指定时间重启
 */
class DailyRestartWorker(
    context: Context, params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Loge.d("指定时间重启动app...doWork ${Thread.currentThread().name}")
        try {
            Loge.d("指定时间重启动app... ${Thread.currentThread().name}")
            val restartCount = SPreUtil[AppUtils.getContext(), "restartCount", 0] as Int
            val save = restartCount + 1
            SPreUtil.put(AppUtils.getContext(), "restartCount", save)
            SPreUtil.put(AppUtils.getContext(), SPreUtil.loginCount, 1)
            delay(2000)
            FaceApplication.getInstance().baseActivity?.let { act ->
                val pendingIntent: PendingIntent
                val delay = 5 * 1000L
                val intent =
                    AppUtils.getContext().packageManager.getLaunchIntentForPackage(AppUtils.getContext().packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                pendingIntent = PendingIntent.getActivity(
                    AppUtils.getContext(),
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val mgr =
                    AppUtils.getContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                mgr[AlarmManager.RTC, System.currentTimeMillis() + delay] = pendingIntent
                pendingIntent.send()
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        } catch (e: Exception) {
            Loge.d("指定时间重启动app... ${e.message}")
        }
        return Result.success()
    }



}