package com.serial.port.t

import kotlinx.coroutines.CompletableDeferred

/**
 * @author: lr
 * @created on: 2026/3/22 下午3:40
 * @description:
 */
/**
 * 指令优先级枚举
 */
enum class Priority(val value: Int) {
    LOW(0),      // 定时轮询、日志查询
    NORMAL(1),   // 重量查询、状态查询
    HIGH(2),     // 开门、校准、设置参数
    IMMEDIATE(3) // 紧急停止、报警解除
}

/**
 * 指令任务包装类
 */
data class CommandTask<T>(
    val priority: Priority = Priority.NORMAL,
    val maxRetries: Int = 3,       // 每个任务允许重试 3 次
    val action: suspend () -> Result<T>,
    val deferred: CompletableDeferred<Result<T>>,
    var currentRetry: Int = 0,     // 当前重试进度
    val timestamp: Long = System.currentTimeMillis()
) : Comparable<CommandTask<*>> {
    override fun compareTo(other: CommandTask<*>): Int {
        val res = other.priority.value.compareTo(this.priority.value)
        return if (res == 0) this.timestamp.compareTo(other.timestamp) else res
    }
}
