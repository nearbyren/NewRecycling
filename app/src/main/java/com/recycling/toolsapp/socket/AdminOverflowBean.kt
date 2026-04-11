package com.recycling.toolsapp.socket

/***
 * 管理下发满溢
 */
data class AdminOverflowBean(

    /***
     * 指令
     */
    var cmd: String? = null,
    /***
     * 事务id
     */
    var transId: String? = null,
    /***
     * 格口ID
     */
    var cabinId: String? = null,

    /***
     * 标明是请求指令
     */
    var type: String? = null,

    /***
     * 服务下发
     */
    /***
     * 是否启用设备计算满溢(0 关闭, 1开启)
     */
    var autoCalcOverflow: Int,

    /***
     * 满溢状态, (0 未满, 1满溢, 仅当autoCalcOverflow为0时有效)
     */
    var overflowState: Int,
    /***
     * 终端上发
     */
    /***
     * 状态 0.成功 1.失败
     */
    var retCode: Int,
) {
    constructor() : this(null, null, null, null, 0, 0, 0)
}
