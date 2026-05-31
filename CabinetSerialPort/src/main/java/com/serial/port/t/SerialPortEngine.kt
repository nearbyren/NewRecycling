package com.serial.port.t

import com.serial.port.utils.AsyncBatchLogger
import com.serial.port.utils.ByteUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 串口：独立读线程 + Flow 分发完整帧。
 * 发送只负责写串口，业务层通过 [frames] / [sendAndObserve] 监听回包。
 */
object SerialPortEngine {
    enum class PortStatus { IDLE, CONNECTING, CONNECTED, ERROR }

    data class SerialFrame(
        val seq: Long,
        val cmdId: Int,
        val packet: ByteArray,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val _portStatus = MutableStateFlow(PortStatus.IDLE)
    val portStatus = _portStatus.asStateFlow()

    private val _frames = MutableStateFlow<SerialFrame?>(null)
    val frames = _frames.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private var fis: FileInputStream? = null
    private var fos: FileOutputStream? = null

    private val responseSeq = AtomicLong(0)
    private val sendSeq = AtomicLong(0)
    private val writeMutex = Mutex()

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null

    private val extractor = FrameExtractorNew { packet ->
        val cmdId = packet[2].toInt() and 0xFF
        val frame = SerialFrame(
            seq = responseSeq.incrementAndGet(),
            cmdId = cmdId,
            packet = packet,
        )
        _frames.value = frame
        AsyncBatchLogger.log(
            "recv [$cmdId] seq=${frame.seq} ${ByteUtils.toHexStringFastTo(packet)}",
            cmdId,
        )
    }

    fun start(path: String, baud: Int) {
        if (isRunning.getAndSet(true)) {
            return
        }
        readJob = engineScope.launch {
            var retryDelay = 1000L
            while (isActive && isRunning.get()) {
                _portStatus.value = PortStatus.CONNECTING
                val success = SerialPortManagerSdk.instance.openDevice(path, baud)
                if (success) {
                    fis = SerialPortManagerSdk.instance.getInputStream()
                    fos = SerialPortManagerSdk.instance.getOutputStream()
                    _portStatus.value = PortStatus.CONNECTED
                    retryDelay = 1000L
                    try {
                        readLoop()
                    } catch (e: Exception) {
                        AsyncBatchLogger.log("port read interrupt: ${e.message}", -1)
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
                    val chunk = buffer.copyOfRange(0, len)
                    AsyncBatchLogger.log(ByteUtils.toHexStringFastTo(chunk), 0)
                    extractor.push(chunk)
                }
            }
        } catch (e: Exception) {
            AsyncBatchLogger.log("port read reading abnormality: ${e.message}", -1)
        }
    }

    /** 仅 readLoop 读 fis；事务线程禁止 read，避免与读线程抢字节导致偶发无 [0] */
    private const val PRE_SEND_DRAIN_MS = 80L
    private const val POST_WRITE_SETTLE_MS = 40L

    suspend fun send(data: ByteArray): Result<Unit> {
        if (_portStatus.value != PortStatus.CONNECTED) {
            return Result.failure(IOException("串口未连接"))
        }
        val msgId = data[2].toInt() and 0xFF
        val seq = sendSeq.incrementAndGet()
        return withContext(Dispatchers.IO) {
            extractor.clear()
            writeMutex.withLock {
                try {
                    delay(PRE_SEND_DRAIN_MS)
                    AsyncBatchLogger.log(
                        "send [$msgId] seq=$seq write ${ByteUtils.toHexStringFastTo(data)}",
                        msgId,
                    )
                    fos?.write(data)
                    fos?.flush()
                    delay(POST_WRITE_SETTLE_MS)
                    Result.success(Unit)
                } catch (e: Exception) {
                    AsyncBatchLogger.log(
                        "send [$msgId] seq=$seq fail: ${e.javaClass.simpleName} ${e.message}",
                        msgId,
                    )
                    Result.failure(e)
                }
            }
        }
    }

    fun observeCommand(cmdId: Int): Flow<ByteArray> =
        frames
            .filter { it?.cmdId == cmdId }
            .map { it!!.packet }

    fun sendAndObserve(data: ByteArray): Flow<ByteArray> = flow {
        val startSeq = responseSeq.get()
        val sendResult = send(data)
        sendResult.getOrThrow()
        val cmdId = data[2].toInt() and 0xFF
        emitAll(
            frames
                .filter { it != null && it.seq > startSeq && it.cmdId == cmdId }
                .map { it!!.packet },
        )
    }

    suspend fun sendOnce(data: ByteArray, timeout: Long = 5000): Result<ByteArray> {
        return runCatching {
            sendAndObserve(data).first()
        }
    }

    suspend fun sendWithRetry(data: ByteArray, maxRetries: Int = 5, timeout: Long = 2000): Result<ByteArray> {
        return sendOnce(data, timeout)
    }

    fun stop() {
        isRunning.set(false)
        _portStatus.value = PortStatus.IDLE
        readJob?.cancel()
        closeStreams()
        extractor.clear()
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
