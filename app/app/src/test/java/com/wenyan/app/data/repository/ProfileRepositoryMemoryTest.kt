package com.wenyan.app.data.repository

import com.wenyan.app.data.db.MemoryFactDao
import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.ProfileDao
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetDao
import com.wenyan.app.data.db.TargetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.2 ProfileRepository 多档案 CRUD 测试（fake DAO：内存实现 TargetDao/ProfileDao 接口）
 * v1.7.3 增加：事实表 CRUD + note→facts 惰性搬移幂等测试（fake MemoryFactDao）。
 * 覆盖：save/get by id / observeAll（id DESC）/ update（改名+正文）/ delete / clearAll。
 */
class ProfileRepositoryMemoryTest {

    private fun newRepo() = ProfileRepository(FakeProfileDao(), FakeTargetDao(), FakeMemoryFactDao())

    @Test
    fun `save and get target by id`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A"))
        assertEquals("小A", repo.getTarget(id)?.codeName)
        assertEquals("", repo.getTarget(id)?.note)
    }

    @Test
    fun `observe targets ordered by id desc`() = runTest {
        val repo = newRepo()
        repo.saveTarget(TargetEntity(codeName = "小A"))
        repo.saveTarget(TargetEntity(codeName = "小B"))
        val list = repo.observeTargets().first()
        assertEquals(listOf("小B", "小A"), list.map { it.codeName })
    }

    @Test
    fun `update target renames and edits note`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = "旧正文"))
        repo.updateTarget(repo.getTarget(id)!!.copy(codeName = "小A2", note = "新正文"))
        val updated = repo.getTarget(id)!!
        assertEquals("小A2", updated.codeName)
        assertEquals("新正文", updated.note)
    }

    @Test
    fun `delete target removes by id`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A"))
        repo.deleteTarget(id)
        assertNull(repo.getTarget(id))
        assertEquals(0, repo.observeTargets().first().size)
    }

    @Test
    fun `get unknown target returns null`() = runTest {
        val repo = newRepo()
        assertNull(repo.getTarget(999L))
    }

    @Test
    fun `clearAll clears targets and profile`() = runTest {
        val repo = newRepo()
        repo.saveProfile(ProfileEntity(mbti = "INTJ"))
        repo.saveTarget(TargetEntity(codeName = "小A"))
        repo.clearAll()
        assertNull(repo.getProfile())
        assertEquals(0, repo.observeTargets().first().size)
    }

    // ===== QA 边界补充：删激活档案回退规则依赖（observeAll 第一条 = id DESC 最新） =====

    @Test
    fun `after deleting newest active target first remaining is next newest`() = runTest {
        val repo = newRepo()
        val idA = repo.saveTarget(TargetEntity(codeName = "小A"))
        val idB = repo.saveTarget(TargetEntity(codeName = "小B"))
        val idC = repo.saveTarget(TargetEntity(codeName = "小C")) // 最新，激活
        // RealSettingsRepository.deleteTarget 逻辑：删 idC 后回退 observeTargets().first()
        repo.deleteTarget(idC)
        val fallback = repo.observeTargets().first().firstOrNull()
        assertEquals(idB, fallback?.id) // 剩余第一条 = 次新（id DESC）
        assertEquals(listOf("小B", "小A"), repo.observeTargets().first().map { it.codeName })
        assertNull(repo.getTarget(idC))
        assertEquals("小A", repo.getTarget(idA)?.codeName)
    }

    @Test
    fun `after deleting all targets observeTargets first is null`() = runTest {
        val repo = newRepo()
        val idA = repo.saveTarget(TargetEntity(codeName = "小A"))
        val idB = repo.saveTarget(TargetEntity(codeName = "小B"))
        repo.deleteTarget(idA)
        repo.deleteTarget(idB)
        // RealSettingsRepository 无剩余 → setActiveTargetId(null)
        assertNull(repo.observeTargets().first().firstOrNull())
        assertEquals(0, repo.observeTargets().first().size)
    }

    // ===== v1.7.3 事实表 CRUD + note→facts 惰性搬移 =====

    @Test
    fun `add fact and observe by target`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A"))
        val factId = repo.addFact(id, "她喜欢猫")
        repo.addFact(id, "她怕黑")
        assertEquals(2, repo.getFacts(id).size)
        assertEquals("她喜欢猫", repo.getFacts(id).single { it.id == factId }.text)
    }

    @Test
    fun `update and delete fact`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A"))
        val factId = repo.addFact(id, "旧事实")
        repo.updateFact(factId, "新事实")
        assertEquals("新事实", repo.getFacts(id).single().text)
        repo.deleteFact(factId)
        assertEquals(0, repo.getFacts(id).size)
    }

    @Test
    fun `migrateNoteToFactsOnce splits note and clears it`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = "她喜欢猫。她怕黑；她喜欢读书"))
        repo.migrateNoteToFactsOnce(id)
        assertEquals(3, repo.countFacts(id))
        assertEquals("", repo.getTarget(id)?.note)
        // memoryText 按 DAO 规范顺序（createdAt DESC, id DESC = 最新在前）
        assertEquals("她喜欢读书；她怕黑；她喜欢猫", repo.memoryText(id))
    }

    @Test
    fun `migrateNoteToFactsOnce idempotent`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = "她喜欢猫。她怕黑"))
        repo.migrateNoteToFactsOnce(id)
        repo.migrateNoteToFactsOnce(id)
        assertEquals(2, repo.countFacts(id))
        assertEquals("她怕黑；她喜欢猫", repo.memoryText(id))
    }

    @Test
    fun `migrateNoteToFactsOnce skips blank note`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = ""))
        repo.migrateNoteToFactsOnce(id)
        assertEquals(0, repo.countFacts(id))
        assertEquals("", repo.getTarget(id)?.note)
    }

    /** v1.7.4 BUG-1 回归：已有事实时不再跳过——老 note 与手工事实 merge 并存，不丢不重 */
    @Test
    fun `migrateNoteToFactsOnce merges legacy note with existing facts`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = "她喜欢猫。她怕黑"))
        repo.addFact(id, "已提炼事实")
        repo.migrateNoteToFactsOnce(id)
        // FakeMemoryFactDao 排序：id DESC（最新在前）→ 搬移插入的在后
        assertEquals(listOf("她怕黑", "她喜欢猫", "已提炼事实"), repo.getFacts(id).map { it.text })
        assertEquals("", repo.getTarget(id)?.note)
        // 与已有事实重叠的段不重复插入
        val id2 = repo.saveTarget(TargetEntity(codeName = "小B", note = "她喜欢猫。她怕黑"))
        repo.addFact(id2, "她喜欢猫")
        repo.migrateNoteToFactsOnce(id2)
        assertEquals(2, repo.countFacts(id2))
        assertEquals("", repo.getTarget(id2)?.note)
    }

    /** v1.7.4 并发回归：8 个并发搬移只插一份（FakeMemoryFactDao.insert 带挂起点模拟真实交错） */
    @Test
    fun `concurrent migrateNoteToFactsOnce inserts facts only once`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = "她喜欢猫。她怕黑；她爱读书"))
        val jobs = (1..8).map { launch { repo.migrateNoteToFactsOnce(id) } }
        jobs.forEach { it.join() }
        assertEquals(3, repo.countFacts(id))
        assertEquals("", repo.getTarget(id)?.note)
    }

    @Test
    fun `migrateNoteToFactsOnce caps at 50 and truncates each to 40 chars`() = runTest {
        val repo = newRepo()
        val longSegment = "字".repeat(60)
        val note = (1..60).joinToString("。") { "事实$it" } + "。" + longSegment
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = note))
        repo.migrateNoteToFactsOnce(id)
        assertEquals(50, repo.countFacts(id))
        assertTrue(repo.getFacts(id).all { it.text.length <= 40 })
    }

    @Test
    fun `clearAll clears facts too`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A"))
        repo.addFact(id, "她喜欢猫")
        repo.clearAll()
        assertEquals(0, repo.countFacts(id))
    }

    // ===== v1.7.3-fix：全档案事实计数（设置页档案行 caption） =====

    @Test
    fun `observeFactCounts groups facts by target`() = runTest {
        val repo = newRepo()
        val idA = repo.saveTarget(TargetEntity(codeName = "小A"))
        val idB = repo.saveTarget(TargetEntity(codeName = "小B"))
        repo.addFact(idA, "她喜欢猫")
        repo.addFact(idA, "她怕黑")
        repo.addFact(idB, "她爱运动")
        val counts = repo.observeFactCounts().first()
        assertEquals(2, counts[idA])
        assertEquals(1, counts[idB])
        assertEquals(0, counts[999L] ?: 0)
    }

    @Test
    fun `observeFactCounts empty when no facts`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A"))
        repo.saveTarget(TargetEntity(codeName = "小B"))
        val counts = repo.observeFactCounts().first()
        assertEquals(0, counts[id] ?: 0)
        assertTrue(counts.isEmpty())
    }

    // ===== QA 独立补充：惰性搬移边界（2026-08-07） =====

    @Test
    fun `memoryText triggers lazy migration implicitly`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = "她喜欢猫。她怕黑"))
        // 注入读 memoryText 前自动搬移：facts 落地 + note 清空
        val text = repo.memoryText(id)
        assertEquals(2, repo.countFacts(id))
        assertEquals("", repo.getTarget(id)?.note)
        assertEquals("她怕黑；她喜欢猫", text)
    }

    @Test
    fun `memoryText on blank note returns empty without migration`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = ""))
        assertEquals("", repo.memoryText(id))
        assertEquals(0, repo.countFacts(id))
    }

    @Test
    fun `memoryText caps joined facts at 2000 chars`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A"))
        repeat(50) { repo.addFact(id, "字".repeat(40)) }
        val text = repo.memoryText(id)
        assertTrue(text.length <= 2000)
    }

    @Test
    fun `migrateNoteToFactsOnce unknown target is no-op`() = runTest {
        val repo = newRepo()
        repo.migrateNoteToFactsOnce(999L)
        // 不抛异常，无副作用
        assertEquals(0, repo.countFacts(999L))
    }

    @Test
    fun `migrateNoteToFactsOnce note already blank stays idempotent`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A", note = ""))
        repo.migrateNoteToFactsOnce(id)
        repo.migrateNoteToFactsOnce(id)
        assertEquals(0, repo.countFacts(id))
        assertEquals("", repo.getTarget(id)?.note)
    }

    @Test
    fun `addFact and updateFact trim text`() = runTest {
        val repo = newRepo()
        val id = repo.saveTarget(TargetEntity(codeName = "小A"))
        val factId = repo.addFact(id, "  她喜欢猫  ")
        assertEquals("她喜欢猫", repo.getFacts(id).single().text)
        repo.updateFact(factId, "  她怕黑  ")
        assertEquals("她怕黑", repo.getFacts(id).single().text)
    }

    /** v1.7.4 BUG-4 回归：悬空 targetId（档案已删）添加事实为 no-op，不触发 FK 约束异常 */
    @Test
    fun `addFact for missing target is no-op`() = runTest {
        val repo = newRepo()
        val id = repo.addFact(999L, "悬空事实")
        assertEquals(0L, id)
        assertEquals(0, repo.countFacts(999L))
    }
}

/** 内存 TargetDao（observeAll 用 MutableStateFlow 模拟 Room Flow 响应式刷新） */
private class FakeTargetDao : TargetDao {
    private val store = mutableListOf<TargetEntity>()
    private var nextId = 1L
    private val _flow = MutableStateFlow<List<TargetEntity>>(emptyList())

    override fun observeAll(): Flow<List<TargetEntity>> = _flow

    override suspend fun getById(id: Long): TargetEntity? = store.firstOrNull { it.id == id }

    override suspend fun insert(entity: TargetEntity): Long {
        val e = entity.copy(id = nextId++)
        store.add(e)
        _flow.value = store.sortedByDescending { it.id }.toList()
        return e.id
    }

    override suspend fun update(entity: TargetEntity) {
        val idx = store.indexOfFirst { it.id == entity.id }
        if (idx >= 0) store[idx] = entity
        _flow.value = store.sortedByDescending { it.id }.toList()
    }

    override suspend fun deleteById(id: Long) {
        store.removeAll { it.id == id }
        _flow.value = store.sortedByDescending { it.id }.toList()
    }

    override suspend fun clearNote(id: Long) {
        val idx = store.indexOfFirst { it.id == id }
        if (idx >= 0) store[idx] = store[idx].copy(note = "")
        _flow.value = store.sortedByDescending { it.id }.toList()
    }

    override suspend fun clear() {
        store.clear()
        _flow.value = emptyList()
    }
}

/** 内存 ProfileDao（MVP 单行：最新一行） */
private class FakeProfileDao : ProfileDao {
    private val store = mutableListOf<ProfileEntity>()
    private var nextId = 1L
    private val _flow = MutableStateFlow<ProfileEntity?>(null)

    override suspend fun getLatest(): ProfileEntity? = store.lastOrNull()

    override fun observeLatest(): Flow<ProfileEntity?> = _flow

    override suspend fun insert(entity: ProfileEntity): Long {
        val e = entity.copy(id = nextId++)
        store.add(e)
        _flow.value = e
        return e.id
    }

    override suspend fun clear() {
        store.clear()
        _flow.value = null
    }
}

/** v1.7.3 内存 MemoryFactDao（observeByTarget 用 MutableStateFlow 模拟 Room Flow 响应式刷新） */
private class FakeMemoryFactDao : MemoryFactDao {
    private val store = mutableListOf<MemoryFactEntity>()
    private var nextId = 1L
    private val _flow = MutableStateFlow<List<MemoryFactEntity>>(emptyList())

    private fun refresh() {
        _flow.value = store.sortedWith(compareByDescending<MemoryFactEntity> { it.createdAt }.thenByDescending { it.id }).toList()
    }

    override fun observeByTarget(targetId: Long): Flow<List<MemoryFactEntity>> =
        MutableStateFlow(store.filter { it.targetId == targetId }.sortedByDescending { it.id })

    override fun observeAll(): Flow<List<MemoryFactEntity>> = _flow

    override suspend fun listByTarget(targetId: Long): List<MemoryFactEntity> =
        store.filter { it.targetId == targetId }.sortedByDescending { it.id }

    override suspend fun getById(id: Long): MemoryFactEntity? = store.firstOrNull { it.id == id }

    override suspend fun insert(entity: MemoryFactEntity): Long {
        // v1.7.4：插入前挂起点模拟真实 Room IO 并发交错（migrate 并发测试依赖：检查-插入非原子）
        kotlinx.coroutines.yield()
        val e = entity.copy(id = nextId++)
        store.add(e)
        refresh()
        return e.id
    }

    override suspend fun update(entity: MemoryFactEntity) {
        val idx = store.indexOfFirst { it.id == entity.id }
        if (idx >= 0) store[idx] = entity
        refresh()
    }

    override suspend fun deleteById(id: Long) {
        store.removeAll { it.id == id }
        refresh()
    }

    override suspend fun deleteByTarget(targetId: Long) {
        store.removeAll { it.targetId == targetId }
        refresh()
    }

    override suspend fun clear() {
        store.clear()
        refresh()
    }
}
