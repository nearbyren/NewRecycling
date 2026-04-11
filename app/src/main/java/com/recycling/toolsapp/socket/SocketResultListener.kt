package com.recycling.toolsapp.socket

/**
 *
 */
fun interface SocketResultListener {

    fun caliResult(state: SocketClient.ConnectionState)

}