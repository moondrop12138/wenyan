package com.wenyan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * memory_fact DAO（v1.7.3 单条事实管理）：
 * 查询按 createdAt DESC, id DESC（最新在前）；FK CASCADE 随档案删除联动。
 */
@Dao
interface MemoryFactDao {
    /** 档案全部事实（时间倒序，最新在前） */
    @Query("SELECT * FROM memory_fact WHERE targetId = :targetId ORDER BY createdAt DESC, id DESC")
    fun observeByTarget(targetId: Long): Flow<List<MemoryFactEntity>>

    /** v1.7.3-fix 全表事实（设置页按 targetId 计数展示用） */
    @Query("SELECT * FROM memory_fact")
    fun observeAll(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_fact WHERE targetId = :targetId ORDER BY createdAt DESC, id DESC")
    suspend fun listByTarget(targetId: Long): List<MemoryFactEntity>

    /** L5: 单档案事实计数（COUNT 查询，替代全表拉取） */
    @Query("SELECT COUNT(*) FROM memory_fact WHERE targetId = :targetId")
    suspend fun countByTarget(targetId: Long): Int

    @Query("SELECT * FROM memory_fact WHERE id = :id")
    suspend fun getById(id: Long): MemoryFactEntity?

    @Insert
    suspend fun insert(entity: MemoryFactEntity): Long

    @Update
    suspend fun update(entity: MemoryFactEntity)

    @Query("DELETE FROM memory_fact WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memory_fact WHERE targetId = :targetId")
    suspend fun deleteByTarget(targetId: Long)

    @Query("DELETE FROM memory_fact")
    suspend fun clear()
}
