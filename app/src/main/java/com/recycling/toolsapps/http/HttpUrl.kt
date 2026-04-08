package com.recycling.toolsapps.http

import android.content.Context
import android.net.wifi.WifiManager
import com.serial.port.utils.AppUtils

object HttpUrl {

    /***
     * 上传照片
     */
    const val uploadPhoto = "device/upload/photo"

    /***
     * 上传日志
     */
    const val uploadLog = "device/upload/log"

    /***
     * 发行设备
     */
    const val issueDevice = "web/deviceIssue/initDevice"

    /***
     * 获取设备连接地址
     */
    const val connectAddress = "web/deviceIssue/connectAddress"


    /***
     * 获取mac地址
     */
    fun getMaxAddress(): String {
        val wifiManager =
            AppUtils.getContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val macAddress = wifiInfo.macAddress
        return macAddress
    }

}