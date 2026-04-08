package com.serial.port.call

/**
 * 灯光指令发送响应
 */
fun interface CommandLightsResultListener {
    /***
     * @param number 1内部灯 2外部灯
     * @param status 1打开 2关闭
     */
    fun lightsResult(number: Int, status: Int)

}