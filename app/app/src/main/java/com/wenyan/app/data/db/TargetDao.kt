package com.wenyan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * target DAO（v1.7.2 多档案：全量 CRUD，id DESC 最新在前）
 * 删除：getLatest / observeLatest（唯一调用方 ProfileRepository 已同步改造）
 */
@Dao
interface TargetDao {
    /** v1.7.2 全档案（id DESC，最新在前；删激活项回退取第一条） */
    @Query("SELECT * FROM target ORDER BY id DESC")
    fun observeAll(): Flow<List<TargetEntity>>

    /** v1.7.2 按 id 取单个档案（会话归属注入用） */
    @Query("SELECT * FROM target WHERE id = :id")
    suspend fun getById(id: Long): TargetEntity?

    @Insert
    suspend fun insert(entity: TargetEntity): Long

    /** v1.7.2 改名/编辑正文（updateTarget） */
    @Update
    suspend fun update(entity: TargetEntity)

    /** v1.7.2 删除单个档案（删除前需二次确认） */
    @Query("DELETE FROM target WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM target")
    suspend fun clear()
}
