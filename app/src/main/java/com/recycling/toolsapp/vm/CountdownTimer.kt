package com.recycling.toolsapp.vm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class CountdownTimer(private val scope: CoroutineScope) {
    // 状态管理
    sealed class CountdownState {
        data class Running(val secondsRemaining: Int) : CountdownState()
        object Finished : CountdownState()
        object Starting : CountdownState()
        data class Error(val message: String) : CountdownState()
    }



    // 使用 MutableSharedFlow 推送状态
    private val _countdownState = MutableSharedFlow<CountdownState>(replay = 1)
    val countdownState: SharedFlow<CountdownState> = _countdownState.asSharedFlow()

    private var countdownJob: Job? = null

    /**
     * 启动倒计时
     * @param totalSeconds 总秒数
     */
    fun startCountdown(totalSeconds: Int) {
        // 取消现有倒计时
        countdownJob?.cancel()

        countdownJob = scope.launch {
            try {
                // 验证输入
                if (totalSeconds <= 0) {
                    _countdownState.emit(CountdownState.Error("无效的倒计时时间: $totalSeconds"))
                    return@launch
                }
                _countdownState.emit(CountdownState.Starting)
                // 倒计时循环
                for (remaining in totalSeconds downTo 0) {
                    // 推送当前状态
                    _countdownState.emit(CountdownState.Running(remaining))

                    // 等待1秒 (支持取消)
                    delay(1000)
                }

                // 完成状态
                _countdownState.emit(CountdownState.Finished)
            } catch (e: CancellationException) {
                // 正常取消，不处理
            } catch (e: Exception) {
                // 错误处理
                _countdownState.emit(CountdownState.Error("倒计时出错: ${e.message}"))
            }
        }
    }

    /**
     * 暂停倒计时
     */
    fun pauseCountdown() {
        countdownJob?.cancel()
    }

    /**
     * 恢复倒计时
     * @param remainingSeconds 剩余秒数
     */
    fun resumeCountdown(remainingSeconds: Int) {
        startCountdown(remainingSeconds)
    }

    /**
     * 重置倒计时
     */
    fun resetCountdown() {
        countdownJob?.cancel()
        scope.launch {
            _countdownState.emit(CountdownState.Finished)
        }
    }
}