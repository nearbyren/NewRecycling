package com.recycling.toolsapps.socket


/***
 * 格口关闭
 */
data class DoorCloseBean(

    /***
     * 指令
     */
    var cmd: String? = null,
    /***
     * 事务id
     */
    var transId: String? = null,

    //服务下发
    /***
     * 状态 0.成功 1.失败
     */
    var retCode: Int,
    /***
     * 舱门信息
     */
    var info: DoorCloseInfo? = null,
    /***
     * 终端上发
     */
    var timestamp: String? = null,
    /***
     * 原始重量
     */
    var curWeightY: String? = null,
    /***
     * 物品上称后的体重【关键字段】
     */
    var curWeight: String? = null,
    /***
     * 上称物品的重量
     */
    var refWeight: String? = null,
    /***
     * 上称物品的重量【关键字段】
     */
    var changeWeight: String? = null,
    /***
     * 未上称物品前重量
     */
    var beforeUpWeight: String? = null,
    /***
     * 未上称物品后重量
     */
    var afterUpWeight: String? = null,
    /***
     * 上称物品前重量
     */
    var beforeDownWeight: String? = null,
    /***
     * 上称物品后重量
     */
    var afterDownWeight: String? = null,
    /***
     * 手机号
     */
    var phoneNumber: String? = null,
    /***
     * 格口ID
     */
    var cabinId: String? = null,
) {
    constructor() : this(null, null, 0, null, null, null,
        null, null, null, null, null, null, null, null)
}
