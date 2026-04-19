package com.recycling.toolsapp.http

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
import com.recycling.toolsapp.FaceApplication
import com.recycling.toolsapp.utils.OSUtils
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
            FaceApplication.getInstance().baseActivity?.let { act ->
                OSUtils.restartAppFrontDesk(act)
            }

        } catch (e: Exception) {
            Loge.d("指定时间重启动app... ${e.message}")
        }
        return Result.success()
    }



}