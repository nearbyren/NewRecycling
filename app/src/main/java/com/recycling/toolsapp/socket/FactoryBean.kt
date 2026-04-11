package com.recycling.toolsapp.socket

/**
 * 出厂格口数据
 */
data class FactoryBean(
    /***
     * 红外
     */
    var ir: Int,
    /***
     * 音量
     */
    var volume: Int,

    /***
     * 推杆值
     */
    var rodHinderValue: Int,
){
    constructor() : this(0,0,500)
}
