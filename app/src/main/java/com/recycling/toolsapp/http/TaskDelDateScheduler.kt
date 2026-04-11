package com.recycling.toolsapp.http

/**
 * @author: lr
 * @created on: 2025/5/14 上午11:26
 * @description: 每日删除db数据库文件
 */
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.serial.port.utils.Loge
import java.time.Duration
import java.time.LocalTime
import java.util.Calendar
import java.util.concurrent.TimeUnit

object TaskDelDateScheduler {

    // 任务标识前缀
    private const val DAILY_TASK_PREFIX = "del_date_daily_task_"
    private const val IMMEDIATE_TASK_PREFIX = "del_date_immediate_task_"

    /**
     * 调度每日任务
     * @param context 上下文
     * @param time 任务执行时间（格式 "HH:mm" 或 "HH:mm:ss"）
     * @param taskName 可选的自定义任务名称（用于区分不同任务）
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleDaily(context: Context, time: String, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)

        // 生成唯一的任务名称
        val fullTaskName = buildTaskName(DAILY_TASK_PREFIX, time, taskName)

        val dailyRequest = buildDailyRequest(time)
        workManager.enqueueUniquePeriodicWork(
            fullTaskName,
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyRequest
        )
    }

    /**
     * 立即执行一次任务
     * @param context 上下文
     * @param taskName 可选的自定义任务名称（用于区分不同任务）
     */
    fun triggerImmediately(context: Context, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)

        // 生成唯一的任务名称
        val fullTaskName = buildTaskName(IMMEDIATE_TASK_PREFIX, "now", taskName)

        val immediateRequest = buildImmediateRequest()
        workManager.enqueueUniqueWork(
            fullTaskName,
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    /**
     * 取消指定时间的每日任务
     * @param context 上下文
     * @param time 任务执行时间
     * @param taskName 可选的自定义任务名称
     */
    fun cancelDailyTask(context: Context, time: String, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)
        val fullTaskName = buildTaskName(DAILY_TASK_PREFIX, time, taskName)
        workManager.cancelUniqueWork(fullTaskName)
    }

    /**
     * 取消所有任务
     * @param context 上下文
     */
    fun cancelAllTasks(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }

    // 构建每日请求
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildDailyRequest(time: String): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<DailyDelDateWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateInitialDelay(time)) // 初始延迟到指定时间
            .addTag("del_date_daily_task")
            .build()
    }

    // 构建立即请求
    private fun buildImmediateRequest(): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<DailyDelDateWorker>()
            .addTag("del_date_immediate_task")
            .build()
    }

    // 计算到指定时间的延迟
    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateInitialDelay(time: String): Duration {
        // 解析时间字符串
        val targetTime = parseTimeString(time)

        // 获取当前时间
        val now = Calendar.getInstance()

        // 创建目标日历
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, targetTime.hour)
            set(Calendar.MINUTE, targetTime.minute)
            set(Calendar.SECOND, targetTime.second)
            set(Calendar.MILLISECOND, 0)

            // 如果当前时间已过目标时间，延迟到次日
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // 计算延迟毫秒数
        val delayMillis = targetCal.timeInMillis - now.timeInMillis
        return Duration.ofMillis(delayMillis)
    }

    // 解析时间字符串（支持 HH:mm 或 HH:mm:ss 格式）
    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseTimeString(time: String): LocalTime {
        return try {
            // 尝试解析带秒的时间格式
            LocalTime.parse(time)
        } catch (e: Exception) {
            try {
                // 尝试解析不带秒的时间格式
                LocalTime.parse("$time:00")
            } catch (e: Exception) {
                // 默认使用 23:00:00
                Loge.e("Invalid time format: $time. Using default 23:00")
                LocalTime.of(23, 0)
            }
        }
    }

    // 构建唯一的任务名称
    private fun buildTaskName(prefix: String, time: String, customName: String?): String {
        return if (!customName.isNullOrEmpty()) {
            "${prefix}_${customName}_$time"
        } else {
            "${prefix}_$time"
        }
    }
}
