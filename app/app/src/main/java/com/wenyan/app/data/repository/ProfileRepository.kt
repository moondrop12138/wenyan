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
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    /** L5: RoomDatabase（可选，注入后 migrate 走 withTransaction；测试注入 null 走直连） */
    private val database: androidx.room.RoomDatabase? = null,
) {
    /**
     * v1.7.4 搬移互斥锁：检查-插入-清空三步非原子，注入链与提炼链并发会重复插入；
     * 锁内重读保证幂等（第二个协程等锁后 note 已空 → 直接跳过）。
     * 搬移频率低（一次成功即清空 note），全局单锁足够。
     */
    private val migrateMutex = Mutex()
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

    /**
     * v1.7.4：悬空 targetId（档案已删）防御——memory_fact 表 FK 为 NO ACTION，
     * 竞态下 INSERT 会抛 SQLiteConstraintException（手工入口无 runCatching 会崩溃）；
     * 返回 0L 表示未插入。
     */
    suspend fun addFact(targetId: Long, text: String): Long =
        addFact(targetId, text, MemoryFactEntity.KIND_FACT)

    /** v1.9.0：带分层写入（fact/hypothesis），自动提炼的推断类事实走 hypothesis */
    suspend fun addFact(targetId: Long, text: String, kind: String): Long =
        addFact(targetId, text, kind, expiresAt = null, source = MemoryFactEntity.SOURCE_MANUAL)

    /**
     * v1.9.1：完整写入（kind + expiresAt 到期时间 + source 素材来源）。
     * 手工添加走 SOURCE_MANUAL/永久；自动提炼由 extractMemoryOnce 传入解析结果。
     */
    suspend fun addFact(
        targetId: Long,
        text: String,
        kind: String,
        expiresAt: Long?,
        source: String,
    ): Long {
        if (getTarget(targetId) == null) return 0L
        return memoryFactDao.insert(
            MemoryFactEntity(
                targetId = targetId,
                text = text.trim(),
                kind = if (kind == MemoryFactEntity.KIND_HYPOTHESIS) kind else MemoryFactEntity.KIND_FACT,
                expiresAt = expiresAt,
                source = source,
            ),
        )
    }

    suspend fun updateFact(factId: Long, text: String) {
        val entity = memoryFactDao.getById(factId) ?: return
        memoryFactDao.update(entity.copy(text = text.trim()))
    }

    /** v1.9.1 临时事实转永久（清空到期时间）；不存在则忽略 */
    suspend fun makePermanent(factId: Long) {
        val entity = memoryFactDao.getById(factId) ?: return
        if (entity.expiresAt != null) {
            memoryFactDao.update(entity.copy(expiresAt = null))
        }
    }

    suspend fun deleteFact(factId: Long) = memoryFactDao.deleteById(factId)

    suspend fun countFacts(targetId: Long): Int = memoryFactDao.countByTarget(targetId)

    /**
     * v1.7.4 惰性搬移（幂等 + 并发安全）：老 note 数据首访时拆分为 facts 后清空 note。
     * - note 空 → 跳过；锁内重读 target/facts（防并发重复）
     * - **merge 语义**（BUG-1 修复）：不再因「已有 facts」跳过——老 note 与现有 facts 去重合并，
     *   仅插 delta（用户升级后先手工加过事实，老 note 内容也不会丢失）
     * - 拆分 ≤50 条逐条插入；单条 ≤40 字（splitNoteToFacts 内截断）；
     * - 完成后定向清空 note（targetDao.clearNote，只动 note 列）
     * 调用方 runCatching 包一层，失败静默（不阻塞主流程）。
     */
    suspend fun migrateNoteToFactsOnce(targetId: Long) {
        migrateMutex.withLock {
            // L5: 多次 INSERT + 清 note 包进事务（database 可用时）；测试无数据库则直连
            val db = database
            if (db != null) db.withTransaction { doMigrate(targetId) } else doMigrate(targetId)
        }
    }

    private suspend fun doMigrate(targetId: Long) {
        val target = getTarget(targetId) ?: return
        if (target.note.isBlank()) return
        val existing = getFacts(targetId).map { it.text }
        val segments = MemoryExtractor.splitNoteToFacts(target.note).take(MemoryExtractor.DEFAULT_FACT_LIMIT)
        // L2 修复：以清洗空白后的数量 drop（同 RealChatRepository.persistFactsOnce）
        val toAdd = MemoryExtractor.mergeFacts(existing, segments).drop(existing.count { it.isNotBlank() })
        toAdd.forEach { memoryFactDao.insert(MemoryFactEntity(targetId = targetId, text = it)) }
        targetDao.clearNote(targetId)
    }

    /**
     * v1.7.3 注入用记忆文本：确保惰性搬移完成后，facts.joinToString("；").take(2000)。
     * v1.9.0：hypothesis（模型推断）条目带「（推测，待验证）」标注注入，与事实区分。
     * v1.9.1：expiresAt 已到期的条目过滤不注入（惰性，不物理删）；transcription（截图转述）来源
     * 条目带「（来自截图转述）」标注（转述可能有误差，提示模型不可全信）。
     * 与 PromptBuilder 契约一致（PromptBuilder 零改动：调用方以 target.copy(note = memoryText) 注入）。
     */
    suspend fun memoryText(targetId: Long): String {
        migrateNoteToFactsOnce(targetId)
        val now = System.currentTimeMillis()
        return memoryFactDao.listByTarget(targetId)
            .filter { fact -> val expires = fact.expiresAt; expires == null || expires > now }
            .joinToString("；") { fact ->
                val annotation = when {
                    fact.kind == MemoryFactEntity.KIND_HYPOTHESIS -> "（推测，待验证）"
                    fact.source == MemoryFactEntity.SOURCE_TRANSCRIPTION -> "（来自截图转述）"
                    else -> ""
                }
                if (annotation.isEmpty()) fact.text else "${fact.text}$annotation"
            }
            .take(2000)
    }

    suspend fun clearAll() {
        profileDao.clear()
        targetDao.clear()
        memoryFactDao.clear()
    }
}
