package com.recycling.toolsapps.socket


/***
 * 格口打开
 */
data class DoorOpenBean(

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
     * 服务下发
     */
    /***
     * 1.格口
     * 2.清运
     */
    var openType: Int,

    /***
     * 终端上发
     */
    /***
     * 状态 0.成功 1.失败
     */
    var retCode: Int,
    /***
     * 手机号
     */
    var phoneNumber: String? = null,
    /***
     * 用户ID
     */
    var userId: String? = null,
    /***
     * 当前重量
     */
    var curWeight: String? = null,
    /***
     * 当前重量原始
     */
    var curWeightY: String? = null,
    /***
     * 时间戳
     */
    var timestamp: String? = null,
    /***
     * 消息
     */
    var msg: String? = null,
) {
    constructor() : this(null, null, null, 0, 0, null, null, null, null, null, null)
}
