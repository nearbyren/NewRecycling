package com.recycling.toolsapp.http

/**
 * @author: lr
 * @created on: 2025/5/14 上午11:26
 * @description: 指定时间重启
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random


object TaskRestartScheduler {

    // 任务标识前缀
    private const val DAILY_TASK_PREFIX = "restart_daily_task_"
    private const val WEEKLY_TASK_PREFIX = "restart_weekly_task_"
    private const val MONTHLY_TASK_PREFIX = "restart_monthly_task_"
    private const val SPECIFIC_DATE_TASK_PREFIX = "restart_specific_date_task_"
    private const val TIME_RANGE_TASK_PREFIX = "restart_time_range_task_"
    private const val TODAY_RANGE_TASK_PREFIX = "restart_today_range_task_"
    private const val IMMEDIATE_TASK_PREFIX = "restart_immediate_task_"

    // 时间格式化器
    @RequiresApi(Build.VERSION_CODES.O)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 调度每日任务
     * @param context 上下文
     * @param time 任务执行时间（格式 "HH:mm" 或 "HH:mm:ss"）
     * @param taskName 可选的自定义任务名称
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleDaily(context: Context, time: String, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)
        val fullTaskName = buildTaskName(DAILY_TASK_PREFIX, time, taskName)

        val dailyRequest = buildDailyRequest(time)
        workManager.enqueueUniquePeriodicWork(
            fullTaskName,
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyRequest
        )
        Loge.d("已调度每日任务: $fullTaskName, 执行时间: $time")
    }

    /**
     * 调度每周任务
     * @param context 上下文
     * @param dayOfWeek 星期几 (Calendar常量，如 Calendar.MONDAY)
     * @param time 任务执行时间
     * @param taskName 可选的自定义任务名称
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleWeekly(context: Context, dayOfWeek: Int, time: String, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)
        val fullTaskName = buildTaskName(WEEKLY_TASK_PREFIX, "${dayOfWeek}_$time", taskName)

        val weeklyRequest = buildWeeklyRequest(dayOfWeek, time)
        workManager.enqueueUniquePeriodicWork(
            fullTaskName,
            ExistingPeriodicWorkPolicy.UPDATE,
            weeklyRequest
        )
        Loge.d("已调度每周任务: $fullTaskName, 星期$dayOfWeek, 时间: $time")
    }

    /**
     * 调度每月任务
     * @param context 上下文
     * @param dayOfMonth 每月的哪一天 (1-31)
     * @param time 任务执行时间
     * @param taskName 可选的自定义任务名称
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleMonthly(context: Context, dayOfMonth: Int, time: String, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)
        val fullTaskName = buildTaskName(MONTHLY_TASK_PREFIX, "${dayOfMonth}_$time", taskName)

        val monthlyRequest = buildMonthlyRequest(dayOfMonth, time)
        workManager.enqueueUniquePeriodicWork(
            fullTaskName,
            ExistingPeriodicWorkPolicy.UPDATE,
            monthlyRequest
        )
        Loge.d("已调度每月任务: $fullTaskName, 日期: $dayOfMonth, 时间: $time")
    }

    /**
     * 调度特定日期任务
     * @param context 上下文
     * @param date 特定日期 (格式 "yyyy-MM-dd")
     * @param time 任务执行时间
     * @param taskName 可选的自定义任务名称
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleSpecificDate(context: Context, date: String, time: String, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)
        val fullTaskName = buildTaskName(SPECIFIC_DATE_TASK_PREFIX, "${date}_$time", taskName)

        val specificDateRequest = buildSpecificDateRequest(date, time)
        workManager.enqueueUniqueWork(
            fullTaskName,
            ExistingWorkPolicy.REPLACE,
            specificDateRequest
        )
        Loge.d("已调度特定日期任务: $fullTaskName, 日期: $date, 时间: $time")
    }

    /**
     * 调度时间段任务（每天在指定时间段内随机执行）
     * @param context 上下文
     * @param startTime 开始时间 (格式 "HH:mm")
     * @param endTime 结束时间 (格式 "HH:mm")
     * @param taskName 可选的自定义任务名称
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleTimeRange(context: Context, startTime: String, endTime: String, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)
        val fullTaskName = buildTaskName(TIME_RANGE_TASK_PREFIX, "${startTime}_$endTime", taskName)

        val timeRangeRequest = buildTimeRangeRequest(startTime, endTime)
        workManager.enqueueUniquePeriodicWork(
            fullTaskName,
            ExistingPeriodicWorkPolicy.UPDATE,
            timeRangeRequest
        )
        Loge.d("已调度时间段任务: $fullTaskName, 时间段: $startTime - $endTime")
    }

    /**
     * 调度当天时间段任务（仅在当天指定时间段内执行一次）
     * @param context 上下文
     * @param startTime 开始时间 (格式 "HH:mm")
     * @param endTime 结束时间 (格式 "HH:mm")
     * @param taskName 可选的自定义任务名称
     * @param executeIfMissed 如果当前时间已过结束时间，是否立即执行（默认false）
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleTodayTimeRange(
        context: Context,
        startTime: String,
        endTime: String,
        taskName: String? = null,
        executeIfMissed: Boolean = false
    ) {
        val workManager = WorkManager.getInstance(context)
        val fullTaskName = buildTaskName(TODAY_RANGE_TASK_PREFIX, "${startTime}_$endTime", taskName)

        val todayRangeRequest = buildTodayTimeRangeRequest(startTime, endTime, executeIfMissed)
        if (todayRangeRequest != null) {
            workManager.enqueueUniqueWork(
                fullTaskName,
                ExistingWorkPolicy.REPLACE,
                todayRangeRequest
            )
            Loge.d("已调度当天时间段任务: $fullTaskName, 时间段: $startTime - $endTime")
        } else {
            Loge.d("当天时间段任务已过时，未调度: $fullTaskName")
        }
    }

    /**
     * 调度当天多个时间段任务
     * @param context 上下文
     * @param timeRanges 时间段列表，每个时间段为 Pair(开始时间, 结束时间)
     * @param taskName 可选的自定义任务名称
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleTodayMultipleRanges(
        context: Context,
        timeRanges: List<Pair<String, String>>,
        taskName: String? = null
    ) {
        timeRanges.forEachIndexed { index, (startTime, endTime) ->
            val rangeTaskName = if (!taskName.isNullOrEmpty()) {
                "${taskName}_range_${index + 1}"
            } else {
                "multiple_range_${index + 1}"
            }
            scheduleTodayTimeRange(context, startTime, endTime, rangeTaskName)
        }
        Loge.d("已调度当天多个时间段任务，共${timeRanges.size}个时间段")
    }

    /**
     * 立即执行一次任务
     * @param context 上下文
     * @param taskName 可选的自定义任务名称
     */
    fun triggerImmediately(context: Context, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)
        val fullTaskName = buildTaskName(IMMEDIATE_TASK_PREFIX, "now", taskName)

        val immediateRequest = buildImmediateRequest()
        workManager.enqueueUniqueWork(
            fullTaskName,
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
        Loge.d("已触发立即执行任务: $fullTaskName")
    }

    /**
     * 取消指定任务
     */
    fun cancelTask(context: Context, taskType: String, identifier: String, taskName: String? = null) {
        val workManager = WorkManager.getInstance(context)
        val prefix = when (taskType.toLowerCase()) {
            "daily" -> DAILY_TASK_PREFIX
            "weekly" -> WEEKLY_TASK_PREFIX
            "monthly" -> MONTHLY_TASK_PREFIX
            "specific" -> SPECIFIC_DATE_TASK_PREFIX
            "range" -> TIME_RANGE_TASK_PREFIX
            "todayrange" -> TODAY_RANGE_TASK_PREFIX
            else -> DAILY_TASK_PREFIX
        }
        val fullTaskName = buildTaskName(prefix, identifier, taskName)
        workManager.cancelUniqueWork(fullTaskName)
        Loge.d("已取消任务: $fullTaskName")
    }

    /**
     * 取消所有任务
     */
    fun cancelAllTasks(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
        Loge.d("已取消所有任务")
    }

    /**
     * 检查当天时间段任务是否可调度
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun isTodayTimeRangeAvailable(startTime: String, endTime: String): Boolean {
        return calculateTodayTimeRangeDelay(startTime, endTime) != null
    }

    /**
     * 获取当前时间状态信息
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getCurrentTimeStatus(): String {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return "当前时间: ${now.format(formatter)}"
    }

    // 构建每日请求
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildDailyRequest(time: String): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<DailyRestartWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateInitialDelay(time))
            .addTag("restart_daily_task")
            .build()
    }

    // 构建每周请求
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildWeeklyRequest(dayOfWeek: Int, time: String): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<DailyRestartWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(calculateWeeklyDelay(dayOfWeek, time))
            .addTag("restart_weekly_task")
            .build()
    }

    // 构建每月请求
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildMonthlyRequest(dayOfMonth: Int, time: String): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<DailyRestartWorker>(30, TimeUnit.DAYS)
            .setInitialDelay(calculateMonthlyDelay(dayOfMonth, time))
            .addTag("restart_monthly_task")
            .build()
    }

    // 构建特定日期请求
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildSpecificDateRequest(date: String, time: String): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<DailyRestartWorker>()
            .setInitialDelay(calculateSpecificDateDelay(date, time))
            .addTag("restart_specific_date_task")
            .build()
    }

    // 构建时间段请求
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildTimeRangeRequest(startTime: String, endTime: String): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<DailyRestartWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateRandomTimeRangeDelay(startTime, endTime))
            .addTag("restart_time_range_task")
            .build()
    }

    // 构建当天时间段请求
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildTodayTimeRangeRequest(
        startTime: String,
        endTime: String,
        executeIfMissed: Boolean = false
    ): OneTimeWorkRequest? {
        val delay = calculateTodayTimeRangeDelay(startTime, endTime, executeIfMissed) ?: return null

        return OneTimeWorkRequestBuilder<DailyRestartWorker>()
            .setInitialDelay(delay)
            .addTag("restart_today_range_task")
            .build()
    }

    // 构建立即请求
    private fun buildImmediateRequest(): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<DailyRestartWorker>()
            .addTag("restart_immediate_task")
            .build()
    }

    // 计算当天时间段任务的延迟
    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateTodayTimeRangeDelay(
        startTime: String,
        endTime: String,
        executeIfMissed: Boolean = false
    ): Duration? {
        val startLocalTime = parseTimeString(startTime)
        val endLocalTime = parseTimeString(endTime)
        val now = LocalTime.now()

        // 验证时间范围有效性
        if (endLocalTime.isBefore(startLocalTime)) {
            Loge.e("结束时间 $endTime 在开始时间 $startTime 之前，时间范围无效")
            return null
        }

        return when {
            // 情况1: 当前时间在时间段之前 - 延迟到开始时间
            now.isBefore(startLocalTime) -> {
                Duration.between(now, startLocalTime)
            }

            // 情况2: 当前时间在时间段内 - 立即执行或随机延迟
            now.isBefore(endLocalTime) -> {
                // 可以选择立即执行或在剩余时间内随机延迟
                val remainingDuration = Duration.between(now, endLocalTime)
                if (remainingDuration.toMinutes() > 5) {
                    // 如果剩余时间超过5分钟，随机延迟0到剩余时间的延迟
                    val randomMinutes = Random.nextLong(0, remainingDuration.toMinutes() + 1)
                    Duration.ofMinutes(randomMinutes)
                } else {
                    // 剩余时间不足5分钟，立即执行
                    Duration.ZERO
                }
            }

            // 情况3: 当前时间已过时间段
            else -> {
                if (executeIfMissed) {
                    Loge.d("时间段已过，立即执行")
                    Duration.ZERO
                } else {
                    Loge.d("时间段已过，跳过执行")
                    null
                }
            }
        }
    }

    // 计算每日任务的初始延迟
    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateInitialDelay(time: String): Duration {
        val targetTime = parseTimeString(time)
        val now = Calendar.getInstance()
        val targetCal = createTargetCalendar(targetTime, now)
        val delayMillis = targetCal.timeInMillis - now.timeInMillis
        return Duration.ofMillis(delayMillis)
    }

    // 计算每周任务的初始延迟
    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateWeeklyDelay(dayOfWeek: Int, time: String): Duration {
        val targetTime = parseTimeString(time)
        val now = Calendar.getInstance()
        val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        var daysToAdd = dayOfWeek - currentDayOfWeek
        if (daysToAdd < 0) {
            daysToAdd += 7
        }

        val targetCal = createTargetCalendar(targetTime, now).apply {
            add(Calendar.DAY_OF_YEAR, daysToAdd)

            if (daysToAdd == 0 && timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 7)
            }
        }

        val delayMillis = targetCal.timeInMillis - now.timeInMillis
        return Duration.ofMillis(delayMillis)
    }

    // 计算每月任务的初始延迟
    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateMonthlyDelay(dayOfMonth: Int, time: String): Duration {
        val targetTime = parseTimeString(time)
        val now = Calendar.getInstance()
        val currentDayOfMonth = now.get(Calendar.DAY_OF_MONTH)

        val targetCal = createTargetCalendar(targetTime, now).apply {
            if (dayOfMonth >= currentDayOfMonth) {
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            } else {
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }

            if (get(Calendar.DAY_OF_MONTH) == currentDayOfMonth && timeInMillis <= now.timeInMillis) {
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
        }

        val delayMillis = targetCal.timeInMillis - now.timeInMillis
        return Duration.ofMillis(delayMillis)
    }

    // 计算特定日期任务的延迟
    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateSpecificDateDelay(date: String, time: String): Duration {
        val targetDateTime = parseDateTimeString(date, time)
        val now = Instant.now()
        val delayMillis = targetDateTime.toInstant(ZoneOffset.UTC).toEpochMilli() - now.toEpochMilli()
        return Duration.ofMillis(maxOf(delayMillis, 0))
    }

    // 计算时间段内的随机延迟
    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateRandomTimeRangeDelay(startTime: String, endTime: String): Duration {
        val startLocalTime = parseTimeString(startTime)
        val endLocalTime = parseTimeString(endTime)

        val now = LocalTime.now()
        val todayStart = LocalDateTime.of(LocalDate.now(), startLocalTime)
        val todayEnd = LocalDateTime.of(LocalDate.now(), endLocalTime)

        val randomTimeInRange = if (now.isAfter(startLocalTime) && now.isBefore(endLocalTime)) {
            val randomMinutes = (0 until Duration.between(now, endLocalTime).toMinutes()).random()
            now.plusMinutes(randomMinutes)
        } else {
            val totalMinutes = Duration.between(startLocalTime, endLocalTime).toMinutes()
            val randomMinutes = (0 until totalMinutes).random()
            startLocalTime.plusMinutes(randomMinutes)
        }

        val targetDateTime = LocalDateTime.of(LocalDate.now(), randomTimeInRange)
        val delayMillis = Duration.between(LocalDateTime.now(), targetDateTime).toMillis()

        return Duration.ofMillis(if (delayMillis < 0) delayMillis + 24 * 60 * 60 * 1000 else delayMillis)
    }

    // 创建目标日历
    @RequiresApi(Build.VERSION_CODES.O) private fun createTargetCalendar(targetTime: LocalTime, now: Calendar): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, targetTime.hour)
            set(Calendar.MINUTE, targetTime.minute)
            set(Calendar.SECOND, targetTime.second)
            set(Calendar.MILLISECOND, 0)
        }
    }

    // 解析时间字符串
    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseTimeString(time: String): LocalTime {
        return try {
            LocalTime.parse(time)
        } catch (e: Exception) {
            try {
                LocalTime.parse("$time:00")
            } catch (e: Exception) {
                Loge.e("Invalid time format: $time. Using default 23:00")
                LocalTime.of(23, 0)
            }
        }
    }

    // 解析日期时间字符串
    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseDateTimeString(date: String, time: String): LocalDateTime {
        val localDate = try {
            LocalDate.parse(date)
        } catch (e: Exception) {
            Loge.e("Invalid date format: $date. Using today")
            LocalDate.now()
        }

        val localTime = parseTimeString(time)
        return LocalDateTime.of(localDate, localTime)
    }

    // 构建唯一的任务名称
    private fun buildTaskName(prefix: String, identifier: String, customName: String?): String {
        return if (!customName.isNullOrEmpty()) {
            "${prefix}_${customName}_$identifier"
        } else {
            "${prefix}_$identifier"
        }
    }
}