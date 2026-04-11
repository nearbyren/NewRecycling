package com.recycling.toolsapp.http

/**
 * @author: lr
 * @created on: 2025/5/14 上午11:25
 * @description:每天定时开灯关灯
 */
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.serial.port.utils.Loge
import nearby.lib.signal.livebus.BusType
import nearby.lib.signal.livebus.LiveBus

/***
 * 每天定时开灯关灯
 */
class DailyLightsWorker(
    context: Context, params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 获取执行时间信息
        val scheduledTime = inputData.getString("scheduled_time")
        lights(scheduledTime)
        return Result.success()
    }

    private fun lights(scheduledTime: String?) {
        Loge.d("流程 testLightsCmd 指定时间到了 lights $scheduledTime")
        LiveBus.get(BusType.BUS_LIGHTS_MSG).post(scheduledTime)
    }

}