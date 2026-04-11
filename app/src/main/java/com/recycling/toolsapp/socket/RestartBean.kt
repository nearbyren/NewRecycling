package com.recycling.toolsapp.socket


/***
 * restart 重新启动app
 *
 */
data class RestartBean(
    /***
     * 指令
     */
    var cmd: String? = null,
    /***
     * 1.立即重启 2.定时重启
     */
    var type: Int? = null,

    /***
     * 时间
     */
    var time: String? = null,
)
