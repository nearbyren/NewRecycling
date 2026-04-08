package com.recycling.toolsapps.model

import androidx.room.ColumnInfo
import androidx.room.ColumnInfo.Companion.INTEGER
import androidx.room.ColumnInfo.Companion.REAL
import androidx.room.ColumnInfo.Companion.TEXT
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/***
 * 心跳上报的状态
 */
@Entity(tableName = "StateEntity") class StateEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id", typeAffinity = INTEGER) val id: Long = 0,
    /***
     * 烟雾警报状态 0.无烟雾报警 1.有烟雾报警
     */
    @ColumnInfo(name = "smoke", typeAffinity = INTEGER) var smoke: Int = 0,
    /***
     *
     * capacity 是当前箱体的超重状态值
     * 0正常
     * 1是红外遮挡
     * 2是重量达到（initConfig-箱体配置：overflow）满溢
     * 3是红外遮挡并且重量达到（initConfig：irOverflow）
     */
    @ColumnInfo(name = "capacity", typeAffinity = INTEGER) var capacity: Int = 0,
    /***
     * 红外状态  0.无溢出 1.有溢出
     */
    @ColumnInfo(name = "irState", typeAffinity = INTEGER) var irState: Int = 0,
    /***
     * 当前重量
     */
    // 修改这里：添加 defaultValue
    @ColumnInfo(name = "weighY", typeAffinity = ColumnInfo.REAL, defaultValue = "0.0")
    var weighY: Float = 0f,
    /***
     * 当前重量
     */
    @ColumnInfo(name = "weigh", typeAffinity = REAL) var weigh: Float = 0f,
    /**
     * 投口门 0.关 1.开 2.开/关门中 3.故障
     */
    @ColumnInfo(name = "doorStatus", typeAffinity = INTEGER) var doorStatus: Int = 0,
    /**
     * 清运锁 清运门 0.关 1.开 2.故障
     */
    @ColumnInfo(name = "lockStatus", typeAffinity = INTEGER) var lockStatus: Int = 0,
    /***
     * 舱门编码
     */
    @ColumnInfo(name = "cabinId", typeAffinity = TEXT, defaultValue = "") var cabinId: String? = null,
    /***
     * 创建时间
     */
    @ColumnInfo(name = "time", typeAffinity = TEXT, defaultValue = "") var time: String? = null,
    ) {
    @Ignore constructor() : this(
        0,
        0,
        0,
        0,
        0f,
        0f,
        0,
        0,
        null,
        null,
        )

    override fun toString(): String {
        return "id=$id," + "smoke=${smoke}," + "capacity=${capacity}," + "irState=${irState},"+ "weighY=${weighY}," + "weigh=${weigh}," + "doorStatus=${doorStatus},"+ "lockStatus=${lockStatus}," + "cabinId=${cabinId}," + "time=${time}"
    }
}

