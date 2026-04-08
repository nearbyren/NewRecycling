package com.serial.port.call

/**
 * 发送指令响应
 */
fun interface CommandSendResultListener {
    /***
     * @param msg 消息
     */
    fun sendResult(msg: String)

}