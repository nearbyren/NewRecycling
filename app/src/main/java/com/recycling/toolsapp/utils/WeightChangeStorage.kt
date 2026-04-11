package com.recycling.toolsapp.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.serial.port.utils.Loge

class WeightChangeStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("weight_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VALUE_PREFIX = "value_"
        private const val KEY_TIMESTAMP_PREFIX = "timestamp_"
        private const val COOLDOWN_MILLIS = 25_000L // 25秒
    }

    /**
     * 存储键值对（带25秒冷却）
     * @param key 存储的键
     * @param value 存储的值
     * @param isSend 未发送则延迟
     * @return true-存储成功, false-冷却中未存储
     */
    fun putWithCooldown(key: String, value: String, isSend: Boolean): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastStoreTime = prefs.getLong(KEY_TIMESTAMP_PREFIX + key, 0L)
        if (isSend) return false
        // 判断是否在25秒内
        if (currentTime - lastStoreTime < COOLDOWN_MILLIS && value == "Failure") {
            // 冷却中，不存储
            val remainingSeconds = (COOLDOWN_MILLIS - (currentTime - lastStoreTime)) / 1000
            Loge.e("执行重量变动 冷却中，还剩 $remainingSeconds 秒")
            return false
        }

        // 超过25秒，执行存储并重置计时
        prefs.edit {
            putString(KEY_VALUE_PREFIX + key, value)
            putLong(KEY_TIMESTAMP_PREFIX + key, currentTime)
        }
        Loge.e("执行重量变动 存储成功: $key = $value")
        return true
    }

    /**
     * 强制存储（忽略冷却时间）
     */
    fun putForce(key: String, value: String) {
        prefs.edit {
            putString(KEY_VALUE_PREFIX + key, value)
            putLong(KEY_TIMESTAMP_PREFIX + key, System.currentTimeMillis())
        }
    }

    /**
     * 获取存储的值
     */
    fun get(key: String): String? {
        return prefs.getString(KEY_VALUE_PREFIX + key, null)
    }

    /**
     * 获取剩余冷却时间（毫秒）
     */
    fun getRemainingCooldown(key: String): Long {
        val lastStoreTime = prefs.getLong(KEY_TIMESTAMP_PREFIX + key, 0L)
        val elapsed = System.currentTimeMillis() - lastStoreTime
        return maxOf(0L, COOLDOWN_MILLIS - elapsed)
    }

    /**
     * 检查是否在冷却中
     */
    fun isInCooldown(key: String): Boolean {
        return getRemainingCooldown(key) > 0
    }

    /**
     * 清除指定键的数据
     */
    fun remove(key: String) {
        prefs.edit {
            remove(KEY_VALUE_PREFIX + key)
            remove(KEY_TIMESTAMP_PREFIX + key)
        }
    }

    /**
     * 清除所有数据
     */
    fun clear() {
        prefs.edit { clear() }
    }
}