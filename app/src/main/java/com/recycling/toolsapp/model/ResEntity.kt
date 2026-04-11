package com.recycling.toolsapp.model

import androidx.room.ColumnInfo
import androidx.room.ColumnInfo.Companion.INTEGER
import androidx.room.ColumnInfo.Companion.TEXT
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/***
 * 配置 图片资源 音频资源
 */
@Entity(tableName = "ResEntity") class ResEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id", typeAffinity = INTEGER) var id: Long = 0,
    /***
     * 指令
     */
    @ColumnInfo(name = "cmd", typeAffinity = TEXT, defaultValue = "") var cmd: String? = null,
    /***
     * 版本号
     */
    @ColumnInfo(name = "version", typeAffinity = TEXT, defaultValue = "") var version: String? = null,
    /***
     * 柜机sn
     */
    @ColumnInfo(name = "sn", typeAffinity = TEXT, defaultValue = "") var sn: String? = null,
    /***
     * 文件名称 类型
     */
    @ColumnInfo(name = "filename", typeAffinity = TEXT, defaultValue = "") var filename: String? = null,
    /***
     * 下载路径
     */
    @ColumnInfo(name = "url", typeAffinity = TEXT, defaultValue = "") var url: String? = null,
    /***
     * 当前md5
     */
    @ColumnInfo(name = "md5", typeAffinity = TEXT, defaultValue = "") var md5: String? = null,

    /***
     * 状态 -1. 1.需要刷新 0.不需要刷新 2.还未升级 3.升级完成 4.下载失败
     */
    @ColumnInfo(name = "status", typeAffinity = INTEGER) var status: Int = -1,
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
        -1,
        null,
    )

    override fun toString(): String {
        return "id=$id," + "cmd=${cmd}," + "version=${version}," + "sn=${sn}," + "filename=${filename}," + "url=${url}," + "md5=${md5}" + "status=${status}" + "time=${time}"
    }
}

