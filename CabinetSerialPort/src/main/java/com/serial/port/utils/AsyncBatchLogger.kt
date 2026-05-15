package com.serial.port.utils


import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets


object AsyncBatchLogger {
    private var loggerJob = SupervisorJob()
    private var logScope = CoroutineScope(Dispatchers.IO + loggerJob)

    // 定义日志分类
    private const val TYPE_SYSTEM = "system"   // 系统/调试/异常日志
    private const val TYPE_BUSINESS = "biz"    // 业务操作日志
    private const val TYPE_SOCKET = "socket"     // Socket通讯

    // 数据包装类
    private data class LogEntry(val type: String, val content: String)

    private val logChannel = Channel<LogEntry>(capacity = 1000)

    private var lastId5LogTime = 0L
    private const val ID5_LOG_INTERVAL = 60 * 1000L

    init {
        startConsumer()
    }

    /**
     * 1. 存储普通系统/调试日志 (带 ID=5 降噪)
     */
    fun log(text: String, id: Int = -1) {
        if (logChannel.isClosedForSend) return
//        val currentTime = System.currentTimeMillis()
//        if (id == 5) {
//            if (currentTime - lastId5LogTime < ID5_LOG_INTERVAL) return
//            lastId5LogTime = currentTime
//        }
        enqueue(TYPE_SYSTEM, "[$id] $text")
    }

    /**
     * 2. 存储核心业务操作日志 (不降噪，每一条都记)
     * 例如：操作类型、成功标志
     */
    fun logBusiness(action: String, detail: String = "") {
        if (logChannel.isClosedForSend) return
        enqueue(TYPE_BUSINESS, "[$action]| $detail")
    }

    /**
     * 3. Socket 发送日志
     */
    fun logSocketWrite(data: String) {
        if (logChannel.isClosedForSend) return
        enqueue(TYPE_SOCKET, "[WRITE] -> $data")
    }

    /**
     * 4. Socket 接收日志
     */
    fun logSocketRead(data: String) {
        if (logChannel.isClosedForSend) return
        enqueue(TYPE_SOCKET, "[READ] <- $data")
    }

    private fun enqueue(type: String, content: String) {
        val time = AppUtils.getDateYMDHMS()
        logChannel.trySend(LogEntry(type, "[$time] $content\n"))
    }

    fun destroy() {
        logChannel.close()
        loggerJob.cancel()
        loggerJob = SupervisorJob()
        logScope = CoroutineScope(Dispatchers.IO + loggerJob)
    }

    private fun startConsumer() {
        logScope.launch {
            // 分类缓冲区，确保不同类型的日志互不干扰
            val systemBuffer = mutableListOf<String>()
            val businessBuffer = mutableListOf<String>()
            val socketBuffer = mutableListOf<String>()

            while (isActive) {
                try {
                    val entry = withTimeoutOrNull(5000) { logChannel.receive() }
                    if (entry != null) {
                        when (entry.type) {
                            TYPE_SYSTEM -> systemBuffer.add(entry.content)
                            TYPE_BUSINESS -> businessBuffer.add(entry.content)
                            TYPE_SOCKET -> socketBuffer.add(entry.content)
                        }

                        // 批量提取积压数据
                        var next = logChannel.tryReceive().getOrNull()
                        while (next != null) {
                            when (next.type) {
                                TYPE_SYSTEM -> systemBuffer.add(next.content)
                                TYPE_BUSINESS -> businessBuffer.add(next.content)
                                TYPE_SOCKET -> socketBuffer.add(next.content)
                            }
                            next = logChannel.tryReceive().getOrNull()
                        }
                    }

                    // 批量落盘逻辑
                    if (systemBuffer.isNotEmpty()) flushToDisk(TYPE_SYSTEM, systemBuffer).also { systemBuffer.clear() }
                    if (businessBuffer.isNotEmpty()) flushToDisk(TYPE_BUSINESS, businessBuffer).also { businessBuffer.clear() }
                    if (socketBuffer.isNotEmpty()) flushToDisk(TYPE_SOCKET, socketBuffer).also { socketBuffer.clear() }

                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun flushToDisk(type: String, lines: List<String>) {
        try {
            val rootPath = AppUtils.getContext().getExternalFilesDir("Download")?.absolutePath
            val folderName = when (type) {
                TYPE_SYSTEM -> "socket_box_crash"
                TYPE_BUSINESS -> "business_logs"
                else -> "socket_logs"
            }
            val path = "$rootPath/$folderName/"
            val dirs = File(path)
            if (!dirs.exists()) dirs.mkdirs()

            val prefix = when (type) {
                TYPE_SYSTEM -> "serial"
                TYPE_BUSINESS -> "biz"
                else -> "socket"
            }
            val fileName = "${prefix}---${AppUtils.getDateYMD()}.txt"

            FileOutputStream(File(path, fileName), true).buffered().use { bos ->
                lines.forEach { bos.write(it.toByteArray(StandardCharsets.UTF_8)) }
                bos.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}