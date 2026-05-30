package com.serial.port.t

import com.serial.port.utils.AsyncBatchLogger
import com.serial.port.utils.ByteUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 串口：独立读线程 + 单协程事务队列 + CountDownLatch 等待回包。
 * 读线程与事务线程通过 latch 同步，避免 CompletableDeferred 与帧被半包逻辑误删。
 */
object SerialPortEngine {
    enum class PortStatus { IDLE, CONNECTING, CONNECTED, ERROR }

    private val _portStatus = MutableStateFlow(PortStatus.IDLE)
    val portStatus = _portStatus.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private var fis: FileInputStream? = null
    private var fos: FileOutputStream? = null

    private val requestSeq = AtomicLong(0)
    private val responseLock = Any()

    @Volatile
    private var activeLatch: CountDownLatch? = null

    @Volatile
    private var activeExpectedCmd: Int = -1

    @Volatile
    private var activeResponse: ByteArray? = null

    @Volatile
    private var activeSeq: Long = -1L

    private data class SerialTransaction(
        val frame: ByteArray,
        val expectedCmdId: Int,
        val timeoutMs: Long,
        val seq: Long,
        val result: CompletableDeferred<Result<ByteArray>>,
    )

    private val transactionChannel = Channel<SerialTransaction>(Channel.UNLIMITED)

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    private var dispatcherJob: Job? = null

    private val extractor = FrameExtractorNew { packet ->
        val cmdId = packet[2].toInt() and 0xFF
        synchronized(responseLock) {
            val latch = activeLatch
            if (latch != null && cmdId == activeExpectedCmd && activeResponse == null) {
                activeResponse = packet
                latch.countDown()
                AsyncBatchLogger.log(
                    "recv [$cmdId] seq=$activeSeq ${ByteUtils.toHexStringFastTo(packet)}",
                    cmdId,
                )
            } else {
                AsyncBatchLogger.log(
                    "port read orphan cmd=$cmdId expect=$activeExpectedCmd seq=$activeSeq",
                    -1,
                )
            }
        }
    }

    private fun ensureDispatcher() {
        if (dispatcherJob?.isActive == true) return
        dispatcherJob = engineScope.launch {
            for (tx in transactionChannel) {
                processTransaction(tx)
            }
        }
    }

    fun start(path: String, baud: Int) {
        if (isRunning.getAndSet(true)) {
            ensureDispatcher()
            return
        }
        ensureDispatcher()
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

    /** 丢弃驱动层残留字节（上一枪迟到的回包），禁止进解析器，避免误绑到本枪 latch */
    private fun discardInputBuffer() {
        val available = fis?.available() ?: 0
        if (available > 0) {
            val buf = ByteArray(available)
            fis?.read(buf)
            AsyncBatchLogger.log(
                "read discard stale $available byte before send",
                -1,
            )
        }
    }

    private suspend fun processTransaction(tx: SerialTransaction) {
        val msgId = tx.expectedCmdId
        withContext(Dispatchers.IO) {
            extractor.clear()
            discardInputBuffer()
            val latch = CountDownLatch(1)
            synchronized(responseLock) {
                activeExpectedCmd = msgId
                activeLatch = latch
                activeResponse = null
                activeSeq = tx.seq
            }
            try {
                AsyncBatchLogger.log(
                    "send [$msgId] seq=${tx.seq} write ${ByteUtils.toHexStringFastTo(tx.frame)}",
                    msgId,
                )
                fos?.write(tx.frame)
                fos?.flush()
                val got = latch.await(tx.timeoutMs, TimeUnit.MILLISECONDS)
                val packet = synchronized(responseLock) { activeResponse }
                if (got && packet != null) {
                    AsyncBatchLogger.log("send [$msgId] seq=${tx.seq} ok", msgId)
                    tx.result.complete(Result.success(packet))
                } else {
                    AsyncBatchLogger.log(
                        "send [$msgId] seq=${tx.seq} fail: timeout got=$got hasPacket=${packet != null}",
                        msgId,
                    )
                    tx.result.complete(
                        Result.failure(TimeoutException("cmd=$msgId seq=${tx.seq} timeout=${tx.timeoutMs}ms")),
                    )
                }
            } catch (e: Exception) {
                AsyncBatchLogger.log(
                    "send [$msgId] seq=${tx.seq} fail: ${e.javaClass.simpleName} ${e.message}",
                    msgId,
                )
                tx.result.complete(Result.failure(e))
            } finally {
                synchronized(responseLock) {
                    if (activeLatch === latch) {
                        activeLatch = null
                        activeExpectedCmd = -1
                        activeResponse = null
                        activeSeq = -1L
                    }
                }
                // 不在此处 clear 解析器/抽干 fis：迟到的合法回包留给 discardInputBuffer 在下一枪开头丢掉
            }
        }
    }

    suspend fun sendOnce(data: ByteArray, timeout: Long = 5000): Result<ByteArray> {
        if (_portStatus.value != PortStatus.CONNECTED) {
            return Result.failure(IOException("串口未连接"))
        }
        val seq = requestSeq.incrementAndGet()
        val result = CompletableDeferred<Result<ByteArray>>()
        transactionChannel.send(
            SerialTransaction(
                frame = data,
                expectedCmdId = data[2].toInt() and 0xFF,
                timeoutMs = timeout,
                seq = seq,
                result = result,
            ),
        )
        return result.await()
    }

    suspend fun sendWithRetry(data: ByteArray, maxRetries: Int = 5, timeout: Long = 2000): Result<ByteArray> {
        var lastErr: Exception? = null
        val attempts = if (maxRetries > 0) maxRetries else 1
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(100L + (attempt * 50L))
            val res = sendOnce(data, timeout)
            if (res.isSuccess) return res
            lastErr = res.exceptionOrNull() as? Exception
            if (maxRetries > 0) {
                AsyncBatchLogger.log("send ${data[2]} no ${attempt + 1} second try...", -1)
            }
        }
        return Result.failure(lastErr ?: Exception("执行失败"))
    }

    fun stop() {
        isRunning.set(false)
        _portStatus.value = PortStatus.IDLE
        readJob?.cancel()
        dispatcherJob?.cancel()
        synchronized(responseLock) {
            activeLatch?.countDown()
            activeLatch = null
            activeExpectedCmd = -1
            activeResponse = null
        }
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
