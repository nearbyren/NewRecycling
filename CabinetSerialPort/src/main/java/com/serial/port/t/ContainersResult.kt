package com.serial.port.t

/**
 * @author: lr
 * @created on: 2026/3/22 上午11:59
 * @description:
 */

data class ContainersResult(
    /** 格口号 */
    var locker: Int = 1,
    /** 重量 */
    var weigh: String = "0.00",
    /** 烟雾传感器 */
    var smokeValue: Int = 0,
    /** 红外传感器 */
    var irStateValue: Int = 0,
    /** 关门传感器 */
    var touCGStatusValue: Int = -1,
    /** 防夹传感器 */
    var touJSStatusValue: Int = 0,
    /** 投口门状态 */
    var doorStatusValue: Int = -1,
    /** 清运门状态 */
    var lockStatusValue: Int = -1,
    /** 校准状态 */
    var xzStatusValue: Int = -1,
    /** 是否夹手 */
    var jsStatusValue: Int = -1,
)

