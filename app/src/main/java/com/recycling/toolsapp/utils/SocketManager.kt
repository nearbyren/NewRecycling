package com.recycling.toolsapp.utils

import com.recycling.toolsapp.socket.SocketClient

// SocketManager.kt
object SocketManager {
    private var _socketClient: SocketClient? = null
    val socketClient: SocketClient
        get() = _socketClient ?: throw IllegalStateException("SocketClient not initialized")
    
    fun initializeSocketClient(host: String, port: Int) {
        if (_socketClient == null) {
            _socketClient = SocketClient(
                SocketClient.Config(
                    host = host,
                    port = port,
                    heartbeatIntervalMillis = 20,
                    heartbeatPayload = "PING".toByteArray()
                )
            )
        }
    }
}
