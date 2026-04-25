package com.serial.port.t

import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.ByteUtils
import com.serial.port.utils.Loge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// 将原本 SerialVM 的逻辑搬迁到单例中
object SerialPortEngine {
    enum class PortStatus { IDLE, CONNECTING, CONNECTED, ERROR }

    private val _portStatus = MutableStateFlow(PortStatus.IDLE)
    val portStatus = _portStatus.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private var fis: FileInputStream? = null
    private var fos: FileOutputStream? = null

    private val sendMutex = Mutex()
    private var responseWaiter: CompletableDeferred<ByteArray>? = null
    private val pendingRequests = ConcurrentHashMap<Int , CompletableDeferred<ByteArray>>()
    private var sequenceId = 0

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null

    // 统一的数据分发：所有的解析回包都通过这里
    private val extractor = FrameExtractorNew{ packet ->
//        responseWaiter?.complete(packet)
        val cmdId = packet[2].toInt() and 0xFF // 强制转为 0~255 的整数
        pendingRequests.remove(cmdId)?.complete(packet)
    }

    fun start(path: String, baud: Int) {
        if (isRunning.getAndSet(true)) return

        readJob = engineScope.launch {
            var retryDelay = 1000L
            while (isActive && isRunning.get()) {
                _portStatus.value = PortStatus.CONNECTING
                val success = SerialPortManagerSdk.instance.openDevice(path, baud)

                if (success) {
                    // 获取并赋值给类成员，供发送方法使用
                    fis = SerialPortManagerSdk.instance.getInputStream()
                    fos = SerialPortManagerSdk.instance.getOutputStream()

                    _portStatus.value = PortStatus.CONNECTED
                    retryDelay = 1000L

                    try {
                        readLoop() // 调用提取出来的读取循环
                    } catch (e: Exception) {
                        BoxToolLogUtils.savePrintln("业务流：读取中断: ${e.message}")
                    } finally {
                        _portStatus.value = PortStatus.ERROR
                        closeStreams()
                    }
                } else {
                    _portStatus.value = PortStatus.ERROR
                }

                if (isRunning.get()) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(10000L)
                }
            }
        }
    }

    private fun readLoop() {
        val buffer = ByteArray(4096)
        try {
            while (isRunning.get()) {
                val len = fis?.read(buffer) ?: -1
                if (len == -1) break
                if (len > 0) {
                    // 拷贝当前读取到的实际有效长度
                    val validData = buffer.copyOfRange(0, len)
                    // 喂给提取器
                    extractor.push(validData)
                }
            }
        } catch (e: Exception) {
            Loge.e("串口读取异常: ${e.message}")
            BoxToolLogUtils.savePrintln("业务流：串口读取异常: ${e.message}")
        }
    }

    /**
     * 统一发送入口
     */
    suspend fun sendOnce(data: ByteArray, timeout: Long = 5000): Result<ByteArray> {
        if (_portStatus.value != PortStatus.CONNECTED) return Result.failure(IOException("串口未连接"))
        val msgId = data[2].toInt() and 0xFF // 统一 ID 取法
        return sendMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val available = fis?.available() ?: 0
                    if (available > 0) {
                        val skipBuffer = ByteArray(available)
                        fis?.read(skipBuffer) // 彻底排空旧缓冲区
                    }
                }
            }
            val waiter = CompletableDeferred<ByteArray>()
            // 存入前清理同 ID 的旧请求（虽然有锁，但在超时重试场景下这是双保险）
            pendingRequests.remove(msgId)?.cancel()
//            responseWaiter = waiter
            // 保存到待处理队列
            pendingRequests[msgId] = waiter
            try {
                withContext(Dispatchers.IO) {
                    Loge.i("SerialPort", "发送: ${ByteUtils.toHexString(data)}")
//                    BoxToolLogUtils.sendOriginalLower(0, ByteUtils.toHexString(data))
                    fos?.write(data)
                    fos?.flush()
//                    delay(10)

                }
                val response = withTimeout(timeout) { waiter.await() }
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
//                responseWaiter = null
                pendingRequests.remove(msgId)

            }
        }
    }

    /**
      * 保留原有方法，内部改为调用 sendOnce (可选，向下兼容)
     */
    suspend fun sendWithRetry(data: ByteArray, maxRetries: Int = 10, timeout: Long = 20000): Result<ByteArray> {
        var lastErr: Exception? = null
        repeat(maxRetries) { attempt ->
            val res = sendOnce(data, timeout)
            if (res.isSuccess) return res
            lastErr = res.exceptionOrNull() as? Exception
            delay(150L * (attempt + 1))
        }
        return Result.failure(lastErr ?: Exception("执行失败"))
    }

    fun stop() {
        isRunning.set(false)
        _portStatus.value = PortStatus.IDLE
        readJob?.cancel()
        closeStreams()
        extractor.clear()
        responseWaiter?.cancel()
        val iterators = pendingRequests.entries.iterator()
        while (iterators.hasNext()) {
            val entry = iterators.next()
            // 取消 Deferred，这会使得 await() 抛出 CancellationException
            entry.value.cancel()
            // 从 Map 中移除已取消的请求，防止内存泄漏
            iterators.remove()
        }
        pendingRequests?.clear()

        // 彻底释放硬件
        SerialPortManagerSdk.instance.closeAllSerialPort()
    }

    private fun closeStreams() {
        kotlin.runCatching {
            fis?.close()
            fos?.close()
        }
        fis = null
        fos = null
    }
}