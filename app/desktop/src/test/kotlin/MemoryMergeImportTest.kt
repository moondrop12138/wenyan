package com.wenyan.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.wenyan.app.data.db.AppDatabase
import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.SessionEntity
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.data.security.KeystoreAesGcmCipher
import com.wenyan.app.domain.MemoryExtractor
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * v1.9.4 桌面记忆导出 / 合并导入测试（对齐安卓 MemoryMergeImportTest）。
 *
 * 数据层用真实内存 Room（BundledSQLiteDriver，与桌面生产同驱动同 AppDatabase）：DAU/SQL/事务
 * 都是真的，因此除合并规则外还覆盖「事务失败整体回滚」与「导出 → 导入」跨库往返。
 * 规则参考：安卓 app/app/src/main/java/com/wenyan/app/data/repository/BackupRepository.kt
 * 的 exportMemoryJson / mergeMemoryImport；字段拼写与该文件及桌面 exportAllJson 对齐。
 * 末条用例起真实 CIO 服务（仅 127.0.0.1 环回）验证路由挂载：附件头 / token / 非 500 失败响应。
 */
class MemoryMergeImportTest {

    private val openedDbs = mutableListOf<AppDatabase>()

    /** 内存 Room：与桌面生产同 driver、同 AppDatabase_Impl（建表/迁移由生成代码负责） */
    private fun newDb(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
        .also { openedDbs += it }

    private fun service(db: AppDatabase): WenyanService = WenyanService(db = db, cipher = KeystoreAesGcmCipher())

    @After
    fun tearDown() {
        openedDbs.forEach { runCatching { it.close() } }
        openedDbs.clear()
    }

    // ===== JSON 构造助手（字段拼写与 exportMemoryJson / 安卓 exportMemoryJson 一致） =====

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
        app: String = "wenyan-desktop",
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

    private fun keysOf(json: JSONObject): Set<String> = json.keys().asSequence().toSet()

    private suspend fun merge(db: AppDatabase, json: JSONObject): Pair<Boolean, String> =
        mergeMemoryImport(db.targetDao(), db.memoryFactDao(), db.profileDao(), json)

    // ===== 1. 导出：只含记忆三段，且与全量导出对应段逐字段一致 =====

    @Test
    fun `exportMemoryJson holds memory segments only and matches full export ones`() = runTest {
        val db = newDb()
        val targetId = db.targetDao().insert(
            TargetEntity(
                codeName = "小A", mbti = "INFJ", score = 82, relationStatus = "暧昧期",
                timeline = """[{"time":"2026-07","event":"初见"}]""",
                note = "旧正文", createdAt = 1700000000000L,
            ),
        )
        db.memoryFactDao().insert(
            MemoryFactEntity(
                targetId = targetId, text = "她喜欢猫", kind = MemoryFactEntity.KIND_HYPOTHESIS,
                expiresAt = 1800000000000L, source = MemoryFactEntity.SOURCE_PASTE, createdAt = 1700000001000L,
            ),
        )
        db.memoryFactDao().insert(
            MemoryFactEntity(targetId = targetId, text = "她怕黑", createdAt = 1700000002000L),
        )
        db.profileDao().insert(
            ProfileEntity(mbti = "INTJ", score = 90, strengths = "共情", weaknesses = "内耗"),
        )
        // 非记忆数据：绝不应出现在记忆导出里
        val sessionId = db.sessionDao().insert(SessionEntity(targetId = targetId, title = "会话标题"))
        db.messageDao().insert(
            MessageEntity(sessionId = sessionId, role = "USER", type = "TEXT", content = "聊天记录不应导出"),
        )

        val svc = service(db)
        val mem = svc.exportMemoryJson()
        val all = svc.exportAllJson()

        // 顶层只有记忆三段 + 文件头
        assertEquals(setOf("app", "version", "exportedAt", "profile", "targets", "facts"), keysOf(mem))
        assertEquals("wenyan-desktop", mem.getString("app"))
        assertEquals(1, mem.getInt("version"))
        // 与桌面全量导出（/api/export）对应三段逐字段一致（同库同刻，序列化结果应完全相同）
        assertEquals(all.getJSONArray("targets").toString(), mem.getJSONArray("targets").toString())
        assertEquals(all.getJSONArray("facts").toString(), mem.getJSONArray("facts").toString())
        assertEquals(all.get("profile").toString(), mem.get("profile").toString())
        // 元素字段集与安卓 exportMemoryJson（BackupRepository.kt:189-219）逐字段一致
        assertEquals(
            setOf("id", "codeName", "mbti", "score", "relationStatus", "timeline", "note", "createdAt"),
            keysOf(mem.getJSONArray("targets").getJSONObject(0)),
        )
        assertEquals(
            setOf("targetId", "text", "kind", "expiresAt", "source", "createdAt"),
            keysOf(mem.getJSONArray("facts").getJSONObject(0)),
        )
        assertEquals(setOf("mbti", "score", "strengths", "weaknesses"), keysOf(mem.getJSONObject("profile")))
    }

    @Test
    fun `exportMemoryJson writes null profile when local has none`() = runTest {
        val db = newDb()
        db.targetDao().insert(TargetEntity(codeName = "小A"))
        val mem = service(db).exportMemoryJson()
        assertEquals(setOf("app", "version", "exportedAt", "profile", "targets", "facts"), keysOf(mem))
        assertTrue(mem.isNull("profile"))   // 与两边实现一致：无档案写 JSON null（键存在）
        assertEquals(1, mem.getJSONArray("targets").length())
        assertEquals(0, mem.getJSONArray("facts").length())
    }

    // ===== 2. 导出 → 合入空库：跨库往返不丢字段 =====

    @Test
    fun `exported memory file imports into a fresh desktop db`() = runTest {
        val src = newDb()
        val srcTargetId = src.targetDao().insert(
            TargetEntity(
                codeName = "小A", mbti = "INFJ", score = 82, relationStatus = "暧昧期",
                timeline = """[{"time":"2026-07","event":"初见"}]""",
                note = "旧正文", createdAt = 1700000000000L,
            ),
        )
        src.memoryFactDao().insert(
            MemoryFactEntity(
                targetId = srcTargetId, text = "她喜欢猫", kind = MemoryFactEntity.KIND_HYPOTHESIS,
                expiresAt = 1800000000000L, source = MemoryFactEntity.SOURCE_PASTE, createdAt = 1700000001000L,
            ),
        )
        src.profileDao().insert(ProfileEntity(mbti = "INTJ", score = 90, strengths = "共情", weaknesses = "内耗"))

        val dst = newDb()
        val (ok, msg) = service(dst).importMemoryMerge(service(src).exportMemoryJson())

        assertTrue(msg, ok)
        assertEquals("导入 1 个档案、1 条记忆，已写入用户档案", msg)
        val t = dst.targetDao().listAll().single()
        assertEquals("小A", t.codeName)
        assertEquals("INFJ", t.mbti)
        assertEquals(82, t.score)
        assertEquals("暧昧期", t.relationStatus)
        assertEquals("""[{"time":"2026-07","event":"初见"}]""", t.timeline)
        assertEquals("旧正文", t.note)
        assertEquals(1700000000000L, t.createdAt)
        val f = dst.memoryFactDao().listAll().single()
        assertEquals("她喜欢猫", f.text)
        assertEquals(MemoryFactEntity.KIND_HYPOTHESIS, f.kind)
        assertEquals(1800000000000L, f.expiresAt)
        assertEquals(MemoryFactEntity.SOURCE_PASTE, f.source)
        assertEquals(1700000001000L, f.createdAt)
        val p = dst.profileDao().getLatest()!!
        assertEquals("INTJ", p.mbti)
        assertEquals(90, p.score)
        assertEquals("共情", p.strengths)
        assertEquals("内耗", p.weaknesses)
    }

    // ===== 3. 合并规则：同名档案合并去重（本地数据零删除） =====

    @Test
    fun `same codeName merges into existing target without deleting local data`() = runTest {
        val db = newDb()
        val localId = db.targetDao().insert(TargetEntity(codeName = "小A"))
        db.memoryFactDao().insert(MemoryFactEntity(targetId = localId, text = "她喜欢猫"))

        val (ok, msg) = merge(
            db,
            memoryJson(
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf(
                    factJson(targetId = 7L, text = "她喜欢猫"), // 与本地重复 → 跳过
                    factJson(targetId = 7L, text = "她怕黑"),   // 增量 → 插到本地 id
                ),
            ),
        )

        assertTrue(ok)
        assertEquals(1, db.targetDao().listAll().size)          // 不重复建档
        assertEquals(localId, db.targetDao().listAll().single().id) // 沿用本地 id
        assertEquals(setOf("她喜欢猫", "她怕黑"), db.memoryFactDao().listAll().map { it.text }.toSet())
        assertEquals(2, db.memoryFactDao().listAll().size)      // 本地那条仍在（只增不删）
        assertEquals(localId, db.memoryFactDao().listAll().single { it.text == "她怕黑" }.targetId)
        assertEquals("导入 0 个档案、1 条记忆，跳过 1 条重复", msg)
    }

    @Test
    fun `same codeName matches after trim`() = runTest {
        val db = newDb()
        val localId = db.targetDao().insert(TargetEntity(codeName = "小A"))
        val (ok, _) = merge(db, memoryJson(targets = listOf(targetJson(id = 7L, name = "  小A "))))
        assertTrue(ok)
        assertEquals(localId, db.targetDao().listAll().single().id)
    }

    @Test
    fun `duplicate names inside one file reuse single new target`() = runTest {
        val db = newDb()
        val (ok, msg) = merge(
            db,
            memoryJson(
                targets = listOf(targetJson(id = 1L, name = "小B"), targetJson(id = 2L, name = "小B")),
                facts = listOf(factJson(targetId = 1L, text = "事实一"), factJson(targetId = 2L, text = "事实二")),
            ),
        )
        assertTrue(ok)
        assertEquals(1, db.targetDao().listAll().size)   // 文件内同名只建一个档案
        assertEquals(2, db.memoryFactDao().listAll().size)
        assertTrue(db.memoryFactDao().listAll().all { it.targetId == db.targetDao().listAll().single().id })
        assertEquals("导入 1 个档案、2 条记忆", msg)
    }

    @Test
    fun `new target created with all fields preserved`() = runTest {
        val db = newDb()
        val (ok, msg) = merge(
            db,
            memoryJson(
                targets = listOf(
                    targetJson(
                        id = 9L, name = "小C", mbti = "INFJ", score = 82,
                        relationStatus = "暧昧期", timeline = """[{"time":"2026-07","event":"初见"}]""",
                        note = "旧正文", createdAt = 1700000000000L,
                    ),
                ),
                facts = listOf(
                    factJson(
                        targetId = 9L, text = "她养了只猫", kind = MemoryFactEntity.KIND_HYPOTHESIS,
                        expiresAt = 1800000000000L, source = MemoryFactEntity.SOURCE_PASTE, createdAt = 1700000001000L,
                    ),
                ),
            ),
        )
        assertTrue(ok)
        assertEquals("导入 1 个档案、1 条记忆", msg)
        val t = db.targetDao().listAll().single()
        assertEquals("小C", t.codeName)
        assertEquals("INFJ", t.mbti)
        assertEquals(82, t.score)
        assertEquals("暧昧期", t.relationStatus)
        assertEquals("""[{"time":"2026-07","event":"初见"}]""", t.timeline)
        assertEquals("旧正文", t.note)
        assertEquals(1700000000000L, t.createdAt)
        val f = db.memoryFactDao().listAll().single()
        assertEquals(MemoryFactEntity.KIND_HYPOTHESIS, f.kind)
        assertEquals(1800000000000L, f.expiresAt)
        assertEquals(MemoryFactEntity.SOURCE_PASTE, f.source)
        assertEquals(1700000001000L, f.createdAt)
    }

    // ===== 4. 每档案 50 条上限（MemoryExtractor.DEFAULT_FACT_LIMIT） =====

    @Test
    fun `facts capped at 50 per target and overflow reported`() = runTest {
        val db = newDb()
        val localId = db.targetDao().insert(TargetEntity(codeName = "小A"))
        repeat(48) { i -> db.memoryFactDao().insert(MemoryFactEntity(targetId = localId, text = "已有事实$i")) }

        val (ok, msg) = merge(
            db,
            memoryJson(
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf("新事实一", "新事实二", "新事实三", "新事实四", "新事实五")
                    .map { factJson(targetId = 7L, text = it) },
            ),
        )
        assertTrue(ok)
        assertEquals(MemoryExtractor.DEFAULT_FACT_LIMIT, db.memoryFactDao().countByTarget(localId))
        assertTrue(msg.contains("2 条记忆"))
        assertTrue(msg.contains("3 条超出档案上限"))
        assertTrue(db.memoryFactDao().listAll().none { it.text == "新事实三" })
    }

    @Test
    fun `limit applies per target not globally`() = runTest {
        val db = newDb()
        val (ok, _) = merge(
            db,
            memoryJson(
                targets = listOf(targetJson(id = 1L, name = "小A"), targetJson(id = 2L, name = "小B")),
                facts = (1..MemoryExtractor.DEFAULT_FACT_LIMIT).map { factJson(targetId = 1L, text = "A事实$it") } +
                    (1..MemoryExtractor.DEFAULT_FACT_LIMIT).map { factJson(targetId = 2L, text = "B事实$it") },
            ),
        )
        assertTrue(ok)
        val aId = db.targetDao().listAll().first { it.codeName == "小A" }.id
        val bId = db.targetDao().listAll().first { it.codeName == "小B" }.id
        assertEquals(MemoryExtractor.DEFAULT_FACT_LIMIT, db.memoryFactDao().countByTarget(aId))
        assertEquals(MemoryExtractor.DEFAULT_FACT_LIMIT, db.memoryFactDao().countByTarget(bId))
    }

    // ===== 5. profile 仅本地为空时写入 =====

    @Test
    fun `profile written only when local absent`() = runTest {
        val db = newDb()
        val (ok, msg) = merge(
            db,
            memoryJson(profile = profileJson(mbti = "INTJ", score = 90, strengths = "共情", weaknesses = "内耗")),
        )
        assertTrue(ok)
        assertTrue(msg.endsWith("已写入用户档案"))
        val p = db.profileDao().getLatest()!!
        assertEquals("INTJ", p.mbti)
        assertEquals(90, p.score)
        assertEquals("共情", p.strengths)
        assertEquals("内耗", p.weaknesses)
    }

    @Test
    fun `existing profile is never overwritten`() = runTest {
        val db = newDb()
        db.profileDao().insert(ProfileEntity(mbti = "ISTP", score = 60, strengths = "冷静", weaknesses = "回避"))

        val (ok, msg) = merge(
            db,
            memoryJson(
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf(factJson(targetId = 7L, text = "她喜欢猫")),
                profile = profileJson(mbti = "INTJ", score = 90),
            ),
        )
        assertTrue(ok)
        assertFalse(msg.contains("用户档案"))
        val p = db.profileDao().getLatest()!! // 本地档案原样保留
        assertEquals("ISTP", p.mbti)
        assertEquals(60, p.score)
        assertEquals("冷静", p.strengths)
    }

    @Test
    fun `null profile in file writes nothing`() = runTest {
        val db = newDb()
        val (ok, _) = merge(db, memoryJson()) // profile = null
        assertTrue(ok)
        assertNull(db.profileDao().getLatest())
    }

    // ===== 6. 文件头校验 / 垃圾条目：不触碰任何数据 =====

    @Test
    fun `invalid header rejected without touching data`() = runTest {
        val db = newDb()
        db.targetDao().insert(TargetEntity(codeName = "小A"))
        val svc = service(db)
        assertEquals(false to "不是温言记忆导出文件", svc.importMemoryMerge(JSONObject("{}")))
        assertEquals(false to "不是温言记忆导出文件", svc.importMemoryMerge(memoryJson(app = "other-app")))
        assertEquals(false to "记忆文件版本无效或过低", svc.importMemoryMerge(memoryJson(version = 0)))
        assertEquals(listOf("小A"), db.targetDao().listAll().map { it.codeName })
    }

    @Test
    fun `android memory export is accepted as merge source`() = runTest {
        val db = newDb()
        val (ok, msg) = merge(
            db,
            memoryJson(
                app = "wenyan-android",
                targets = listOf(targetJson(id = 7L, name = "小A")),
                facts = listOf(factJson(targetId = 7L, text = "她喜欢猫")),
            ),
        )
        assertTrue(ok)
        assertEquals("导入 1 个档案、1 条记忆", msg)
    }

    @Test
    fun `blank codeName target skipped and its facts not archived`() = runTest {
        val db = newDb()
        val garbage = JSONObject().put("id", 5L).put("codeName", JSONObject.NULL)
        val (ok, msg) = merge(
            db,
            memoryJson(
                targets = listOf(garbage, targetJson(id = 6L, name = "   ")),
                facts = listOf(
                    factJson(targetId = 5L, text = "无主事实"),
                    factJson(targetId = 6L, text = "也无主"),
                ),
            ),
        )
        assertTrue(ok)
        assertEquals(0, db.targetDao().listAll().size)
        assertEquals(0, db.memoryFactDao().listAll().size)
        assertEquals("导入 0 个档案、0 条记忆", msg)
    }

    @Test
    fun `malformed entries do not delete or corrupt local data`() = runTest {
        val db = newDb()
        val localId = db.targetDao().insert(TargetEntity(codeName = "小A"))
        db.memoryFactDao().insert(MemoryFactEntity(targetId = localId, text = "她喜欢猫"))

        val json = memoryJson(targets = listOf(targetJson(id = 7L, name = "小A")))
        json.put(
            "facts",
            JSONArray()
                .put(factJson(targetId = 999L, text = "无主事实"))          // 档案缺失 → 跳过
                .put(JSONObject().put("targetId", 7L).put("text", JSONObject.NULL)) // text=null → 跳过
                .put(factJson(targetId = 7L, text = "   ")),                 // 空白文本 → 跳过且不计统计
        )
        val (ok, msg) = merge(db, json)

        assertTrue(ok)
        assertEquals(1, db.targetDao().listAll().size)
        assertEquals(listOf("她喜欢猫"), db.memoryFactDao().listAll().map { it.text })
        assertEquals("导入 0 个档案、0 条记忆", msg)
    }

    @Test
    fun `missing targets and facts arrays are tolerated`() = runTest {
        val db = newDb()
        val (ok, msg) = merge(db, JSONObject().put("app", "wenyan-desktop").put("version", 1))
        assertTrue(ok)
        assertTrue(db.targetDao().listAll().isEmpty())
        assertEquals("导入 0 个档案、0 条记忆", msg)
    }

    // ===== 7. 桌面全量导出也可作为记忆来源（sessions/messages 段被忽略） =====

    @Test
    fun `desktop full export imports memory only ignoring chat data`() = runTest {
        val src = newDb()
        val srcTargetId = src.targetDao().insert(TargetEntity(codeName = "小A"))
        val sessionId = src.sessionDao().insert(SessionEntity(targetId = srcTargetId, title = "会话"))
        src.messageDao().insert(
            MessageEntity(sessionId = sessionId, role = "USER", type = "TEXT", content = "不该被导入"),
        )
        src.memoryFactDao().insert(MemoryFactEntity(targetId = srcTargetId, text = "她喜欢猫"))

        val dst = newDb()
        val (ok, msg) = service(dst).importMemoryMerge(service(src).exportAllJson())

        assertTrue(msg, ok)
        assertEquals(1, dst.targetDao().listAll().size)
        assertEquals(1, dst.memoryFactDao().listAll().size)
        assertTrue(dst.sessionDao().observeAll().first().isEmpty())  // 聊天记录未随记忆导入
        assertTrue(dst.messageDao().observeBySession(1L).first().isEmpty())
    }

    // ===== 8. 事务：中途失败整体回滚（不得留下半份导入） =====

    @Test
    fun `failure inside transaction rolls back every insert`() = runTest {
        val db = newDb()
        val localId = db.targetDao().insert(TargetEntity(codeName = "小A"))
        db.memoryFactDao().insert(MemoryFactEntity(targetId = localId, text = "她喜欢猫"))

        val json = JSONObject()
            .put("app", "wenyan-desktop").put("version", 1)
            .put(
                "targets",
                JSONArray()
                    .put(targetJson(id = 1L, name = "小B"))   // 已被真实 insert
                    .put("垃圾条目"),                          // 非对象 → getJSONObject 抛异常 → 事务回滚
            )
            .put("facts", JSONArray().put(factJson(targetId = 1L, text = "新事实")))

        val (ok, msg) = service(db).importMemoryMerge(json)

        assertFalse(ok)
        assertTrue(msg, msg.startsWith("导入失败："))
        assertEquals(listOf("小A"), db.targetDao().listAll().map { it.codeName }) // 「小B」插入已回滚
        assertEquals(listOf("她喜欢猫"), db.memoryFactDao().listAll().map { it.text })
    }

    // ===== 9. HTTP 挂载：附件下载 / token / 失败不抛 500（真实 CIO，仅 127.0.0.1） =====

    @Test
    fun `routes export attachment without token and guard import with token`() = runBlocking {
        val db = newDb()
        db.targetDao().insert(TargetEntity(codeName = "小A"))
        val token = "test-token-9f2c"
        val svc = service(db)
        // 与生产 Main.kt 的 L19 教训一致：bind→close→稍后重绑的临时端口存在 TOCTOU——
        // 探测与实际绑定的窗口期可被并行进程/测试 JVM 抢走。改 port=0 由系统分配，
        // start 后经 resolvedConnectors() 取真实端口（resolvedConnectors 为挂起函数，本用例在 runBlocking 内）。
        val engine = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            // 与 Main.kt 同配置：拦截器的 403（respond(status, mapOf(...))）需要内容协商才能序列化
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; prettyPrint = false })
            }
            routing { apiRoutes(svc, ChatEngine(svc), token) }
        }
        engine.start(wait = false)
        val port = engine.resolvedConnectors().first().port
        try {
            awaitServer(port)
            val client = HttpClient.newHttpClient()

            // GET /api/memory/export：无 token 也可下载，响应为附件
            val exportResp = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/memory/export")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, exportResp.statusCode())
            val disposition = exportResp.headers().firstValue("Content-Disposition").orElse("")
            assertTrue(disposition, disposition.startsWith("attachment; filename=\"wenyan-memory-"))
            val exported = JSONObject(exportResp.body())
            assertEquals("wenyan-desktop", exported.getString("app"))
            assertEquals(setOf("app", "version", "exportedAt", "profile", "targets", "facts"), keysOf(exported))

            val body = memoryJson(
                targets = listOf(targetJson(id = 7L, name = "小B")),
                facts = listOf(factJson(targetId = 7L, text = "她喜欢猫")),
            ).toString()

            // POST 无 token：403（统一拦截器，同 /api/import），且未写入
            val forbidden = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/memory/import"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(403, forbidden.statusCode())
            assertEquals(listOf("小A"), db.targetDao().listAll().map { it.codeName })

            // POST 带 token：合并导入成功
            val okResp = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/memory/import"))
                    .header("Content-Type", "application/json")
                    .header("X-Wenyan-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, okResp.statusCode())
            val okJson = JSONObject(okResp.body())
            assertTrue(okJson.getBoolean("ok"))
            assertEquals("导入 1 个档案、1 条记忆", okJson.getString("message"))
            assertEquals("", okJson.getString("error"))
            assertEquals(listOf("小A", "小B"), db.targetDao().listAll().map { it.codeName })

            // 非法 JSON：200 + ok:false（不抛 500）
            val badResp = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/memory/import"))
                    .header("Content-Type", "application/json")
                    .header("X-Wenyan-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString("{不是 JSON")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, badResp.statusCode())
            assertFalse(JSONObject(badResp.body()).getBoolean("ok"))
        } finally {
            engine.stop(1000, 2000)
        }
    }

    /**
     * 等待嵌入式服务完成端口绑定（start(wait=false) 与实际可连接之间的极短窗口）。
     * 用真实阻塞 sleep：本用例走 runBlocking（非 runTest），delay 的虚拟时间不适用。
     */
    private fun awaitServer(port: Int) {
        repeat(100) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }
                return
            } catch (_: Exception) {
                Thread.sleep(50)
            }
        }
        throw IllegalStateException("嵌入式测试服务未在 5s 内就绪（port=$port）")
    }
}
