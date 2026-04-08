package com.serial.port.call

/**
 * 效验发送文件是否准确
 */
fun interface CommandUpgradeXYResultListener {
    /***
     * @param bytes 回调的文件byte
     */
    fun upgradeResult(bytes: ByteArray)

}