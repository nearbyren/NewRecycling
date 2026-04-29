package com.recycling.toolsapp.socket

/***
 * 配置信息
 */
data class ConfigInfo(
    /***
     * 0-100的数字，
     * overflow箱子内重量达到这个数，就是超重满溢；
     * irOverflow红外感应的前提下，箱子内重量达到这个数，就是超重满溢
     */
    var overflow: Int,
    /***
     * 0-100的数字，
     * overflow箱子内重量达到这个数，就是超重满溢；
     * irOverflow红外感应的前提下，箱子内重量达到这个数，就是超重满溢
     */
    var irOverflow: Int,
    /***
     * 二维码地址
     */
    var qrCode: String? = null,
    /***
     * 调试密码
     */
    var debugPasswd: String? = null,
    /***
     * 心跳秒
     */
    var heartBeatInterval: String? = null,
    /***
     * 打开灯
     */
    var turnOnLight: String? = null,
    /***
     * 关闭灯
     */
    var turnOffLight: String? = null,
    /***
     * 光照时间
     */
    var lightTime: String? = null,
    /***
     * 箱子状态
     */
    var status: Int,
    /***
     * 图片上传路径
     */
    var uploadPhotoURL: String? = null,
    /***
     * 日志上传路径
     */
    var uploadLogURL: String? = null,
    /***
     * 日志级别
     */
    var logLevel: Int,
    /***
     * 红外默认状态
     */
    var irDefaultState: Int,
    /***
     * 红外默重量传感器模式认状态
     */
    var weightSensorMode: Int,
    /***
     * 格口信息
     */
    var list: List<ConfigLattice>? = null,
    /***
     * 图片音频资源
     */
    var resourceList: List<ConfigRes>? = null,

    /***
     * 屏幕是否显示总重量, [0显示, 1隐藏] (可选配置)
     */
    var hiddenTotalWeight: Int,
    /***
     * 投递过程中，屏幕是否显示重量信息, [0显示, 1隐藏] (可选配置)
     */
    var hiddenPostWeight: Int,
    /***
     * 清运过程中，屏幕是否显示重量信息, [0显示, 1隐藏] (可选配置)
     */
    var hiddenCleanWeight: Int,
    /***
     * 手机号投递, [0显示, 1隐藏] (可选配置)
     */
    var hiddenPhonePost: Int,

)