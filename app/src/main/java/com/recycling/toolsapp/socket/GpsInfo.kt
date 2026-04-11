package com.recycling.toolsapp.socket


/***
 * GPS信息
 */
data class GpsInfo(
    /***
     * 纬度
     */
    var latitude: String? = null,
    /***
     * 经度
     */
    var longitude: String? = null,
    /***
     * 状态
     */
    var state: Int? = null,
)
