package com.recycling.toolsapp.socket


/***
 * login和initConfig一致
 * 登录和初始化配置
 */
data class ConfigBean(
    /***
     * 指令
     */
    var cmd: String? = null,
    /***
     * 状态 0.成功 1.失败
     */
    var retCode: Int? = null,
    /***
     * sn码
     */
    var sn: String? = null,
    /***
     * 配置
     */
    var config: ConfigInfo,
)
