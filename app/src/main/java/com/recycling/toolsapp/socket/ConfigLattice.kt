package com.recycling.toolsapp.socket

/**
 * 配置格口信息
 */
data class ConfigLattice(
    /***
     * 格口ID
     */
    var cabinId: String? = null,
    /***
     *
     * capacity 是当前箱体的超重状态值
     * 0正常
     * 1是红外遮挡
     * 2是重量达到（initConfig-箱体配置：overflow）满溢
     * 3是红外遮挡并且重量达到（initConfig：irOverflow）
     */
    var capacity: String? = null,
    /***
     * 构建时间
     */
    var createTime: String? = null,
    var delFlag: String? = null,
    //格口状态
    var doorStatus: String? = null,
    var filledTime: String? = null,
    var id: Int,
    /***
     * 红外 // 红外开关[0关闭 1开启]
     */
    var ir: Int,
    //最大称重60kg
    var overweight: String? = null,
    /***
     * 当前价格
     */
    var price: String? = null,
    /***
     * 推杆值
     */
    var rodHinderValue: Int,
    /***
     * 烟雾警报
     */
    var smoke: Int,
    /***
     * sn码
     */
    var sn: String? = null,
    var sort: Int,
    var sync: String? = null,
    /***
     * 音量大小
     */
    var volume: Int,
    /***
     * 当前重量
     */
    var weight: String? = null,
    var closeCount: Int? = 3,
    var weightPercent : Int? = 0

)