package com.recycling.toolsapps.socket

/**
 *
 */
fun interface SocketResultListener {

    fun caliResult(state: SocketClient.ConnectionState)

}