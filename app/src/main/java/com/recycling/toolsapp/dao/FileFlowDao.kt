package com.recycling.toolsapp.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.recycling.toolsapp.model.FileEntity


/***
 *
 */
@Dao interface FileFlowDao {

    //key键重复的替换
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(fileEntity: FileEntity): Long
    //删除所有数据
    @Query("delete from FileEntity") fun deleteAll()
    //删除所有数据
    @Query("delete from FileEntity WHERE status = 1 ") fun deleteAll1()
    //删除所有数据
    @Query("delete from FileEntity WHERE status = 0 ") fun deleteAll0()

    @Query("delete from FileEntity WHERE id = :id")
    fun deletedFileEntity(id: Long)

    @Query("select * from FileEntity WHERE cmd = :cmd and transId = :transId")
    fun queryFileEntity(cmd: String, transId: String): FileEntity

    @Query("select * from FileEntity WHERE transId = :transId and status = 0")
    fun queryFileEntitys(transId: String): List<FileEntity>

    @Query("select * from FileEntity WHERE status = -1")
    fun queryAllFileEntity(): List<FileEntity>

    @Query("select * from FileEntity WHERE status = 1")
    fun queryAllFileStatus1(): List<FileEntity>

    @Update fun upFileEntity(fileEntity: FileEntity): Int

    @Query("UPDATE FileEntity SET status = 1 WHERE  cmd = :cmd and transId = :transId ")
    fun upFileStatus(cmd: String, transId: String)

}
