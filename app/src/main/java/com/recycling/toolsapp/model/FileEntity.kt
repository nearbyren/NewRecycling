package com.recycling.toolsapp.model

import androidx.room.ColumnInfo
import androidx.room.ColumnInfo.Companion.INTEGER
import androidx.room.ColumnInfo.Companion.TEXT
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/***
 * 文件信息
 */
@Entity(tableName = "FileEntity") class FileEntity(
    /***
     * 日志主键id
     */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id", typeAffinity = INTEGER) val id: Long = 0,

    /***
     * 指令
     */
    @ColumnInfo(name = "cmd", typeAffinity = TEXT, defaultValue = "") var cmd: String? = null,
    /***
     * 事务ID
     */
    @ColumnInfo(name = "transId", typeAffinity = TEXT, defaultValue = "") var transId: String? = null,
    /***
     * 照片内
     */
    @ColumnInfo(name = "photoIn", typeAffinity = TEXT, defaultValue = "") var photoIn: String? = null,
    /***
     * 照片外
     */
    @ColumnInfo(name = "photoOut", typeAffinity = TEXT, defaultValue = "") var photoOut: String? = null,
    /***
     * 消息
     */
    @ColumnInfo(name = "msg", typeAffinity = TEXT, defaultValue = "") var msg: String? = null,
    /***
     * 状态 0.未上传 1.已上传
     */
    @ColumnInfo(name = "status", typeAffinity = INTEGER) var status: Int = 0,
    /***
     * 时间
     */
    @ColumnInfo(name = "time", typeAffinity = TEXT, defaultValue = "") var time: String? = null,

    ) {
    @Ignore constructor() : this(0, null, null,null,null,null,0,null,)

    override fun toString(): String {
        return "id=$id,cmd=${cmd},transId=${transId},photoIn=${photoIn},photoOut=${photoOut},msg=${msg},status=${status},time=${time}"
    }
}

