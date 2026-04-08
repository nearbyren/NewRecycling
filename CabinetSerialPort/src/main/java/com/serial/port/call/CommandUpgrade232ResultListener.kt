package com.serial.port.call

/**
 * 固件升级指令发送响应
 */
fun interface CommandUpgrade232ResultListener {
    /***
     * @param status 状态
     */
    fun upgradeResult(status: Int)

}