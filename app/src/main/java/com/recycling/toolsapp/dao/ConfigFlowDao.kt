package com.recycling.toolsapp.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.recycling.toolsapp.model.ConfigEntity


/***
 * 配置dao
 */
@Dao interface ConfigFlowDao {

    //key键重复的替换
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(configEntity: ConfigEntity): Long

    @Query("select * from ConfigEntity WHERE sn = :sn")
    fun queryInitConfig(sn: String): ConfigEntity

    @Update fun upConfigEntity(configEntity: ConfigEntity): Int

    //删除所有数据
    @Query("delete from ConfigEntity") fun deleteAll()
}
