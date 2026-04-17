package com.recycling.toolsapp.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.recycling.toolsapp.model.ResEntity


/***
 *图片资源 音频资源dao
 */
@Dao
interface ResFlowDao {

    //key键重复的替换
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(resEntity: ResEntity): Long

    @Query("select * from ResEntity WHERE filename = :filename")
    fun queryResName(filename: String): ResEntity

    @Query("select * from ResEntity WHERE version = :version and sn = :sn and cmd = :cmd")
    fun queryResCmd(version: String, sn: String, cmd: String): ResEntity

    @Query("select * from ResEntity WHERE version = :version and cmd = :cmd")
    fun queryResCmd(version: String, cmd: String): ResEntity

    @Query("select * from ResEntity WHERE version = :version ")
    fun queryResVersion(version: String): ResEntity

    @Query("select * from ResEntity ORDER BY ROWID DESC LIMIT 1")
    fun queryResEntityMax(): ResEntity

    @Query("select * from ResEntity where id = (select MAX(id) from ResEntity where cmd  = :cmd)")
    fun queryResNewAPk(cmd: String): ResEntity

    @Query("select * from ResEntity WHERE id = (SELECT max(id) from ResEntity WHERE cmd = :cmd and sn = :sn)")
    fun queryResNewBin(sn: String, cmd: String): ResEntity

    @Update
    fun upResEntity(resEntity: ResEntity): Int

    @Query("UPDATE ResEntity SET status = :status WHERE id = :id")
    fun upResStatus(id: Long, status: Int)

    @Query("delete from ResEntity WHERE id = :id")
    fun deletedResEntity(id: Long)

    //删除所有数据
    @Query("delete from ResEntity")
    fun deleteAll()
}
