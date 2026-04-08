package com.recycling.toolsapps.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.recycling.toolsapps.dao.ConfigFlowDao
import com.recycling.toolsapps.dao.FileFlowDao
import com.recycling.toolsapps.dao.LatticeFlowDao
import com.recycling.toolsapps.dao.LogFlowDao
import com.recycling.toolsapps.dao.ResFlowDao
import com.recycling.toolsapps.dao.StateFlowDao
import com.recycling.toolsapps.dao.TransFlowDao
import com.recycling.toolsapps.dao.WeightFlowDao
import com.recycling.toolsapps.model.ConfigEntity
import com.recycling.toolsapps.model.FileEntity
import com.recycling.toolsapps.model.LatticeEntity
import com.recycling.toolsapps.model.LogEntity
import com.recycling.toolsapps.model.ResEntity
import com.recycling.toolsapps.model.StateEntity
import com.recycling.toolsapps.model.TransEntity
import com.recycling.toolsapps.model.WeightEntity

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
