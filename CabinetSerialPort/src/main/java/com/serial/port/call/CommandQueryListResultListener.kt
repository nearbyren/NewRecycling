package com.serial.port.call

import com.serial.port.PortDeviceInfo

/**
 * 查询格口设备状态
 */
fun interface CommandQueryListResultListener {

    /***
     * @param lockerInfos 查询所有格口设备状态
     */
    fun queryResult(lockerInfos: MutableList<PortDeviceInfo>)
}