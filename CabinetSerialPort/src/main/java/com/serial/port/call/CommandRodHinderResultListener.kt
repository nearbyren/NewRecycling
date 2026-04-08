package com.serial.port.call

/**
 *
 */
fun interface CommandRodHinderResultListener {
    /***
     * @param number 仓位
     * @param status 状态
     */
    fun setResult(number: Int, status: Int)

}