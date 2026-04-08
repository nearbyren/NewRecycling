package com.recycling.toolsapps.socket


/***
 *
 */
data class AdminPhoto(
    /***
     * 0.移除
     * 1.添加
     */
    var actionType: Int = -1,
    /***
     * 事务id
     */
    var transId: String? = null,

    /***
     * [值为-1上传内外2张，值为4内摄像头，值为5外摄像头]
     */
    var photoType: Int = -1,
    /***
     * 仓门ID (多个仓门时，设备按仓门ID执行操作)
     */
    var cabinId: String? = null,
)
