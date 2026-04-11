package com.recycling.toolsapp.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.recycling.toolsapp.model.LatticeEntity


/***
 * 格口操作dao
 */
@Dao interface LatticeFlowDao {

    @Query("select * from LatticeEntity") fun queryLattices(): List<LatticeEntity>
    //key键重复的替换
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(latticeEntity: LatticeEntity): Long

    @Query("select * from LatticeEntity WHERE cabinId = :cabinId")
    fun queryLatticeEntity(cabinId: String): LatticeEntity

    @Update fun upLatticeEntity(latticeEntity: LatticeEntity): Int

    //删除所有数据
    @Query("delete from LatticeEntity") fun deleteAll()
}
