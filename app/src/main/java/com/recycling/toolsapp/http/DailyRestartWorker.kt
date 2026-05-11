package com.recycling.toolsapp.http

/**
 * @author: lr
 * @created on: 2025/5/14 上午11:25
 * @description:指定时间重启
 */
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recycling.toolsapp.FaceApplication
import com.recycling.toolsapp.utils.BoxResourceManager
import com.recycling.toolsapp.utils.OSUtils
import com.serial.port.utils.Loge

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
                // 1. 释放业务资源（关键：这里执行你原先 ViewModel 里的释放逻辑）
                // 建议将 ViewModel 的释放逻辑抽成全局可访问的方法
                BoxResourceManager.releaseAllResources()

                // 2. 调用上面的物理冷重启
                OSUtils.fullRestart(applicationContext)
            }

        } catch (e: Exception) {
            Loge.d("指定时间重启动app... ${e.message}")
        }
        return Result.success()
    }



}