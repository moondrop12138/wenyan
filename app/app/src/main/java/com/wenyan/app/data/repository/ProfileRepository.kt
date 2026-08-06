package com.wenyan.app.data.repository

import com.wenyan.app.data.db.ProfileDao
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetDao
import com.wenyan.app.data.db.TargetEntity
import kotlinx.coroutines.flow.Flow

/**
 * 档案数据访问（F1 建档持久化，AC-03）
 * 供 PromptBuilder 读取 me/target 档案。
 * v1.7.2：target 单行 API → 多档案 API（observeTargets/getTarget(id)/saveTarget/updateTarget/deleteTarget）。
 * 不持有 DataStore——激活档案 id 由调用方注入（最小侵入）。
 */
class ProfileRepository(
    private val profileDao: ProfileDao,
    private val targetDao: TargetDao,
) {
    fun observeProfile(): Flow<ProfileEntity?> = profileDao.observeLatest()

    /** v1.7.2 全档案（id DESC，最新在前） */
    fun observeTargets(): Flow<List<TargetEntity>> = targetDao.observeAll()

    suspend fun getProfile(): ProfileEntity? = profileDao.getLatest()

    /** v1.7.2 按 id 取档案（会话归属注入用） */
    suspend fun getTarget(id: Long): TargetEntity? = targetDao.getById(id)

    suspend fun saveProfile(entity: ProfileEntity): Long = profileDao.insert(entity)

    suspend fun saveTarget(entity: TargetEntity): Long = targetDao.insert(entity)

    /** v1.7.2 改名/编辑正文 */
    suspend fun updateTarget(entity: TargetEntity) = targetDao.update(entity)

    /** v1.7.2 删除档案（删激活项后由调用方回退激活） */
    suspend fun deleteTarget(id: Long) = targetDao.deleteById(id)

    suspend fun clearAll() {
        profileDao.clear()
        targetDao.clear()
    }
}
