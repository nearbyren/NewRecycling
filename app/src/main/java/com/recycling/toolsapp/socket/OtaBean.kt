package com.recycling.toolsapp.socket


/***
 * ota 固件升级 apk升级
 *
 */
data class OtaBean(
    /***
     * 指令
     */
    var cmd: String? = null,
    /***
     * 版本号
     */
    var version: String? = null,

    /***
     * 下载链接
     */
    var url: String? = null,
    /***
     * md5
     */
    var md5: String? = null,
    /***
     * sn码
     */
    var sn: String? = null,
)
