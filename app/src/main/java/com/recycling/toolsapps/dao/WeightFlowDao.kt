package com.recycling.toolsapps.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.recycling.toolsapps.model.WeightEntity


/***
 *
 */
@Dao interface WeightFlowDao {

    //key键重复的替换
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(weightEntity: WeightEntity): Long

    //删除所有数据
    @Query("delete from WeightEntity") fun deleteAll()

    @Query("select * from WeightEntity WHERE transId = :transId and time = (select MAX(time) from WeightEntity where transId= :transId and status = 10)")
    fun queryWeightId(transId: String): WeightEntity

    @Query("select * from WeightEntity WHERE status = :status")
    fun queryWeightStatus(status: Int): List<WeightEntity>

    @Update fun upWeightEntity(weightEntity: WeightEntity): Int

    @Query("select * from WeightEntity ORDER BY ROWID DESC LIMIT 1")
    fun queryWeightMax(): WeightEntity

    @Query("UPDATE WeightEntity SET status = :status WHERE transId = :transId")
    fun upWeightStatus(status: Int, transId: String)

    @Query("select * from WeightEntity WHERE transId = :transId")
    fun queryWeightEntity(transId: String): WeightEntity
}
