package com.recycling.toolsapp.socket

/**
 * 心跳包
 */
data class HeartBeatBean(
    /***
     * 指令
     */
    var cmd: String? = null,
    /***
     * 信号值
     */
    var signal: String? = null,
    /***
     * 经纬度信息
     */
    var gps: GpsInfo? = null,
    /***
     * 箱体状态
     */
    var stateList: List<HeartStateInfo>? = null,
    /***
     * 时间戳
     */
    var timestamp: String? = null,
)