package com.recycling.toolsapp.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recycling.toolsapp.model.LogEntity
import kotlinx.coroutines.flow.Flow


/***
 * 日志dao
 */
@Dao interface LogFlowDao {
    //key键重复的替换
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(LogInfo: LogEntity): Long

    //结合flow监听数据变化，避免重复更新ui数据
    @Query("select * from LogEntity ORDER BY id DESC") fun queryLoginInfos(): Flow<List<LogEntity>>

    //删除所有数据
    @Query("delete from LogEntity") fun deleteAll()
}
