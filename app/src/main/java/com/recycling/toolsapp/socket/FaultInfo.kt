package com.recycling.toolsapp.socket

/***
 * 异常信息
 */
data class FaultInfo(
    /***
     * 异常类型
     * 1.投送门开门异常
     * 2.投递门关门异常
     * 3.清运门开门异常
     * 4.清运门关门异常
     * 5.摄像头异常
     * 6.电磁锁异常
     * 7:内灯异常
     * 8:外灯异常
     * 9:推杆异常
     */
    var type: Int? = null,
    /***
     * 统计它的时间
     */
    var desc: String? = null,
    /***
     * 舱门索引
     */
    var cabinIndex: Int? = null,
    /***
     * 舱门id
     */
    var cabinId: String? = null,

    )