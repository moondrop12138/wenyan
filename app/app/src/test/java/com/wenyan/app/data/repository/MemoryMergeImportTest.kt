package com.wenyan.app.data.repository

import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.domain.MemoryExtractor
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.4 记忆合并导入测试：
 * 被测对象 mergeMemoryImport = BackupRepository.importMemoryMerge 的核心合并逻辑
 * （internal 提出为纯 DAO 函数，绕开 db.withTransaction 的 Room 依赖，纯 JVM 可测；
 * 生产路径由 importMemoryMerge 包 withTransaction + runCatchingCancellable，行为不变）。
 * 覆盖：同名档案合并去重 / 新档案创建携带字段 / 50 条上限截断 / profile 仅空时写入 / 结构非法返回 (false, _)。
 * F57 精简：假 DAO 改用同包共享夹具（FakeDaos.kt）。
 */
class MemoryMergeImportTest {

    private fun newFakes(): Triple<FakeTargetDao, FakeMemoryFactDao, FakeProfileDao> =
        Triple(FakeTargetDao(), FakeMemoryFactDao(), FakeProfileDao())

    /** 单次合并入口（三假 DAO 对齐 BackupRepository.importMemoryMerge 的传参顺序） */
    private suspend fun merge(
        targetDao: FakeTargetDao,
        factDao: FakeMemoryFactDao,
        profileDao: FakeProfileDao,
        json: JSONObject,
    ): Pair<Boolean, String> = mergeMemoryImport(targetDao, factDao, profileDao, json)

    // ===== JSON 构造助手（字段拼写与 BackupRepository.exportMemoryJson 一致） =====

    private fun targetJson(
        id: Long,
        name: String,
        mbti: String? = null,
        score: Int? = null,
        relationStatus: String? = null,
        timeline: String = "[]",
        note: String = "",
        createdAt: Long = 1000L,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("codeName", name)
        .put("mbti", mbti ?: JSONObject.NULL)
        .put("score", score ?: JSONObject.NULL)
        .put("relationStatus", relationStatus ?: JSONObject.NULL)
        .put("timeline", timeline)
        .put("note", note)
        .put("createdAt", createdAt)

    private fun factJson(
        targetId: Long,
        text: String,
        kind: String = MemoryFactEntity.KIND_FACT,
        expiresAt: Long? = null,
        source: String = MemoryFactEntity.SOURCE_MANUAL,
        createdAt: Long = 2000L,
    ): JSONObject = JSONObject()
        .put("targetId", targetId)
        .put("text", text)
        .put("kind", kind)
        .put("expiresAt", expiresAt ?: JSONObject.NULL)
        .put("source", source)
        .put("createdAt", createdAt)

    private fun memoryJson(
        app: String = "wenyan-android",
        version: Int = 1,
        targets: List<JSONObject> = emptyList(),
        facts: List<JSONObject> = emptyList(),
        profile: JSONObject? = null,
    ): JSONObject {
        val root = JSONObject()
            .put("app", app)
            .put("version", version)
            .put("exportedAt", 123L)
            .put("targets", JSONArray().apply { targets.forEach { put(it) } })
            .put("facts", JSONArray().apply { facts.forEach { put(it) } })
        root.put("profile", profile ?: JSONObject.NULL)
        return root
    }

    private fun profileJson(
        mbti: String? = null,
        score: Int? = null,
        strengths: String? = null,
        weaknesses: String? = null,
    ): JSONObject = JSONObject()
        .put("mbti", mbti ?: JSONObject.NULL)
        .put("score", score ?: JSONObject.NULL)
        .put("strengths", strengths ?: JSONObject.NULL)
        .put("weaknesses", weaknesses ?: JSONObject.NULL)

    // ===== 1. 同名档案合并去重 =====

    @Test
    fun `same codeName merges into existing target without creating new one`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val localId = targetDao.insert(TargetEntity(codeName = "小A"))
        factDao.insert(MemoryFactEntity(targetId = localId, text = "她喜欢猫"))

        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf(
                    // 与本地重复 → 跳过；新增一条 → 插入本地 id
                    factJson(targetId = 7L, text = "她喜欢猫"),
                    factJson(targetId = 7L, text = "她怕黑"),
                ),
            ),
        )
        assertTrue(ok)
        assertEquals(1, targetDao.listAll().size) // 不重复建档
        assertEquals(localId, targetDao.listAll().single().id) // 沿用本地 id
        // 原数据未删：本地事实保留 + 新增一条（断言用集合，factDao.listAll 按 createdAt 升序与插入序无关）
        val texts = factDao.listAll().map { it.text }
        assertEquals(setOf("她喜欢猫", "她怕黑"), texts.toSet())
        assertEquals(2, texts.size)
        assertEquals(localId, factDao.listAll().single { it.text == "她怕黑" }.targetId)
        assertEquals("导入 0 个档案、1 条记忆，跳过 1 条重复", msg)
    }

    @Test
    fun `same codeName matches after trim`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val localId = targetDao.insert(TargetEntity(codeName = "小A"))
        val (ok, _) = merge(
            targetDao, factDao, profileDao,
            memoryJson(targets = listOf(targetJson(id = 7L, name = "  小A "))),
        )
        assertTrue(ok)
        assertEquals(localId, targetDao.listAll().single().id)
    }

    @Test
    fun `duplicate names inside one file reuse single new target`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(targetJson(id = 1L, name = "小B"), targetJson(id = 2L, name = "小B")),
                facts = listOf(
                    factJson(targetId = 1L, text = "事实一"),
                    factJson(targetId = 2L, text = "事实二"),
                ),
            ),
        )
        assertTrue(ok)
        assertEquals(1, targetDao.listAll().size) // 文件内同名只建一个档案
        assertEquals(2, factDao.listAll().size) // 两条事实都归档到同一本地 id
        assertTrue(factDao.listAll().all { it.targetId == targetDao.listAll().single().id })
        assertEquals("导入 1 个档案、2 条记忆", msg)
    }

    // ===== 2. 新档案创建并携带字段 =====

    @Test
    fun `new target created with all fields preserved`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(
                    targetJson(
                        id = 9L, name = "小C", mbti = "INFJ", score = 82,
                        relationStatus = "暧昧期", timeline = "[{\"time\":\"2026-07\",\"event\":\"初见\"}]",
                        note = "旧正文", createdAt = 1700000000000L,
                    ),
                ),
                facts = listOf(
                    factJson(targetId = 9L, text = "她养了只猫", kind = MemoryFactEntity.KIND_HYPOTHESIS,
                        expiresAt = 1800000000000L, source = MemoryFactEntity.SOURCE_PASTE, createdAt = 1700000001000L),
                ),
            ),
        )
        assertTrue(ok)
        assertEquals("导入 1 个档案、1 条记忆", msg)
        val t = targetDao.listAll().single()
        assertEquals("小C", t.codeName)
        assertEquals("INFJ", t.mbti)
        assertEquals(82, t.score)
        assertEquals("暧昧期", t.relationStatus)
        assertEquals("[{\"time\":\"2026-07\",\"event\":\"初见\"}]", t.timeline)
        assertEquals("旧正文", t.note)
        assertEquals(1700000000000L, t.createdAt)
        // facts 的 kind/expiresAt/source/createdAt 原样保留
        val f = factDao.listAll().single()
        assertEquals("她养了只猫", f.text)
        assertEquals(MemoryFactEntity.KIND_HYPOTHESIS, f.kind)
        assertEquals(1800000000000L, f.expiresAt)
        assertEquals(MemoryFactEntity.SOURCE_PASTE, f.source)
        assertEquals(1700000001000L, f.createdAt)
    }

    // ===== 3. 每档案 50 条上限截断 =====

    @Test
    fun `facts capped at 50 per target and overflow reported`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val localId = targetDao.insert(TargetEntity(codeName = "小A"))
        repeat(48) { i -> factDao.insert(MemoryFactEntity(targetId = localId, text = "已有事实$i")) }

        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf("新事实一", "新事实二", "新事实三", "新事实四", "新事实五")
                    .map { factJson(targetId = 7L, text = it) },
            ),
        )
        assertTrue(ok)
        assertEquals(50, factDao.countByTarget(localId)) // 恰好补到上限，绝不越界
        assertTrue(msg.contains("2 条记忆"))
        assertTrue(msg.contains("3 条超出档案上限"))
        assertTrue(factDao.listAll().none { it.text == "新事实三" })
    }

    @Test
    fun `limit applies per target not globally`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, _) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(targetJson(id = 1L, name = "小A"), targetJson(id = 2L, name = "小B")),
                facts = (1..MemoryExtractor.DEFAULT_FACT_LIMIT).map { factJson(targetId = 1L, text = "A事实$it") } +
                    (1..MemoryExtractor.DEFAULT_FACT_LIMIT).map { factJson(targetId = 2L, text = "B事实$it") },
            ),
        )
        assertTrue(ok)
        val aId = targetDao.listAll().first { it.codeName == "小A" }.id
        val bId = targetDao.listAll().first { it.codeName == "小B" }.id
        assertEquals(MemoryExtractor.DEFAULT_FACT_LIMIT, factDao.countByTarget(aId))
        assertEquals(MemoryExtractor.DEFAULT_FACT_LIMIT, factDao.countByTarget(bId))
    }

    // ===== 4. profile 仅本地无档案时写入 =====

    @Test
    fun `profile written only when local absent`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(profile = profileJson(mbti = "INTJ", score = 90, strengths = "共情", weaknesses = "内耗")),
        )
        assertTrue(ok)
        assertTrue(msg.endsWith("已写入用户档案"))
        val p = profileDao.getLatest()!!
        assertEquals("INTJ", p.mbti)
        assertEquals(90, p.score)
        assertEquals("共情", p.strengths)
        assertEquals("内耗", p.weaknesses)
    }

    @Test
    fun `existing profile is never overwritten`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        profileDao.insert(ProfileEntity(mbti = "ISTP", score = 60, strengths = "冷静", weaknesses = "回避"))

        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf(factJson(targetId = 7L, text = "她喜欢猫")),
                profile = profileJson(mbti = "INTJ", score = 90),
            ),
        )
        assertTrue(ok)
        assertFalse(msg.contains("用户档案"))
        val p = profileDao.getLatest()!! // 本地档案原样保留，未被覆盖
        assertEquals("ISTP", p.mbti)
        assertEquals(60, p.score)
        assertEquals("冷静", p.strengths)
    }

    @Test
    fun `null profile in file writes nothing`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, _) = merge(targetDao, factDao, profileDao, memoryJson()) // profile = null
        assertTrue(ok)
        assertTrue(profileDao.getLatest() == null)
    }

    // ===== 5. 损坏 / 结构非法 JSON 返回 (false, 原因) =====

    @Test
    fun `missing app field returns false`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(targetDao, factDao, profileDao, JSONObject("{}"))
        assertFalse(ok)
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `unknown app field returns false`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(targetDao, factDao, profileDao, memoryJson(app = "other-app"))
        assertFalse(ok)
        assertEquals("不是温言记忆导出文件", msg)
    }

    @Test
    fun `invalid version returns false`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(targetDao, factDao, profileDao, memoryJson(version = 0))
        assertFalse(ok)
        assertEquals("记忆文件版本无效或过低", msg)
    }

    @Test
    fun `malformed entry does not delete or corrupt local data`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val localId = targetDao.insert(TargetEntity(codeName = "小A"))
        factDao.insert(MemoryFactEntity(targetId = localId, text = "她喜欢猫"))
        // 非法条目：text 为 JSON null（垃圾条目跳过）/ targetId 无对应档案（跳过）
        val facts = JSONArray()
            .put(factJson(targetId = 999L, text = "无主事实"))
            .put(JSONObject().put("targetId", 7L).put("text", JSONObject.NULL))
        val json = memoryJson(
            targets = listOf(targetJson(id = 7L, name = "小A")),
        )
        json.put("facts", facts)
        val (ok, _) = merge(targetDao, factDao, profileDao, json)
        assertTrue(ok)
        assertEquals(1, targetDao.listAll().size)
        assertEquals(listOf("她喜欢猫"), factDao.listAll().map { it.text }) // 本地数据完好
    }

    // ===== 6. 合并边界补充：垃圾档案条目 / 文件内重复 / 跨档案同文案 / 缺段容忍 / 空白文本 =====

    @Test
    fun `blank codeName target skipped and its facts not archived`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        // 无名（JSON null）与纯空白档案同为垃圾条目 → 跳过建档，其事实因档案缺失一并跳过
        val garbage = JSONObject().put("id", 5L).put("codeName", JSONObject.NULL)
        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(garbage, targetJson(id = 6L, name = "   ")),
                facts = listOf(
                    factJson(targetId = 5L, text = "无主事实"),
                    factJson(targetId = 6L, text = "也无主"),
                ),
            ),
        )
        assertTrue(ok)
        assertEquals(0, targetDao.listAll().size)
        assertEquals(0, factDao.listAll().size) // 本地数据零污染
        assertEquals("导入 0 个档案、0 条记忆", msg)
    }

    @Test
    fun `duplicate text inside one file counts as skipped duplicate`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf(
                    factJson(targetId = 7L, text = "她喜欢猫"),
                    factJson(targetId = 7L, text = "她喜欢猫"), // 文件内部重复 → 只插第一条
                ),
            ),
        )
        assertTrue(ok)
        assertEquals(1, factDao.listAll().size)
        assertEquals("导入 1 个档案、1 条记忆，跳过 1 条重复", msg)
    }

    @Test
    fun `same text under different targets is not deduplicated`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(targetJson(id = 1L, name = "小A"), targetJson(id = 2L, name = "小B")),
                facts = listOf(
                    factJson(targetId = 1L, text = "她喜欢猫"),
                    factJson(targetId = 2L, text = "她喜欢猫"), // 去重按档案隔离，跨档案同文案互不影响
                ),
            ),
        )
        assertTrue(ok)
        assertEquals(2, factDao.listAll().size)
        assertEquals("导入 2 个档案、2 条记忆", msg)
    }

    @Test
    fun `missing targets and facts arrays are tolerated`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        // 缺 targets/facts 段（如桌面端旧版导出）→ 按空数组处理，不报错
        val json = JSONObject().put("app", "wenyan-android").put("version", 1)
        val (ok, msg) = merge(targetDao, factDao, profileDao, json)
        assertTrue(ok)
        assertTrue(targetDao.listAll().isEmpty())
        assertEquals("导入 0 个档案、0 条记忆", msg)
    }

    @Test
    fun `blank text fact skipped without counting as duplicate`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf(factJson(targetId = 7L, text = "   ")),
            ),
        )
        assertTrue(ok)
        assertEquals(0, factDao.listAll().size)
        // 空白文本属垃圾条目：不入库、不计入任何统计（不出现「跳过 N 条重复」）
        assertEquals("导入 1 个档案、0 条记忆", msg)
    }

    // ===== 补充：桌面端全量导出（wenyan-desktop）也可作为记忆合并来源 =====

    @Test
    fun `desktop export accepted as memory source`() = runTest {
        val (targetDao, factDao, profileDao) = newFakes()
        val (ok, msg) = merge(
            targetDao, factDao, profileDao,
            memoryJson(
                app = "wenyan-desktop",
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf(factJson(targetId = 7L, text = "她喜欢猫")),
            ),
        )
        assertTrue(ok)
        assertEquals("导入 1 个档案、1 条记忆", msg)
    }
}

// F57 精简：内存假 DAO 改用同包共享夹具（FakeDaos.kt；原 MergeFake* 私有拷贝删除）
