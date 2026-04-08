package com.serial.port.t

/**
 * @author: lr
 * @created on: 2026/3/22 下午3:40
 * @description:
 */
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.PriorityBlockingQueue

class CommandScheduler(private val scope: CoroutineScope) {
  private val taskQueue = PriorityBlockingQueue<CommandTask<*>>()

  init { startScheduler() }

  suspend fun <T> submit(
    priority: Priority = Priority.NORMAL,
    maxRetries: Int = 3,
    block: suspend () -> Result<T>
  ): Result<T> {
    val deferred = CompletableDeferred<Result<T>>()
    taskQueue.add(CommandTask(priority, maxRetries, block, deferred))
    return deferred.await()
  }

  private fun startScheduler() {
    scope.launch(Dispatchers.IO) {
      while (isActive) {
        val task = taskQueue.take()

        // 增加 try-catch 保护，防止任务内部崩溃导致整个调度器停止
        val result = try {
          withTimeoutOrNull(2000) {
            task.action()
          } ?: Result.failure(Exception("串口响应超时"))
        } catch (e: Exception) {
          Result.failure(e)
        }

        if (result.isSuccess) {
          task.completeTask(result)
        } else {
          if (task.currentRetry < task.maxRetries) {
            task.currentRetry++
            Log.w("Scheduler", "指令[${task.priority}]重试中(${task.currentRetry}/${task.maxRetries})")
            delay(100)
            taskQueue.add(task)
          } else {
            task.completeTask(result)
          }
        }
        delay(50)
      }
    }
  }

  /**
   * 为 CommandTask 添加一个辅助扩展，优雅处理泛型转换
   */
  @Suppress("UNCHECKED_CAST")
  private fun <T> CommandTask<T>.completeTask(result: Result<*>) {
    (deferred as CompletableDeferred<Result<T>>).complete(result as Result<T>)
  }
}