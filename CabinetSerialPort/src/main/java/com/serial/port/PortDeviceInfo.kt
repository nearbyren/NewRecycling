package com.serial.port

data class PortDeviceInfo(
    /***
     * 当前重量
     */
    var weigh: String? = null,
    /***
     * 烟雾警报 0.无烟雾报警 1.有烟雾报警
     */
    var smoke: Int? = null,
    /***
     *
     * capacity 是当前箱体的超重状态值
     * 0正常
     * 1是红外遮挡
     * 2是重量达到（initConfig-箱体配置：overflow）满溢
     * 3是红外遮挡并且重量达到（initConfig：irOverflow）
     */
    var capacity: Int? = null,
    /***
     * 红外状态 0.无溢出 1.有溢出
     */
    var irState: Int? = null,
    /***
     * 关门感器状态
     */
    var touGMStatus: Int? = null,
    /***
     * 防夹手传感器。 0.无夹手 1.有夹手
     */
    var touJSStatus: Int? = null,
    /***
     * 投递门 0.关 1.开 2.开/关门中 3.故障
     */
    var doorStatus: Int? = null,
    /***
     * 清运门 0.关 1.开
     */
    var lockStatus: Int? = null,
    /***
     * 程序状态 校准 0.运行状态 1.校准状态 2.故障状态
     */
    var runStatus: Int? = null,
    /***
     * 是否夹手 0无夹手1夹到手
     */
    var jsStatus: Int? = null,
    /***
     * 格口ID
     */
    var cabinId: String? = null,
    )
