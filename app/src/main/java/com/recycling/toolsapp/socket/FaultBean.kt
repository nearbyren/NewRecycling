package com.recycling.toolsapp.socket


/***
 * fault 上传异常
 *
 */
data class FaultBean(
    /***
     * 指令
     */
    var cmd: String? = null,
    /***
     * 物联网卡imei
     */
    var imei: String? = null,
    /***
     * sn码
     */
    var sn: String? = null,
    /***
     * 异常信息
     */
    var data: FaultInfo? = null,

    /***
     * 时间戳
     */
    var timestamp: String? = null,

    ) {
    constructor() : this(null, null, null, null, null)
}

