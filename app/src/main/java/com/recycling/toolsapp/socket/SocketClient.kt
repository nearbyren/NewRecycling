package com.recycling.toolsapp.socket


import com.recycling.toolsapp.db.DatabaseManager
import com.recycling.toolsapp.utils.CmdValue
import com.recycling.toolsapp.utils.JsonBuilder
import com.serial.port.utils.AppUtils
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.Loge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nearby.lib.netwrok.response.SPreUtil
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Coroutine-based TCP socket client with auto-reconnect, heartbeat, and backpressure-aware send queue.
 */
class SocketClient(
    val config: Config,
) {
    data class Config(
        val host: String,
        val port: Int,
        val connectTimeoutMillis: Long = TimeUnit.SECONDS.toMillis(10),
        val readTimeoutMillis: Int = TimeUnit.SECONDS.toMillis(30).toInt(),
        val writeFlushIntervalMillis: Long = 0L,
        var heartbeatIntervalMillis: Long = TimeUnit.SECONDS.toMillis(10),
        val heartbeatPayload: ByteArray = byteArrayOf(),
        val idleTimeoutMillis: Long = TimeUnit.MINUTES.toMillis(2),
        val minReconnectDelayMillis: Long = 500,
        val maxReconnectDelayMillis: Long = TimeUnit.SECONDS.toMillis(30),
        val reconnectBackoffMultiplier: Double = 2.0,
        val maxSendQueueBytes: Int = 1_048_576,
        val maxFrameSizeBytes: Int = 4 * 1024 * 1024,
    )

    /***
     *  START  启动
     *  DISCONNECTED  已断开连接
     *  CONNECTING  正在连接
     *  CONNECTED  已连接
     */
    enum class ConnectionState { START, DISCONNECTED, CONNECTING, CONNECTED }

    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _state2 = MutableSharedFlow<ConnectionState>(replay = 0)
    val state2: SharedFlow<ConnectionState> = _state2.asSharedFlow()


    private var socketResultListener: SocketResultListener? = null
    fun addSocketResultListener(callback: (SocketClient.ConnectionState) -> Unit) {
        Loge.e("出厂配置 initSocket SocketClient addSocketResultListener ")
        this.socketResultListener = SocketResultListener { state ->
            callback(state)
        }
    }
    fun deleteSocketResultListener() {
        socketResultListener = null
    }
    private var socketResultListener2: SocketResultListener2? = null
    fun addSocketResultListener2(callback: (SocketClient.ConnectionState) -> Unit) {
        Loge.e("出厂配置 initSocket SocketClient addSocketResultListener2 ")
        this.socketResultListener2 = SocketResultListener2 { state ->
            callback(state)
        }
    }
    fun deleteSocketResultListener2() {
        socketResultListener2 = null
    }



    private val _incoming = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    private val sendQueueByte = Channel<ByteArray>(capacity = Channel.BUFFERED)

    @Volatile
    private var socket: Socket? = null
    private val socketMutex = Mutex()

    @Volatile
    private var lastReceivedAtMillis: Long = System.currentTimeMillis()

    @Volatile
    private var running = false

    /***
     * 启动socket连接
     */
    suspend fun start() {
        Loge.e("出厂配置 initSocket SocketClient start running $running $clientScope")
        if (running) return
        running = true
        _state.value = ConnectionState.START
        _state2.emit(ConnectionState.START)
        socketResultListener?.caliResult(ConnectionState.START)
        socketResultListener2?.caliResult(ConnectionState.START)
        clientScope.launch { runMainLoop() }
    }

    /***
     * 关闭socket连接
     */
    suspend fun stop() {
        Loge.e("出厂配置 initSocket SocketClient stop ")
        running = false
        try {
            clientScope.coroutineContext.job.cancelAndJoin()
        } catch (e: CancellationException) {
            Loge.e("出厂配置 initSocket SocketClient stop ${e.message}")
        }
        closeSocketQuietly()
    }

    /***
     * @param text
     * 发送字节
     */
    suspend fun send(data: ByteArray) {
//        Loge.e("出厂配置 initSocket SocketClient send ByteArray  ${ByteUtils.toHexString(data)}")
        require(data.size <= config.maxFrameSizeBytes) { "Frame too large: ${data.size}" }
        // Backpressure control by counting queued bytes
        enqueueSend(data)
    }

    /***
     * @param text
     * 发送字符串
     */
    suspend fun sendText(text: String) {
//        Loge.e("出厂配置 initSocket SocketClient sendText  $text")
        send(text.toByteArray())
    }

    /***
     * 调查send
     * @param data
     *
     */
    private suspend fun enqueueSend(data: ByteArray) {
//        Loge.e("出厂配置 initSocket SocketClient enqueueSend  ${ByteUtils.toHexString(data)}")
        // Simple soft limit enforcement by suspending when over budget
        val queuedBytes = data.size
        if (queuedBytes > config.maxSendQueueBytes) {
            throw IOException("Send queue bytes over limit")
        }
        sendQueueByte.send(data)
    }

    /***
     * 运行主循环
     */
    private suspend fun runMainLoop() {
        var attempt = 0
        Loge.e("出厂配置 initSocket SocketClient runMainLoop $running ${clientScope.isActive}")
        while (running && clientScope.isActive) {
            try {
                _state.value = ConnectionState.CONNECTING
                _state2.emit(ConnectionState.CONNECTING)
                socketResultListener?.caliResult(ConnectionState.CONNECTING)
                socketResultListener2?.caliResult(ConnectionState.CONNECTING)
                Loge.e("出厂配置 initSocket SocketClient runMainLoop 连接中")
                connectAndServe()
                attempt = 0 // reset backoff after successful session
            } catch (e: CancellationException) {
                Loge.e("出厂配置 initSocket SocketClient runMainLoop catch1 ${e.message} running $running")
                BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,runMainLoop CancellationException ${e.message} running $running")
                break
            } catch (e: Exception) {
                // Swallow and backoff
                Loge.e("出厂配置 initSocket SocketClient runMainLoop catch2 ${e.message} running $running")
                BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,runMainLoop Exception ${e.message} running $running")
            } finally {
                Loge.e("出厂配置 initSocket SocketClient runMainLoop finally running $running")
                closeSocketQuietly()
                if (!running) break
                _state.value = ConnectionState.DISCONNECTED
                _state2.emit(ConnectionState.DISCONNECTED)
                socketResultListener?.caliResult(ConnectionState.DISCONNECTED)
                socketResultListener2?.caliResult(ConnectionState.DISCONNECTED)
            }

            attempt += 1
//            val delayMs = computeReconnectDelay(attempt)
//            Loge.e("出厂配置 initSocket SocketClient runMainLoop 重连接延迟 $delayMs")
            Loge.e("出厂配置 initSocket SocketClient runMainLoop 重连接延迟 ")
            delay(10000L)
        }
    }

    /***
     * 计算重新连接延迟
     * @param attempt
     */
    private fun computeReconnectDelay(attempt: Int): Long {
        Loge.e("出厂配置 initSocket SocketClient computeReconnectDelay attempt $attempt")
        val base =
            config.minReconnectDelayMillis * config.reconnectBackoffMultiplier.pow((attempt - 1).toDouble())
        val clamped = min(base, config.maxReconnectDelayMillis.toDouble()).toLong()
        val jitter = (clamped * 0.2 * Random.nextDouble()).toLong()
        return clamped + jitter
    }

    var input: BufferedInputStream? = null
    var output: BufferedOutputStream? = null
    var readerJob: Job? = null
    var writerJob: Job? = null
    var monitorJob: Job? = null

    /***
     * 连接和服务
     */
    private suspend fun connectAndServe() {
        Loge.e("出厂配置 initSocket SocketClient connectAndServe")
        val s = Socket()
        s.tcpNoDelay = true
        s.soTimeout = config.readTimeoutMillis
        s.connect(InetSocketAddress(config.host, config.port), config.connectTimeoutMillis.toInt())

        socketMutex.withLock { socket = s }

        lastReceivedAtMillis = System.currentTimeMillis()

        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
        readerJob = clientScope.launch {
            input?.let { i ->
                readLoop(i)
            }
        }
        writerJob = clientScope.launch {
            output?.let { o ->
                writeLoopByte(o)
            }

        }
//        val monitor = clientScope.launch { heartbeatAndIdleMonitor() }
        Loge.e("出厂配置 initSocket SocketClient connectAndServe 已连接")
        _state.value = ConnectionState.CONNECTED
        _state2.emit(ConnectionState.CONNECTED)
        socketResultListener?.caliResult(ConnectionState.CONNECTED)
        socketResultListener2?.caliResult(ConnectionState.CONNECTED)

        try {
            readerJob?.join()
        } finally {
//            writer.cancel()
//            monitor.cancel()
//
        }
    }

    /***
     * 启动心跳查询
     */
    suspend fun sendHeartbeat() {
        monitorJob = clientScope.launch { heartbeatAndIdleMonitor() }
//        monitor.cancel()
    }

    /***
     * 读取socket数据
     * @param input
     * 缓冲输入流
     */
    private suspend fun readLoop(input: BufferedInputStream) {
        Loge.e("出厂配置 initSocket SocketClient readLoop ")
        val buffer = ByteArray(8 * 1024)
        while (running && clientScope.isActive) {
            try {
                val read = input.read(buffer)
                if (read == -1) {
                    BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "SocketClient,readLoop Stream closed")
//                    LiveBus.get(BusType.BUS_NET_MSG).post("读取不到socket数据 Stream closed")
                    throw IOException("Stream closed")
                }
                lastReceivedAtMillis = System.currentTimeMillis()
                val frame = buffer.copyOf(read)
//                Loge.e("出厂配置 initSocket SocketClient readLoop ${ByteUtils.toHexString(frame)}")
                _incoming.emit(frame)
            } catch (e: IOException) {
                e.printStackTrace()
                Loge.e("出厂配置 initSocket SocketClient readLoop catch ${e.message}")
                BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,readLoop catch ${e.message}}")
                break
            }
        }
    }

    /***
     * 读取socket数据
     * @param output
     * 缓冲输出流
     */
    private suspend fun writeLoopByte(output: BufferedOutputStream) {
        Loge.e("出厂配置 initSocket SocketClient writeLoop running $running | isActive ${clientScope.isActive}")
        while (running && clientScope.isActive) {
            try {
                val data = sendQueueByte.receive()
//                Loge.e("出厂配置 initSocket SocketClient writeLoopByte byte：${ByteUtils.toHexString(data)}")
                BoxToolLogUtils.recordSocket(CmdValue.SEND, JsonBuilder.toByteArrayToStringNotPretty(data))
                output.write(data)
                if (config.writeFlushIntervalMillis == 0L) {
                    output.flush()
                } else {
                    // Optional coalescing
                    delay(config.writeFlushIntervalMillis)
                    output.flush()
                }
            } catch (e: CancellationException) {
                Loge.e("出厂配置 initSocket SocketClient writeLoop catch1 ${e.message}")
                BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,writeLoopByte catch1 ${e.message}}")
                break
            } catch (e: IOException) {
                Loge.e("出厂配置 initSocket SocketClient writeLoop catch2 ${e.message}")
                BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,writeLoopByte catch2 ${e.message}}")

                break
            }
        }
    }

    /***
     *
     * 心跳启动
     */
    private suspend fun heartbeatAndIdleMonitor() {
//       Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor $running | ${clientScope.isActive}")
        val hasHeartbeat =
            config.heartbeatIntervalMillis > 0 /*&& config.heartbeatPayload.isNotEmpty()*/
        while (running && clientScope.isActive) {
            val now = System.currentTimeMillis()
//            Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor 分钟：${config.idleTimeoutMillis} | 当前毫秒：$lastReceivedAtMillis | 当前-最后：${now - lastReceivedAtMillis}")
            if (config.idleTimeoutMillis > 0 && now - lastReceivedAtMillis > config.idleTimeoutMillis) {
                // Force reconnect by closing the socket
                Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor closeSocketQuietly")
                closeSocketQuietly()
                return
            }
//            Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor $hasHeartbeat")
            if (hasHeartbeat) {
                try {
//                    Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor trySend")
                    val stateList = DatabaseManager.queryStateList(AppUtils.getContext())

//                    Loge.e("出厂配置 initSocket SocketClient stateList：${stateList.size}")
                    val setSignal = SPreUtil[AppUtils.getContext(), SPreUtil.setSignal, 19] as Int
                    val setIr1 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr1, -1] as Int
                    val setIr2 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr2, -1] as Int
                    // 构建JSON对象
                    val jsonObject = JsonBuilder.build {
                        addProperty("cmd", "heartBeat")
                        addProperty("signal", setSignal)
                        // 添加数组
                        addArray("stateList") {
                            stateList.withIndex().forEach { (index, state) ->
                                addObject {
                                    addProperty("smoke", state.smoke)
                                    addProperty("capacity", state.capacity)
                                    if (setIr1 == 1 && index == 0) {
                                        addProperty("irState", state.irState)
                                    } else {
                                        addProperty("irState", 0)
                                    }
                                    if (setIr2 == 1 && index == 1) {
                                        addProperty("irState", state.irState)
                                    } else {
                                        addProperty("irState", 0)
                                    }
//                                    addProperty("irState", 1)
//                                    addProperty("weigh", 36.00)
                                    addProperty("weigh", state.weigh)
                                    addProperty("doorStatus", state.doorStatus)
                                    addProperty("lockStatus", state.lockStatus)
                                    addProperty("cabinId", state.cabinId ?: "")
                                }
                            }
                        }
                    }
//                    Loge.e("出厂配置 initSocket SocketClient 发送心跳数据：$jsonObject")
                    val byteArray = JsonBuilder.toByteArray(jsonObject)
                    sendQueueByte.trySend(byteArray)
                } catch (e: Exception) {
                    Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor catch ${e.message}")
                    BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,heartbeatAndIdleMonitor catch ${e.message}")

                }
            }
            delay(maxOf(1000L, config.heartbeatIntervalMillis))
        }
    }

    fun closeSocketIO() {
        readerJob?.cancel()
        writerJob?.cancel()
        monitorJob?.cancel()
        readerJob = null
        writerJob = null
        monitorJob = null
    }

    /***
     * 关闭socket
     */
    private fun closeSocketQuietly() {
        Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly $running | ${clientScope.isActive}")
        try {
            socketMutex.tryLock()?.let { locked ->
                if (locked) {
                    try {
                        closeSocketIO()
                        socket?.close()
                    } catch (e: Exception) {
                        Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly catch1 ${e.message}")
                        BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,closeSocketQuietly ${e.message}}")
                    } finally {
                        socket = null
                        socketMutex.unlock()
                        BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,closeSocketQuietly finally")
                        Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly finally")
                    }
                }
            }
        } catch (e: Exception) {
            Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly catch2 ${e.message}")
            BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,closeSocketQuietly catch2 ${e.message}")

        }
    }
}


