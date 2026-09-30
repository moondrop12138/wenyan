package com.wenyan.app.data.repository

import com.wenyan.app.data.db.MemoryFactDao
import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.ProfileDao
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetDao
import com.wenyan.app.data.db.TargetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * F57 精简：内存假 DAO 收敛为同包共享测试夹具——原 MemoryMergeImportTest（MergeFake* 前缀）
 * 与 ProfileRepositoryMemoryTest（Fake* 前缀）各持一份约 120 行的逐行同构拷贝
 * （唯一实质差异：FakeMemoryFactDao.insert 的 yield() 挂起点，对合并测试无害，统一保留；
 * TargetDao 的 refresh() 抽取属纯等价重构）。
 * 排序全部对齐生产 SQL（见各方法注释）。
 */

/** 内存 TargetDao（observeAll 用 MutableStateFlow 模拟 Room Flow 响应式刷新） */
internal class FakeTargetDao : TargetDao {
    private val store = mutableListOf<TargetEntity>()
    private var nextId = 1L
    private val _flow = MutableStateFlow<List<TargetEntity>>(emptyList())

    private fun refresh() {
        _flow.value = store.sortedByDescending { it.id }.toList()
    }

    override fun observeAll(): Flow<List<TargetEntity>> = _flow

    /** v1.9.4：id 升序，对齐生产 SQL（SELECT * FROM target ORDER BY id ASC） */
    override suspend fun listAll(): List<TargetEntity> = store.sortedBy { it.id }

    override suspend fun getById(id: Long): TargetEntity? = store.firstOrNull { it.id == id }

    override suspend fun insert(entity: TargetEntity): Long {
        val e = entity.copy(id = nextId++)
        store.add(e)
        refresh()
        return e.id
    }

    override suspend fun update(entity: TargetEntity) {
        val idx = store.indexOfFirst { it.id == entity.id }
        if (idx >= 0) store[idx] = entity
        refresh()
    }

    override suspend fun deleteById(id: Long) {
        store.removeAll { it.id == id }
        refresh()
    }

    override suspend fun clearNote(id: Long) {
        val idx = store.indexOfFirst { it.id == id }
        if (idx >= 0) store[idx] = store[idx].copy(note = "")
        refresh()
    }

    override suspend fun clear() {
        store.clear()
        refresh()
    }
}

/** 内存 ProfileDao（MVP 单行：最新一行） */
internal class FakeProfileDao : ProfileDao {
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

/** 内存 MemoryFactDao（observeByTarget 用 MutableStateFlow 模拟 Room Flow 响应式刷新） */
internal class FakeMemoryFactDao : MemoryFactDao {
    private val store = mutableListOf<MemoryFactEntity>()
    private var nextId = 1L
    private val _flow = MutableStateFlow<List<MemoryFactEntity>>(emptyList())

    private fun refresh() {
        _flow.value = store.sortedWith(
            compareByDescending<MemoryFactEntity> { it.createdAt }.thenByDescending { it.id },
        ).toList()
    }

    override fun observeByTarget(targetId: Long): Flow<List<MemoryFactEntity>> =
        MutableStateFlow(store.filter { it.targetId == targetId }.sortedByDescending { it.id })

    override fun observeAll(): Flow<List<MemoryFactEntity>> = _flow

    /** v1.9.4：targetId+createdAt+id 升序，对齐生产 SQL（listAll） */
    override suspend fun listAll(): List<MemoryFactEntity> =
        store.sortedWith(compareBy({ it.targetId }, { it.createdAt }, { it.id }))

    override suspend fun listByTarget(targetId: Long): List<MemoryFactEntity> =
        store.filter { it.targetId == targetId }.sortedByDescending { it.id }

    override suspend fun countByTarget(targetId: Long): Int = store.count { it.targetId == targetId }

    override suspend fun getById(id: Long): MemoryFactEntity? = store.firstOrNull { it.id == id }

    override suspend fun insert(entity: MemoryFactEntity): Long {
        // v1.7.4：插入前挂起点模拟真实 Room IO 并发交错（migrate 并发测试依赖：检查-插入非原子；
        // 对串行执行的合并导入测试无影响）
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
