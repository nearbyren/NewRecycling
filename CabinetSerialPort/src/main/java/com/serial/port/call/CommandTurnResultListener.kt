package com.serial.port.call

/**
 *
 */
fun interface CommandTurnResultListener {
    /***
     * @param number 仓位
     * @param status 状态
     */
    fun openResult(number: Int, status: Int)

}