package com.recycling.toolsapps.model

import androidx.room.ColumnInfo
import androidx.room.ColumnInfo.Companion.INTEGER
import androidx.room.ColumnInfo.Companion.TEXT
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/***
 * 格口信息
 */
@Entity(tableName = "LatticeEntity") class LatticeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id", typeAffinity = INTEGER) val id: Long = 0,
    /***
     * 格口id
     */
    @ColumnInfo(name = "cabinId", typeAffinity = TEXT, defaultValue = "") var cabinId: String? = null,
    /***
     *
     * capacity 是当前箱体的超重状态值
     * 0正常
     * 1是红外遮挡
     * 2是重量达到（initConfig-箱体配置：overflow）满溢
     * 3是红外遮挡并且重量达到（initConfig：irOverflow）
     */
    @ColumnInfo(name = "capacity", typeAffinity = TEXT, defaultValue = "") var capacity: String? = null,
    /***
     * 创建时间
     */
    @ColumnInfo(name = "createTime", typeAffinity = TEXT, defaultValue = "") var createTime: String? = null,
    /***
     *删除flag
     */
    @ColumnInfo(name = "delFlag", typeAffinity = TEXT, defaultValue = "") var delFlag: String? = null,
    /***
     * 门状态
     */
    @ColumnInfo(name = "doorStatus", typeAffinity = TEXT, defaultValue = "") var doorStatus: String? = null,
    /***
     *填充时间
     */
    @ColumnInfo(name = "filledTime", typeAffinity = TEXT, defaultValue = "") var filledTime: String? = null,
    /***
     *网络ID
     */
    @ColumnInfo(name = "netId", typeAffinity = INTEGER) var netId: Int = 0,
    /***
     *红外
     */
    @ColumnInfo(name = "ir", typeAffinity = INTEGER) var ir: Int = 0,
    /***
     *超重
     */
    @ColumnInfo(name = "overweight", typeAffinity = TEXT, defaultValue = "") var overweight: String? = null,
    /***
     *价格
     */
    @ColumnInfo(name = "price", typeAffinity = TEXT, defaultValue = "") var price: String? = null,
    /***
     *挡杆的值
     */
    @ColumnInfo(name = "rodHinderValue",  typeAffinity = INTEGER) var rodHinderValue: Int = 0,
    /***
     *sn码
     */
    @ColumnInfo(name = "sn", typeAffinity = TEXT, defaultValue = "") var sn: String? = null,
    /***
     *烟雾警报
     */
    @ColumnInfo(name = "smoke", typeAffinity = INTEGER) var smoke: Int = 0,
    /***
     *排序
     */
    @ColumnInfo(name = "sort", typeAffinity = INTEGER) var sort: Int = 0,
    /***
     *同步状态  0 1
     */
    @ColumnInfo(name = "sync", typeAffinity = TEXT, defaultValue = "") var sync: String? = null,
    /***
     *音量大小
     */
    @ColumnInfo(name = "volume", typeAffinity = INTEGER) var volume: Int = 0,
    /***
     *强制关闭仓门
     */
    @ColumnInfo(name = "closeCount", typeAffinity = INTEGER) var closeCount: Int = 5,
    /***
     *读取重量再次换算
     */
    @ColumnInfo(name = "weightPercent", typeAffinity = INTEGER) var weightPercent: Int = 0,
    /***
     *重量
     */
    @ColumnInfo(name = "weight", typeAffinity = TEXT, defaultValue = "") var weight: String? = null,
    /***
     *时刻检测的总重量
     */
    @ColumnInfo(name = "weightMonitor", typeAffinity = TEXT, defaultValue = "") var weightMonitor: String? = null,
    /***
     *默认的总重量
     */
    @ColumnInfo(name = "weightDefault", typeAffinity = TEXT, defaultValue = "100.00") var weightDefault: String? = null,
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
        0,
        0,
        null,
        null,
        0,
        null,
        0,
        0,
        null,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        )

    override fun toString(): String {
        return "id=$id," + "cabinId=${cabinId}," + "capacity=${capacity}," + "createTime=${createTime}," + "delFlag=${delFlag}," + "doorStatus=${doorStatus}," + "filledTime=${filledTime}," + "netId=${netId}," + "ir=${ir}," + "overweight=${overweight}," + "overweight=${overweight}," + "price=${price}," + "rodHinderValue=${rodHinderValue}," + "sn=${sn}," + "smoke=${smoke}," + "sort=${sort}," + "sync=${sync}," + "volume=${volume},"+ "closeCount=${closeCount},"+ "weightPercent=${weightPercent}," + "weight=${weight}," + "weightMonitor=${weightMonitor}," + "weightDefault=${weightDefault}"
    }
}

