package com.recycling.toolsapp.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.recycling.toolsapp.model.TransEntity


/***
 * 交易单号dao
 */
@Dao interface TransFlowDao {

    //key键重复的替换
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(trensEntity: TransEntity): Long

    @Query("UPDATE TransEntity SET closeStatus = :closeStatus WHERE transId = :transId")
    fun upTransCloseStatus(closeStatus: Int, transId: String)

    @Query("UPDATE TransEntity SET openStatus = :openStatus WHERE transId = :transId")
    fun upTransOpenStatus(openStatus: Int, transId: String)


    @Query("select * from TransEntity WHERE closeStatus = :closeStatus")
    fun queryTransOpenStatus(closeStatus: Int): List<TransEntity>

    @Query("select * from TransEntity ORDER BY ROWID DESC LIMIT 1")
    fun queryTransMax(): TransEntity

    //删除所有数据
    @Query("delete from TransEntity") fun deleteAll()

    @Query("select * from TransEntity WHERE transId = :transId")
    fun queryTransEntity(transId: String): TransEntity

    @Update fun upTransEntity(transEntity: TransEntity): Int
}
