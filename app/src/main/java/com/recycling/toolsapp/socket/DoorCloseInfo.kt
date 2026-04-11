package com.recycling.toolsapp.socket


/***
 * 服务器响应的
 */
data class DoorCloseInfo(
    var imei: String? = null,
    /***
     * sn编码
     */
    var sn: String? = null,
    /***
     * 舱门编号
     */
    var cabinIndex: String? = null,
    /***
     * 类型
     */
    var type: String? = null,
    /***
     * 经销商ID
     */
    var dealerID: String? = null,
    /***
     * 经销商名称
     */
    var dealerName: String? = null,
    /***
     * 手机号
     */
    var phoneNumber: String? = null,
    /***
     * 改变重量
     */
    var changeWeight: String? = null,
    /***
     * 经纬度信息
     */
    var gps: GpsInfo? = null,
)
