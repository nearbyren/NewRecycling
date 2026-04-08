package com.recycling.toolsapps.socket


/***
 * photo相册上传
 *
 */
data class PhotoBean(
    /***
     * 指令
     */
    var cmd: String? = null,
    /***
     * 事务id
     */
    var transId: String? = null,

    /***
     * [值为-1上传内外2张，值为4内摄像头，值为5外摄像头]
     */
    var photoType: Int,
    /***
     * 仓门ID (多个仓门时，设备按仓门ID执行操作)
     */
    var cabinId: String? = null,

)
