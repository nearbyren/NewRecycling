package com.serial.port.t

/**
 * @author: lr
 * @created on: 2026/3/22 上午11:59
 * @description:
 */

data class ContainersResult(
  var locker: Int= 0,     // 格口号
  var weigh: String = "",    // 重量
  var smokeValue: Int = 1,    // 烟雾传感器
  var irStateValue: Int = 1,   // 红外传感器
  var touCGStatusValue: Int = 1,   // 关门传感器
  var touJSStatusValue: Int = 0,   // 防夹传感器
  var doorStatusValue: Int = -1,   // 投口门状态
  var lockStatusValue: Int = -1,   // 清运门状态
  var xzStatusValue: Int = -1,   // 校准状态
  var jsStatusValue: Int = -1,   // 是否夹手
)

