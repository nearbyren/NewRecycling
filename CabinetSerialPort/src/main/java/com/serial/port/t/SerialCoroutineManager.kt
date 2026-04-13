package com.serial.port.t

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.nio.ByteBuffer

class SerialCoroutineManager(private val context: Context) {
    
    private var serialPort: UsbSerialPort? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 使用 SharedFlow 发送接收到的数据
    private val _dataFlow = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val dataFlow: SharedFlow<ByteArray> = _dataFlow.asSharedFlow()
    
    // 字符串形式的数据流
    val stringDataFlow: Flow<String> = dataFlow.map { String(it, Charsets.UTF_8) }
    
    // 十六进制字符串流
    val hexDataFlow: Flow<String> = dataFlow.map { bytes ->
        bytes.joinToString(" ") { "%02X".format(it) }
    }
    
    // 连接状态
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // 错误事件通道
    private val _errorChannel = Channel<String>(Channel.CONFLATED)
    val errorFlow = _errorChannel.receiveAsFlow()
    
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }
    
    /**
     * 连接串口设备（挂起函数）
     */
    suspend fun connect(
        baudRate: Int = 115200,
        dataBits: Int = 8,
        stopBits: Int = UsbSerialPort.STOPBITS_1,
        parity: Int = UsbSerialPort.PARITY_NONE
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            
            if (availableDrivers.isEmpty()) {
                _errorChannel.send("未找到USB设备")
                _connectionState.value = ConnectionState.ERROR
                return@withContext false
            }
            
            val driver = availableDrivers[0]
            val connection = manager.openDevice(driver.device)
            
            if (connection == null) {
                _errorChannel.send("无法打开设备，需要USB权限")
                _connectionState.value = ConnectionState.ERROR
                return@withContext false
            }
            
            serialPort = driver.ports[0].apply {
                open(connection)
                setParameters(baudRate, dataBits, stopBits, parity)
            }
            
            _connectionState.value = ConnectionState.CONNECTED
            startReading()
            
            return@withContext true
        } catch (e: Exception) {
            _errorChannel.send("连接失败: ${e.message}")
            _connectionState.value = ConnectionState.ERROR
            return@withContext false
        }
    }
    
    /**
     * 开始读取数据（协程方式）
     */
    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(4096)
            
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    serialPort?.let { port ->
                        if (port.isOpen) {
                            // 使用 withTimeoutOrNull 避免永久阻塞
                            val len = withTimeoutOrNull(1000) {
                                port.read(buffer, 200)
                            } ?: 0
                            
                            if (len > 0) {
                                val data = buffer.copyOf(len)
                                _dataFlow.emit(data)
                            }
                        }
                    }
                    delay(10) // 避免过度循环
                } catch (e: IOException) {
                    if (e.message?.contains("device disconnected") == true) {
                        _errorChannel.send("设备已断开连接")
                        _connectionState.value = ConnectionState.DISCONNECTED
                        break
                    }
                    _errorChannel.send("读取错误: ${e.message}")
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    _errorChannel.send("未知错误: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 发送字符串数据（挂起函数）
     */
    suspend fun sendString(data: String, addNewLine: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val sendData = if (addNewLine) "$data\r\n" else data
            serialPort?.write(sendData.toByteArray(), 1000)
            return@withContext true
        } catch (e: Exception) {
            _errorChannel.send("发送失败: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * 发送字节数据
     */
    suspend fun sendBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            serialPort?.write(bytes, 1000)
            return@withContext true
        } catch (e: Exception) {
            _errorChannel.send("发送失败: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * 发送十六进制字符串（例如："01 02 03 FF"）
     */
    suspend fun sendHex(hexString: String): Boolean {
        return try {
            val cleanHex = hexString.replace(" ", "")
            require(cleanHex.length % 2 == 0) { "十六进制字符串长度必须是偶数" }
            
            val bytes = cleanHex.chunked(2).map { 
                it.toInt(16).toByte() 
            }.toByteArray()
            
            sendBytes(bytes)
        } catch (e: Exception) {
            _errorChannel.send("十六进制转换失败: ${e.message}")
            false
        }
    }
    

    
    /**
     * 读取直到遇到指定结束符
     */
    suspend fun readUntil(delimiter: Byte, timeoutMs: Long = 5000): ByteArray? = withContext(Dispatchers.IO) {
        val buffer = ByteArrayOutputStream()
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            serialPort?.let { port ->
                val byte = ByteArray(1)
                if (port.read(byte, 100) > 0) {
                    buffer.write(byte[0].toInt())
                    if (byte[0] == delimiter) {
                        return@withContext buffer.toByteArray()
                    }
                }
            }
            delay(10)
        }
        return@withContext null
    }
    
    /**
     * 清空缓冲区
     */
    suspend fun flush() = withContext(Dispatchers.IO) {
        serialPort?.let { port ->
            val buffer = ByteArray(1024)
            while (port.read(buffer, 10) > 0) {
                // 清空缓冲区
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        readJob?.cancel()
        try {
            serialPort?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serialPort = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
    
    /**
     * 关闭管理器（释放资源）
     */
    fun close() {
        disconnect()
        scope.cancel()
    }
}

// 辅助类
class ByteArrayOutputStream {
    private val bytes = mutableListOf<Byte>()
    
    fun write(byte: Int) {
        bytes.add(byte.toByte())
    }
    
    fun toByteArray(): ByteArray = bytes.toByteArray()
}