package com.serial.port.call

/**
 * 开仓指令发送响应
 */
fun interface CommandQueryResultListener {
    /***
     * @param number 仓位
     * @param status 状态
     * @param type 1.开启 0.查询
     */
    fun openResult(number: Int, status: Int, type: Int)

}