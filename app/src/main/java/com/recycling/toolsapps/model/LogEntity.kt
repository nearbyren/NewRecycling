package com.recycling.toolsapps.model

import androidx.room.ColumnInfo
import androidx.room.ColumnInfo.Companion.INTEGER
import androidx.room.ColumnInfo.Companion.TEXT
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/***
 * 日志信息
 */
@Entity(tableName = "LogEntity") class LogEntity(
    /***
     * 日志主键id
     */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id", typeAffinity = INTEGER) val id: Long = 0,

    /***
     * 指令
     */
    @ColumnInfo(name = "cmd", typeAffinity = TEXT, defaultValue = "") var cmd: String? = null,
    /***
     * 消息
     */
    @ColumnInfo(name = "msg", typeAffinity = TEXT, defaultValue = "") var msg: String? = null,
    /***
     * 时间
     */
    @ColumnInfo(name = "time", typeAffinity = TEXT, defaultValue = "") var time: String? = null,

    ) {
    @Ignore constructor() : this(0, null, null)

    override fun toString(): String {
        return "id=$id,cmd=${cmd},time=${time}"
    }
}

