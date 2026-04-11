package com.recycling.toolsapp.socket


/***
 * 配置 图片资源 音频资源
 */
data class ConfigRes(
    /***
     * 文件类型
     */
    var filename: String? = null,
    /***
     * 下载路径
     */
    var url: String? = null,
    /***
     * md5不同则下载
     */
    var md5: String? = null,
)
