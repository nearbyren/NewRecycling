package com.serial.port.call

/**
 * 实时查询门状态
 */
fun interface CommandDoorResultListener {
    /***
     * @param status 状态
     * 1.门开
     * 0.门关
     */
    fun openResult(status: Int)

}