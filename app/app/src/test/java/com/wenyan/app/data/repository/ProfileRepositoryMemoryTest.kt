package com.wenyan.app.data.repository

import com.wenyan.app.data.db.ProfileDao
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetDao
import com.wenyan.app.data.db.TargetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.7.2 ProfileRepository 多档案 CRUD 测试（fake DAO：内存实现 TargetDao/ProfileDao 接口）
 * 覆盖：save/get by id / observeAll（id DESC）/ update（改名+正文）/ delete / clearAll。
 */
class ProfileRepositoryMemoryTest {

    private fun newRepo() = ProfileRepository(FakeProfileDao(), FakeTargetDao())

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
