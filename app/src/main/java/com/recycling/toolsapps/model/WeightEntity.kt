package com.recycling.toolsapps.model

import androidx.room.ColumnInfo
import androidx.room.ColumnInfo.Companion.INTEGER
import androidx.room.ColumnInfo.Companion.TEXT
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/***
 * 上传关门dao
 */
@Entity(tableName = "WeightEntity") class WeightEntity(
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
     * 1.格口
     * 2.清运
     */
    @ColumnInfo(name = "openType", typeAffinity = INTEGER) var openType: Int = 0,
    /***
     * 仓门编码
     */
    @ColumnInfo(name = "cabinId", typeAffinity = TEXT, defaultValue = "") var cabinId: String? = null,
    /***
     * 物品上称后的体重【关键字段】
     */
    @ColumnInfo(name = "curWeightY", typeAffinity = TEXT, defaultValue = "") var curWeightY: String? = null,
    /***
     * 物品上称后的体重【关键字段】
     */
    @ColumnInfo(name = "curWeight", typeAffinity = TEXT, defaultValue = "") var curWeight: String? = null,
    /***
     * 上称物品的重量
     */
    @ColumnInfo(name = "refWeight", typeAffinity = TEXT, defaultValue = "") var refWeight: String? = null,
    /***
     * 上称物品的重量【关键字段】
     */
    @ColumnInfo(name = "changeWeight", typeAffinity = TEXT, defaultValue = "") var changeWeight: String? = null,
    /***
     * 未上称物品前重量
     */
    @ColumnInfo(name = "beforeUpWeight", typeAffinity = TEXT, defaultValue = "") var beforeUpWeight: String? = null,
    /***
     * 未上称物品后重量
     */
    @ColumnInfo(name = "afterUpWeight", typeAffinity = TEXT, defaultValue = "") var afterUpWeight: String? = null,
    /***
     * 上称物品前重量
     */
    @ColumnInfo(name = "beforeDownWeight", typeAffinity = TEXT, defaultValue = "") var beforeDownWeight: String? = null,
    /***
     * 上称物品后重量
     */
    @ColumnInfo(name = "afterDownWeight", typeAffinity = TEXT, defaultValue = "") var afterDownWeight: String? = null,

    /***
     * 标记是否完成状态
     * 10.进行中 1.完成
     */
    @ColumnInfo(name = "status", typeAffinity = INTEGER) var status: Int = -1,
    /***
     * 创建时间
     */
    @ColumnInfo(name = "time", typeAffinity = TEXT) var time: String? = null, ) {
    @Ignore constructor() : this(
        0,
        null,
        null,
        0,
        null,
        null,
        null,
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
        return "id=$id," +
                "cmd=${cmd}," +
                "transId=${transId}," +
                "openType=${openType}," +
                "cabinId=${cabinId}," +
                "curWeightY=${curWeightY}," +
                "curWeight=${curWeight}," +
                "changeWeight=${changeWeight}" +
                "refWeight=${refWeight}" +
                "beforeUpWeight=${beforeUpWeight}" +
                "afterUpWeight=${afterUpWeight}" +
                "beforeDownWeight=${beforeDownWeight}" +
                "afterDownWeight=${afterDownWeight}" +
                "status=${status}" +
                "time=${time}"
    }
}

