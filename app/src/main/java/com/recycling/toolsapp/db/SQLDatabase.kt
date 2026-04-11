package com.recycling.toolsapp.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.recycling.toolsapp.dao.ConfigFlowDao
import com.recycling.toolsapp.dao.FileFlowDao
import com.recycling.toolsapp.dao.LatticeFlowDao
import com.recycling.toolsapp.dao.LogFlowDao
import com.recycling.toolsapp.dao.ResFlowDao
import com.recycling.toolsapp.dao.StateFlowDao
import com.recycling.toolsapp.dao.TransFlowDao
import com.recycling.toolsapp.dao.WeightFlowDao
import com.recycling.toolsapp.model.ConfigEntity
import com.recycling.toolsapp.model.FileEntity
import com.recycling.toolsapp.model.LatticeEntity
import com.recycling.toolsapp.model.LogEntity
import com.recycling.toolsapp.model.ResEntity
import com.recycling.toolsapp.model.StateEntity
import com.recycling.toolsapp.model.TransEntity
import com.recycling.toolsapp.model.WeightEntity

@Database(entities = [
    LatticeEntity::class,
    ConfigEntity::class,
    StateEntity::class,
    TransEntity::class,
    WeightEntity::class,
    ResEntity::class,
    FileEntity::class,
    LogEntity::class], version = 12, exportSchema = false)
abstract class SQLDatabase : RoomDatabase() {

    ///日志操作
    abstract fun logFlow(): LogFlowDao

    ///箱体配置
    abstract fun latticeFlow(): LatticeFlowDao

    ///箱体配置
    abstract fun stateFlow(): StateFlowDao

    ///初始化配置
    abstract fun initConfigFlow(): ConfigFlowDao

    ///打开舱门
    abstract fun transFlowFlow(): TransFlowDao

    ///上报关闭
    abstract fun weightFlowDao(): WeightFlowDao

    ///资源
    abstract fun resFlowDao(): ResFlowDao

    ///文件
    abstract fun fileFlowDao(): FileFlowDao
}
