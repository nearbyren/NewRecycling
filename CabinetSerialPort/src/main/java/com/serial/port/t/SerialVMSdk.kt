package com.serial.port.t

/**
 * @author: lr
 * @created on: 2026/3/21 下午3:58
 * @description:
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.ByteUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

enum class PortStatus {
    IDLE,       // 空闲
    CONNECTING, // 连接中
    CONNECTED,  // 已连接
    ERROR       // 发生异常
}

class SerialVM : ViewModel() {
    private val _portStatus = MutableStateFlow(PortStatus.IDLE)
    val portStatus = _portStatus.asStateFlow()

    private val sendMutex = Mutex()
    private var responseWaiter: CompletableDeferred<ByteArray>? = null
    private val isRunning = AtomicBoolean(false)
    private var fis: FileInputStream? = null
    private var fos: FileOutputStream? = null

    private val extractor = FrameExtractor { packet ->
        responseWaiter?.complete(packet)
    }

    fun startMonitor(path: String, baud: Int) {
        if (isRunning.getAndSet(true)) return
        viewModelScope.launch(Dispatchers.IO) {
            var retryDelay = 1000L
            while (isActive && isRunning.get()) {
                try {
                    _portStatus.value = PortStatus.CONNECTING
                    val fd = SerialPortManagerSdk.instance.openDevice(path, baud)
                    if (fd != null) {
                        fis = FileInputStream(fd)
                        fos = FileOutputStream(fd)
                        _portStatus.value = PortStatus.CONNECTED
                        retryDelay = 1000L // 成功重连，重置时间
                        readLoop()
                    } else throw IOException("FD is null")
                } catch (e: Exception) {
                    _portStatus.value = PortStatus.ERROR
                    println("我的数据 接收处理 物理链路断开，${retryDelay}ms 后尝试重连")
                    closeStreams()
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(10000L) // 指数退避
                }
            }
        }
    }

    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(1024)
        while (isRunning.get()) {
            val len = fis?.read(buffer) ?: -1
            if (len == -1) throw IOException("End of Stream")
            println("我的数据 接收处理 readLoop ${ByteUtils.toHexString(buffer)}")
            if (len > 0) extractor.push(buffer.copyOfRange(0, len))
        }
    }

    /**
     * 基础发送：仅执行一次发送并等待响应
     * 供调度器 CommandScheduler 调用，实现精准的优先级重试
     */
    suspend fun sendOnce(data: ByteArray, timeout: Long = 3000): Result<ByteArray> {
        if (_portStatus.value != PortStatus.CONNECTED) return Result.failure(IOException("串口未连接"))

        return sendMutex.withLock {
            val waiter = CompletableDeferred<ByteArray>()
            responseWaiter = waiter
            try {
                withContext(Dispatchers.IO) {
                    println("我的数据 发送处理 sendOnce ${ByteUtils.toHexString(data)}")
                    BoxToolLogUtils.sendOriginalLower(0,  ByteUtils.toHexString(data))
                    fos?.write(data)
                    fos?.flush()
                }
                // 挂起直到收到数据或超时
                val response = withTimeout(timeout) { waiter.await() }
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                responseWaiter = null
            }
        }
    }

    /**
     * 保留原有方法，内部改为调用 sendOnce (可选，向下兼容)
     */
    suspend fun sendWithRetry(data: ByteArray, maxRetries: Int = 10, timeout: Long = 30000): Result<ByteArray> {
        var lastErr: Exception? = null
        repeat(maxRetries) { attempt ->
            val res = sendOnce(data, timeout)
            if (res.isSuccess) return res
            lastErr = res.exceptionOrNull() as? Exception
            delay(150L * (attempt + 1))
        }
        return Result.failure(lastErr ?: Exception("执行失败"))
    }
    fun stop() { isRunning.set(false); closeStreams(); extractor.clear() }

    private fun closeStreams() {
        try { fis?.close(); fos?.close() } catch (e: Exception) {}
        fis = null; fos = null
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}