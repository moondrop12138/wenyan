package com.wenyan.app.data.repository

import com.wenyan.app.data.db.ProfileDao
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetDao
import com.wenyan.app.data.db.TargetEntity
import kotlinx.coroutines.flow.Flow

/**
 * 档案数据访问（F1 建档持久化，AC-03）
 * 供 PromptBuilder 读取 me/target 档案。
 */
class ProfileRepository(
    private val profileDao: ProfileDao,
    private val targetDao: TargetDao,
) {
    fun observeProfile(): Flow<ProfileEntity?> = profileDao.observeLatest()

    fun observeTarget(): Flow<TargetEntity?> = targetDao.observeLatest()

    suspend fun getProfile(): ProfileEntity? = profileDao.getLatest()

    suspend fun getTarget(): TargetEntity? = targetDao.getLatest()

    suspend fun saveProfile(entity: ProfileEntity): Long = profileDao.insert(entity)

    suspend fun saveTarget(entity: TargetEntity): Long = targetDao.insert(entity)

    suspend fun clearAll() {
        profileDao.clear()
        targetDao.clear()
    }
}
