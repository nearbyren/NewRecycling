package com.recycling.toolsapps.model

import androidx.room.ColumnInfo
import androidx.room.ColumnInfo.Companion.INTEGER
import androidx.room.ColumnInfo.Companion.TEXT
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/***
 * 配置信息
 */
@Entity(tableName = "ConfigEntity") class ConfigEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id", typeAffinity = INTEGER) val id: Long = 0,
    /***
     * sn码
     */
    @ColumnInfo(name = "sn", typeAffinity = TEXT, defaultValue = "") var sn: String? = null,
    /***
     * 心跳秒
     */
    @ColumnInfo(name = "heartBeatInterval", typeAffinity = TEXT, defaultValue = "") var heartBeatInterval: String? = null,
    /***
     *打开灯
     */
    @ColumnInfo(name = "turnOnLight", typeAffinity = TEXT, defaultValue = "") var turnOnLight: String? = null,
    /***
     * 关闭灯
     */
    @ColumnInfo(name = "turnOffLight", typeAffinity = TEXT, defaultValue = "") var turnOffLight: String? = null,
    /***
     *
     */
    @ColumnInfo(name = "lightTime", typeAffinity = TEXT, defaultValue = "") var lightTime: String? = null,
    /***
     * 上传照片路径
     */
    @ColumnInfo(name = "uploadPhotoURL", typeAffinity = TEXT, defaultValue = "") var uploadPhotoURL: String? = null,
    /**
     * 上传日志路径
     */
    @ColumnInfo(name = "uploadLogURL", typeAffinity = TEXT, defaultValue = "") var uploadLogURL: String? = null,
    /***
     * 二维码
     */
    @ColumnInfo(name = "qrCode", typeAffinity = TEXT, defaultValue = "") var qrCode: String? = null,
    /***
     * 满溢 服务器下发的配置
     */
    @ColumnInfo(name = "overflow", typeAffinity = INTEGER) var overflow: Int = 0,
    /***
     * 红外达到的满溢
     */
    @ColumnInfo(name = "irOverflow", typeAffinity = INTEGER) var irOverflow: Int = 0,
    /***
     * 日志等级
     */
    @ColumnInfo(name = "logLevel", typeAffinity = INTEGER) var logLevel: Int = 0,
    /***
     * 状态
     */
    @ColumnInfo(name = "status", typeAffinity = INTEGER) var status: Int = 0,
    /***
     * debug密码
     */
    @ColumnInfo(name = "debugPasswd", typeAffinity = TEXT, defaultValue = "") var debugPasswd: String? = null,
    /***
     * 红外状态
     */
    @ColumnInfo(name = "irDefaultState",  typeAffinity = INTEGER) var  irDefaultState: Int = 0,
    /***
     * 重量传感器模式
     */
    @ColumnInfo(name = "weightSensorMode",  typeAffinity = INTEGER) var  weightSensorMode: Int = 0,

    /***
     * 创建时间
     */
    @ColumnInfo(name = "time", typeAffinity = TEXT) var time: String? = null,
) {
    @Ignore constructor() : this(
        0,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        0,
        0,
        0,
        null,
        0,
        0,
        null,
        )

    override fun toString(): String {
        return "id=$id," +
                "sn=${sn}," +
                "heartBeatInterval=${heartBeatInterval}," +
                "turnOnLight=${turnOnLight}," +
                "turnOffLight=${turnOffLight}," +
                "lightTime=${lightTime}," +
                "uploadPhotoURL=${uploadPhotoURL}," +
                "uploadLogURL=${uploadLogURL}," +
                "qrCode=${qrCode}," +
                "overflow=${overflow}," +
                "irOverflow=${irOverflow}," +
                "logLevel=${logLevel}," +
                "status=${status}," +
                "debugPasswd=${debugPasswd}," +
                "irDefaultState=${irDefaultState}," +
                "weightSensorMode=${weightSensorMode}"+
                "time=${time}"
    }
}

