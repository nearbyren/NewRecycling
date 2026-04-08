package com.serial.port.call

/**
 * 下报大箱剥线结果
 */
fun interface CommandReportResultListener {
    /***
     * @param number 仓位
     * @param status 状态
     */
    fun reportResult(status: Int)

}