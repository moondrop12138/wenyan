package com.wenyan.app.data.repository

import com.wenyan.app.data.db.MemoryFactDao
import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.ProfileDao
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetDao
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.domain.MemoryExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 档案数据访问（F1 建档持久化，AC-03）
 * 供 PromptBuilder 读取 me/target 档案。
 * v1.7.2：target 单行 API → 多档案 API（observeTargets/getTarget(id)/saveTarget/updateTarget/deleteTarget）。
 * v1.7.3：新增 memory_fact 事实表 CRUD + note→facts 惰性搬移（幂等）：
 *   - note 代码层废弃不再写入，列保留防旧数据；
 *   - memoryText(targetId) = migrate 后 facts.joinToString("；").take(2000)，供注入链路。
 * 不持有 DataStore——激活档案 id 由调用方注入（最小侵入）。
 */
class ProfileRepository(
    private val profileDao: ProfileDao,
    private val targetDao: TargetDao,
    private val memoryFactDao: MemoryFactDao,
) {
    fun observeProfile(): Flow<ProfileEntity?> = profileDao.observeLatest()

    /** v1.7.2 全档案（id DESC，最新在前） */
    fun observeTargets(): Flow<List<TargetEntity>> = targetDao.observeAll()

    suspend fun getProfile(): ProfileEntity? = profileDao.getLatest()

    /** v1.7.2 按 id 取档案（会话归属注入用） */
    suspend fun getTarget(id: Long): TargetEntity? = targetDao.getById(id)

    suspend fun saveProfile(entity: ProfileEntity): Long = profileDao.insert(entity)

    suspend fun saveTarget(entity: TargetEntity): Long = targetDao.insert(entity)

    /** v1.7.2 改名/编辑正文（@Update 全字段覆盖） */
    suspend fun updateTarget(entity: TargetEntity) = targetDao.update(entity)

    /** v1.7.2 删除档案（删激活项后由调用方回退激活） */
    suspend fun deleteTarget(id: Long) = targetDao.deleteById(id)

    // ===== v1.7.3 单条事实管理 =====

    /** 档案全部事实（时间倒序，最新在前） */
    fun observeFacts(targetId: Long): Flow<List<MemoryFactEntity>> =
        memoryFactDao.observeByTarget(targetId)

    /** v1.7.3-fix 全档案事实计数（targetId → 条数），设置页档案行 caption 用；随事实增删响应式刷新 */
    fun observeFactCounts(): Flow<Map<Long, Int>> =
        memoryFactDao.observeAll().map { list -> list.groupingBy { it.targetId }.eachCount() }

    suspend fun getFacts(targetId: Long): List<MemoryFactEntity> =
        memoryFactDao.listByTarget(targetId)

    suspend fun addFact(targetId: Long, text: String): Long =
        memoryFactDao.insert(MemoryFactEntity(targetId = targetId, text = text.trim()))

    suspend fun updateFact(factId: Long, text: String) {
        val entity = memoryFactDao.getById(factId) ?: return
        memoryFactDao.update(entity.copy(text = text.trim()))
    }

    suspend fun deleteFact(factId: Long) = memoryFactDao.deleteById(factId)

    suspend fun countFacts(targetId: Long): Int = memoryFactDao.listByTarget(targetId).size

    /**
     * v1.7.3 惰性搬移（幂等）：老 note 数据首访时拆分为 facts 后清空 note。
     * - note 空 → 跳过；facts 非空（已搬移）→ 跳过（幂等）；
     * - 拆分 ≤50 条逐条插入；单条 ≤40 字（splitNoteToFacts 内截断）；
     * - 完成后清空 note（列保留防旧数据回滚）。
     * 调用方 runCatching 包一层，失败静默（不阻塞主流程）。
     */
    suspend fun migrateNoteToFactsOnce(targetId: Long) {
        val target = getTarget(targetId) ?: return
        if (target.note.isBlank()) return
        if (countFacts(targetId) > 0) return
        val segments = MemoryExtractor.splitNoteToFacts(target.note).take(MemoryExtractor.DEFAULT_FACT_LIMIT)
        segments.forEach { memoryFactDao.insert(MemoryFactEntity(targetId = targetId, text = it)) }
        updateTarget(target.copy(note = ""))
    }

    /**
     * v1.7.3 注入用记忆文本：确保惰性搬移完成后，facts.joinToString("；").take(2000)。
     * 与 PromptBuilder 契约一致（PromptBuilder 零改动：调用方以 target.copy(note = memoryText) 注入）。
     */
    suspend fun memoryText(targetId: Long): String {
        migrateNoteToFactsOnce(targetId)
        return memoryFactDao.listByTarget(targetId)
            .joinToString("；") { it.text }
            .take(2000)
    }

    suspend fun clearAll() {
        profileDao.clear()
        targetDao.clear()
        memoryFactDao.clear()
    }
}
