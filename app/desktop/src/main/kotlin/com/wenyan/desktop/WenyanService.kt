package com.wenyan.desktop

import androidx.room.useWriterConnection
import com.wenyan.app.data.db.AppDatabase
import com.wenyan.app.data.db.MemoryFactDao
import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.ModelEntity
import com.wenyan.app.data.db.PresetSeed
import com.wenyan.app.data.db.ProfileDao
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.ProviderEntity
import com.wenyan.app.data.db.SessionEntity
import com.wenyan.app.data.db.TargetDao
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.data.security.KeystoreAesGcmCipher
import com.wenyan.app.domain.MemoryExtractor
import com.wenyan.app.knowledge.KnowledgeRouting
import com.wenyan.app.knowledge.RouteLlmConfig
import kotlinx.coroutines.flow.first

/**
 * 桌面版业务服务层：封装 Room 数据库 + 加解密，供 Ktor 路由调用。
 *
 * 数据访问直接面向共享的 DAO（不经过 Android 的 Repository 层——那是 UI 接缝）。
 * API Key 加解密复用共享的 AesGcmCipher + desktop 机器指纹 provider（KeystoreAesGcmCipher）。
 */
class WenyanService(
    private val db: AppDatabase = AppDatabase.get(),
    private val cipher: KeystoreAesGcmCipher = KeystoreAesGcmCipher(),
) {

    /** 首次启动注入预设提供商/模型（幂等） */
    suspend fun seedIfEmpty() = PresetSeed.seedIfEmpty(db)

    // ===== 提供商 / 模型（BYOK）=====

    suspend fun listProviders(): List<ProviderEntity> = db.providerDao().listAll()

    suspend fun listModels(providerId: Long): List<ModelEntity> =
        db.modelDao().listByProvider(providerId)

    suspend fun listAllModels(): List<ModelEntity> = db.modelDao().observeAll().first()

    suspend fun addProvider(name: String, baseUrl: String, apiKey: String?, isPreset: Boolean): Long {
        val encrypted = apiKey?.takeIf { it.isNotBlank() }?.let { cipher.encrypt(it) }
        return db.providerDao().insert(
            ProviderEntity(
                name = name,
                baseUrl = baseUrl,
                apiKeyEncrypted = encrypted,
                isPreset = isPreset,
            )
        )
    }

    suspend fun updateProvider(id: Long, name: String, baseUrl: String, apiKey: String?) {
        val current = db.providerDao().getById(id) ?: return
        val encrypted = when {
            apiKey == null -> current.apiKeyEncrypted          // 未提供 → 保留
            apiKey.isBlank() -> null                            // 显式清空
            else -> cipher.encrypt(apiKey)                      // 重新加密
        }
        db.providerDao().update(
            current.copy(name = name, baseUrl = baseUrl, apiKeyEncrypted = encrypted)
        )
    }

    suspend fun deleteProvider(id: Long) = db.providerDao().deleteById(id)

    suspend fun getProvider(id: Long): ProviderEntity? = db.providerDao().getById(id)

    suspend fun getModel(id: Long): ModelEntity? = db.modelDao().getById(id)

    /** 测连接红绿灯回写（RealSettingsRepository.markConnectionStatus 语义） */
    suspend fun updateConnectionStatus(providerId: Long, status: String) {
        val current = db.providerDao().getById(providerId) ?: return
        db.providerDao().update(current.copy(connectionStatus = status))
    }

    /** 解密 API Key（仅供 LLM 出网用） */
    suspend fun decryptApiKey(providerId: Long): String? {
        val entity = db.providerDao().getById(providerId) ?: return null
        val encrypted = entity.apiKeyEncrypted ?: return null
        return runCatching { cipher.decrypt(encrypted) }.getOrNull()
    }

    suspend fun addModel(providerId: Long, name: String, supportsVision: Boolean): Long =
        db.modelDao().insert(
            ModelEntity(providerId = providerId, name = name, supportsVision = supportsVision)
        )

    suspend fun deleteModel(id: Long) = db.modelDao().deleteById(id)

    // ===== 档案（target）=====

    suspend fun listTargets(): List<TargetEntity> = db.targetDao().observeAll().first()

    suspend fun createTarget(codeName: String): Long =
        db.targetDao().insert(TargetEntity(codeName = codeName))

    suspend fun updateTarget(entity: TargetEntity) = db.targetDao().update(entity)

    suspend fun getTarget(id: Long): TargetEntity? = db.targetDao().getById(id)

    suspend fun clearTargetNote(id: Long) = db.targetDao().clearNote(id)

    suspend fun deleteTarget(id: Long) {
        db.targetDao().deleteById(id)
        db.sessionDao().unbindTarget(id)   // 防悬空（v1.7.4 语义）
    }

    // ===== 记忆事实（memory_fact）=====

    suspend fun listFacts(targetId: Long): List<MemoryFactEntity> =
        db.memoryFactDao().listByTarget(targetId)

    suspend fun addFact(targetId: Long, text: String): Long =
        addFact(targetId, text, MemoryFactEntity.KIND_FACT)

    /** v1.9.0：带分层写入（fact/hypothesis） */
    suspend fun addFact(targetId: Long, text: String, kind: String): Long =
        addFact(targetId, text, kind, expiresAt = null, source = MemoryFactEntity.SOURCE_MANUAL)

    /** v1.9.1：完整写入（kind + expiresAt 到期时间 + source 素材来源） */
    suspend fun addFact(targetId: Long, text: String, kind: String, expiresAt: Long?, source: String): Long =
        db.memoryFactDao().insert(
            MemoryFactEntity(
                targetId = targetId,
                text = text,
                kind = if (kind == MemoryFactEntity.KIND_HYPOTHESIS) kind else MemoryFactEntity.KIND_FACT,
                expiresAt = expiresAt,
                source = source,
            ),
        )

    suspend fun updateFact(factId: Long, text: String) {
        val current = db.memoryFactDao().getById(factId) ?: return
        db.memoryFactDao().update(current.copy(text = text))
    }

    /** v1.9.1 临时事实转永久（清空到期时间） */
    suspend fun makePermanent(factId: Long) {
        val current = db.memoryFactDao().getById(factId) ?: return
        if (current.expiresAt != null) {
            db.memoryFactDao().update(current.copy(expiresAt = null))
        }
    }

    suspend fun deleteFact(factId: Long) = db.memoryFactDao().deleteById(factId)

    // ===== 会话 / 消息 =====

    suspend fun listSessions(): List<SessionEntity> = db.sessionDao().observeAll().first()

    suspend fun createSession(targetId: Long?): Long =
        db.sessionDao().insert(SessionEntity(targetId = targetId))

    suspend fun getSession(id: Long): SessionEntity? = db.sessionDao().getById(id)

    suspend fun deleteSession(id: Long) {
        db.messageDao().deleteBySession(id)
        db.sessionDao().deleteById(id)
    }

    suspend fun updateSessionTitle(id: Long, title: String) =
        db.sessionDao().updateTitle(id, title)

    suspend fun updateSessionState(id: Long, stateJson: String) =
        db.sessionDao().updateState(id, stateJson)

    suspend fun updateSessionRefDocs(id: Long, refDocsJson: String) =
        db.sessionDao().updateRefDocs(id, refDocsJson)

    suspend fun updateSessionTarget(id: Long, targetId: Long?) =
        db.sessionDao().bindTarget(id, targetId)

    suspend fun listMessages(sessionId: Long): List<MessageEntity> =
        db.messageDao().listBySession(sessionId)

    /** O3: 全文检索（空白查询返回空；命中消息原文，不含脱敏 Key） */
    suspend fun searchMessages(query: String): List<MessageEntity> =
        if (query.isBlank()) emptyList() else db.messageDao().search(query.trim())

    suspend fun addMessage(sessionId: Long, role: String, type: String, content: String): Long =
        db.messageDao().insert(
            MessageEntity(sessionId = sessionId, role = role, type = type, content = content)
        )

    suspend fun deleteMessage(messageId: Long) = db.messageDao().deleteById(messageId)

    // ===== 档案（profile）=====

    suspend fun getLatestProfile(): ProfileEntity? = db.profileDao().getLatest()

    /**
     * onboarding 判定：未配置任何带 Key 的提供商（首次进 App 引导配置 BYOK）。
     * 手机版对应"是否完成引导"的本地标记；桌面版无 SharedPreferences，以数据态推导。
     */
    suspend fun needsOnboarding(): Boolean =
        db.providerDao().listAll().none { it.apiKeyEncrypted != null }

    suspend fun saveProfile(mbti: String?, score: Int?, strengths: String?, weaknesses: String?): Long =
        db.profileDao().insert(
            ProfileEntity(mbti = mbti, score = score, strengths = strengths, weaknesses = weaknesses)
        )

    // ===== 设置槽位（轻量 KV，Properties 文件；不动共享 Room schema）=====

    /**
     * 视觉模型槽位（对齐手机端 DataStore vision_model_id）：主模型不支持视觉时，
     * 聊天图片走通道 B——由该槽位模型先转述。null = 未配置。
     */
    fun getVisionModelId(): Long? = DesktopSettingsStore.get("visionModelId")?.toLongOrNull()

    /** null = 清除槽位（对齐手机端 setVisionModelId(null) 语义） */
    fun setVisionModelId(id: Long?) = DesktopSettingsStore.put("visionModelId", id?.toString())

    // ===== v1.9.0 记忆控制（对齐手机端 DataStore 槽位）=====

    /** 自动记忆开关（默认开；关闭后回复完成不再提炼） */
    fun isMemoryAutoEnabled(): Boolean = DesktopSettingsStore.get("memoryAutoEnabled") != "false"

    fun setMemoryAutoEnabled(enabled: Boolean) =
        DesktopSettingsStore.put("memoryAutoEnabled", enabled.toString())

    // ===== 知识路由模式（对齐手机端 DataStore knowledge_routing 槽位）=====

    /** 知识路由模式（"llm" 默认 | "offline" 显式关闭）；读取经 KnowledgeRouting.normalize 归一，只有显式 "offline" 才关 */
    fun getKnowledgeRouting(): String = KnowledgeRouting.normalize(DesktopSettingsStore.get("knowledgeRouting"))

    /** 写入前归一（只有显式 "offline" 才关，与手机端 setKnowledgeRouting 语义一致） */
    fun setKnowledgeRouting(mode: String) {
        DesktopSettingsStore.put("knowledgeRouting", KnowledgeRouting.normalize(mode))
    }

    /**
     * 主模型三元组（LLM 路由分类器配置；模型/提供商/Key 任一缺失 → null，分类器不可用）。
     * 桌面主模型随聊天请求传入（前端逐请求带 modelId），与手机端 DataStore 槽位解析等价。
     */
    suspend fun resolveRouteLlmConfig(modelId: Long): RouteLlmConfig? {
        val model = getModel(modelId) ?: return null
        val provider = getProvider(model.providerId) ?: return null
        val apiKey = decryptApiKey(provider.id) ?: return null
        return RouteLlmConfig(provider.baseUrl, apiKey, model.name)
    }

    /** 最近一次自动写入日志（无则 null） */
    fun lastMemoryWrite(): DesktopWriteLogEntry? = DesktopWriteLogCodec.decodeLast(DesktopSettingsStore.get("memoryWriteLog"))

    /** 撤销最近一次自动写入：返回被撤销的 fact id 列表（空 = 无日志可撤销） */
    fun undoLastMemoryWrite(): List<Long> {
        val log = DesktopWriteLogCodec.decodeAll(DesktopSettingsStore.get("memoryWriteLog"))
        val last = log.firstOrNull() ?: return emptyList()
        DesktopSettingsStore.put("memoryWriteLog", DesktopWriteLogCodec.encodeAll(log.drop(1)))
        return last.factIds
    }

    /** 记录一次自动写入（最近在前，截断保留 5 条） */
    fun recordMemoryWrite(targetId: Long, factIds: List<Long>, summary: String) {
        if (factIds.isEmpty()) return
        val entry = DesktopWriteLogEntry(targetId, factIds, summary, System.currentTimeMillis())
        val updated = (listOf(entry) + DesktopWriteLogCodec.decodeAll(DesktopSettingsStore.get("memoryWriteLog"))).take(5)
        DesktopSettingsStore.put("memoryWriteLog", DesktopWriteLogCodec.encodeAll(updated))
    }

    // ===== 数据管理（导出 / 清空）=====

    // F113：exportAllJson 与 exportMemoryJson 的 targets/facts/profile 三段序列化原本逐字重复，
    // 提炼为共用构建器（字段保持与两处原实现逐字段一致，改动前已逐字段比对确认）

    private fun targetToJson(t: TargetEntity): org.json.JSONObject =
        org.json.JSONObject()
            .put("id", t.id).put("codeName", t.codeName)
            .put("mbti", t.mbti ?: org.json.JSONObject.NULL)
            .put("score", t.score ?: org.json.JSONObject.NULL)
            .put("relationStatus", t.relationStatus ?: org.json.JSONObject.NULL)
            .put("timeline", t.timeline).put("note", t.note)
            .put("createdAt", t.createdAt)

    private fun factToJson(f: MemoryFactEntity): org.json.JSONObject =
        org.json.JSONObject()
            .put("targetId", f.targetId).put("text", f.text)
            .put("kind", f.kind)
            .put("expiresAt", f.expiresAt ?: org.json.JSONObject.NULL)
            .put("source", f.source)
            .put("createdAt", f.createdAt)

    private fun profileToJson(p: ProfileEntity): org.json.JSONObject =
        org.json.JSONObject()
            .put("mbti", p.mbti ?: org.json.JSONObject.NULL)
            .put("score", p.score ?: org.json.JSONObject.NULL)
            .put("strengths", p.strengths ?: org.json.JSONObject.NULL)
            .put("weaknesses", p.weaknesses ?: org.json.JSONObject.NULL)

    /** 全量导出为 JSON（Provider 的 Key 密文脱敏为 hasApiKey 布尔，绝不出密文） */
    suspend fun exportAllJson(): org.json.JSONObject {
        val providers = JSONArray().apply {
            listProviders().forEach { p ->
                put(org.json.JSONObject()
                    .put("id", p.id)
                    .put("name", p.name).put("baseUrl", p.baseUrl)
                    .put("hasApiKey", p.apiKeyEncrypted != null)
                    .put("isPreset", p.isPreset).put("sortOrder", p.sortOrder))
            }
        }
        val models = JSONArray().apply {
            listAllModels().forEach { m ->
                put(org.json.JSONObject()
                    .put("id", m.id)
                    .put("providerId", m.providerId).put("name", m.name)
                    .put("supportsVision", m.supportsVision)
                    .put("isDefault", m.isDefault).put("showInSheet", m.showInSheet)
                    .put("sortOrder", m.sortOrder))
            }
        }
        val targets = JSONArray().apply {
            listTargets().forEach { t -> put(targetToJson(t)) }
        }
        val facts = JSONArray().apply {
            listTargets().forEach { t ->
                listFacts(t.id).forEach { f -> put(factToJson(f)) }
            }
        }
        val sessions = JSONArray().apply {
            listSessions().forEach { s ->
                put(org.json.JSONObject()
                    .put("id", s.id).put("title", s.title)
                    .put("targetId", s.targetId ?: org.json.JSONObject.NULL)
                    .put("stateJson", s.stateJson).put("refDocs", s.refDocs)
                    .put("createdAt", s.createdAt))
            }
        }
        val profile = getLatestProfile()?.let { profileToJson(it) } ?: org.json.JSONObject.NULL
        val messages = JSONArray().apply {
            listSessions().forEach { s ->
                listMessages(s.id).forEach { m ->
                    put(org.json.JSONObject()
                        .put("sessionId", m.sessionId).put("role", m.role)
                        .put("type", m.type).put("content", m.content)
                        .put("createdAt", m.createdAt))
                }
            }
        }
        return org.json.JSONObject()
            .put("app", "wenyan-desktop").put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("providers", providers).put("models", models)
            .put("targets", targets).put("facts", facts)
            .put("sessions", sessions).put("messages", messages)
            .put("profile", profile)
    }

    /**
     * O1: 从导出 JSON 恢复数据（清空后重建；FK 逐表重映射；Key 密文脱敏，导入后需重新输入）。
     * @return (成功, 错误信息)
     */
    suspend fun importAllJson(json: org.json.JSONObject): Pair<Boolean, String> {
        if (json.optString("app", "") != "wenyan-desktop") {
            return false to "备份文件不是温言桌面版导出"
        }
        if (json.optInt("version", -1) < 1) {
            return false to "备份文件版本无效或过低"
        }
        runCatching {
            // M3 修复：清空+重建包进 Room 事务——任一记录损坏整体回滚。
            // 原实现无事务：原数据已清空、新数据只导一半，不可恢复（体保持原缩进以最小化 diff）。
            // JVM 侧 Room KMP 无 RoomDatabase.withTransaction 扩展（room-ktx 是 Android-only），
        // 用 useWriterConnection + Transactor.withTransaction（DAO 挂起调用经协程元素加入同一事务）
        db.useWriterConnection { transactor ->
            transactor.withTransaction(androidx.room.Transactor.SQLiteTransactionType.DEFERRED) {
            clearAll()
            val providerIdMap = mutableMapOf<Long, Long>()
            val providers = json.optJSONArray("providers") ?: JSONArray()
            for (i in 0 until providers.length()) {
                val p = providers.getJSONObject(i)
                val newId = db.providerDao().insert(
                    ProviderEntity(
                        name = p.optString("name"),
                        baseUrl = p.optString("baseUrl"),
                        apiKeyEncrypted = null,  // 脱敏：导入后重新输入
                        isPreset = p.optBoolean("isPreset", false),
                        sortOrder = p.optInt("sortOrder", 0),
                    )
                )
                providerIdMap[p.optLong("id", -1)] = newId
            }
            val models = json.optJSONArray("models") ?: JSONArray()
            for (i in 0 until models.length()) {
                val m = models.getJSONObject(i)
                val providerId = providerIdMap[m.optLong("providerId", -1)] ?: continue
                db.modelDao().insert(
                    ModelEntity(
                        providerId = providerId,
                        name = m.optString("name"),
                        supportsVision = m.optBoolean("supportsVision", false),
                        isDefault = m.optBoolean("isDefault", false),
                        showInSheet = m.optBoolean("showInSheet", true),
                        sortOrder = m.optInt("sortOrder", 0),
                    )
                )
            }
            val targetIdMap = mutableMapOf<Long, Long>()
            val targets = json.optJSONArray("targets") ?: JSONArray()
            for (i in 0 until targets.length()) {
                val t = targets.getJSONObject(i)
                val newId = db.targetDao().insert(
                    TargetEntity(
                        codeName = t.optString("codeName"),
                        mbti = if (t.isNull("mbti")) null else t.optString("mbti"),
                        score = if (t.isNull("score")) null else t.optInt("score"),
                        relationStatus = if (t.isNull("relationStatus")) null else t.optString("relationStatus"),
                        timeline = t.optString("timeline", "[]"),
                        note = t.optString("note", ""),
                        createdAt = t.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
                targetIdMap[t.optLong("id", -1)] = newId
            }
            val facts = json.optJSONArray("facts") ?: JSONArray()
            for (i in 0 until facts.length()) {
                val f = facts.getJSONObject(i)
                val targetId = targetIdMap[f.optLong("targetId", -1)] ?: continue
                db.memoryFactDao().insert(
                    MemoryFactEntity(
                        targetId = targetId,
                        text = f.optString("text"),
                        kind = f.optString("kind", MemoryFactEntity.KIND_FACT),
                        expiresAt = if (f.isNull("expiresAt")) null else f.optLong("expiresAt"),
                        source = f.optString("source", MemoryFactEntity.SOURCE_MANUAL),
                        createdAt = f.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
            val sessionIdMap = mutableMapOf<Long, Long>()
            val sessions = json.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until sessions.length()) {
                val s = sessions.getJSONObject(i)
                val newId = db.sessionDao().insert(
                    SessionEntity(
                        createdAt = s.optLong("createdAt", System.currentTimeMillis()),
                        refDocs = s.optString("refDocs", "[]"),
                        stateJson = s.optString("stateJson", ""),
                        title = s.optString("title", ""),
                        targetId = if (s.isNull("targetId")) null else targetIdMap[s.optLong("targetId", -1)],
                    )
                )
                sessionIdMap[s.optLong("id", -1)] = newId
            }
            val messages = json.optJSONArray("messages") ?: JSONArray()
            for (i in 0 until messages.length()) {
                val m = messages.getJSONObject(i)
                val sessionId = sessionIdMap[m.optLong("sessionId", -1)] ?: continue
                db.messageDao().insert(
                    MessageEntity(
                        sessionId = sessionId,
                        role = m.optString("role"),
                        type = m.optString("type"),
                        content = m.optString("content"),
                        createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
            val profile = json.optJSONObject("profile")
            if (profile != null && profile !== org.json.JSONObject.NULL) {
                db.profileDao().insert(
                    ProfileEntity(
                        mbti = if (profile.isNull("mbti")) null else profile.optString("mbti"),
                        score = if (profile.isNull("score")) null else profile.optInt("score"),
                        strengths = if (profile.isNull("strengths")) null else profile.optString("strengths"),
                        weaknesses = if (profile.isNull("weaknesses")) null else profile.optString("weaknesses"),
                    )
                )
            }
        }
        } // M3: end useWriterConnection/withTransaction
        }.onFailure {
            // F112：runCatching 会吞 CancellationException（取消后仍返回「失败」，破坏结构化并发）
            // ——与下方 importMemoryMerge / 安卓 runCatchingCancellable（L27）同修：取消必须原样重抛
            if (it is kotlinx.coroutines.CancellationException) throw it
            return false to "导入失败：${it.message ?: "数据损坏"}"
        }
        return true to ""
    }

    // ===== v1.9.4 记忆导出 / 合并导入（换机迁移，对齐安卓 BackupRepository）=====

    /**
     * v1.9.4 记忆导出：只导出记忆三段（targets/facts/profile），不含聊天记录（sessions/messages）
     * 与 API Key（providers/models）。
     * 三段字段与 exportAllJson 对应段逐字段一致、与安卓 BackupRepository.exportMemoryJson 同构，
     * app 标记为 wenyan-desktop（version 1）；任一端「记忆合并导入」均可读取本文件。
     * 注：facts 顺序沿用 exportAllJson 的「按档案 id 升序 + 档案内 listFacts（新→旧）」，
     * 与安卓导出（全表按 createdAt 升序）集合相同、顺序无关（导入按 text 去重，不看顺序）。
     */
    suspend fun exportMemoryJson(): org.json.JSONObject {
        val targets = listTargets()
        return org.json.JSONObject()
            .put("app", "wenyan-desktop").put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("profile", getLatestProfile()?.let { profileToJson(it) } ?: org.json.JSONObject.NULL)
            .put("targets", JSONArray().apply {
                targets.forEach { t -> put(targetToJson(t)) }
            })
            .put("facts", JSONArray().apply {
                targets.forEach { t ->
                    listFacts(t.id).forEach { f -> put(factToJson(f)) }
                }
            })
    }

    /**
     * v1.9.4 记忆合并导入（换机迁移）：绝不清表、绝不删除任何现有数据
     * （与 importAllJson 的清空重建相反；重复内容按 text 去重跳过）。
     * 兼容两种文件：桌面记忆导出（wenyan-desktop）与安卓记忆导出（wenyan-android）；
     * 桌面「全量导出」文件同样可导入——providers/models/sessions/messages 段被忽略，
     * 只取 targets/facts/profile 三段（换机只带走记忆）。
     * 事务包裹（写法同 importAllJson：useWriterConnection + Transactor.withTransaction）：
     * 任一步失败整体回滚，不产生半份导入。
     * @return (是否成功, 中文结果描述)——成功时为合并摘要（导入 N 个档案、M 条记忆…），
     *         失败时为中文原因；文件非法快速失败且不触碰任何数据
     */
    suspend fun importMemoryMerge(json: org.json.JSONObject): Pair<Boolean, String> {
        validateMemoryExportHeader(json)?.let { return false to it }
        return try {
            db.useWriterConnection { transactor ->
                transactor.withTransaction(androidx.room.Transactor.SQLiteTransactionType.DEFERRED) {
                    mergeMemoryImport(db.targetDao(), db.memoryFactDao(), db.profileDao(), json)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 安卓 L27 同款：runCatching 会吞 CancellationException（取消后仍返回「失败」，破坏结构化并发）
            throw e
        } catch (e: Throwable) {
            false to "导入失败：${e.message ?: "数据损坏"}"
        }
    }

    /** 清空全部数据（顺序：消息→会话→事实→档案→模型→提供商→用户画像），返回后由调用方重新 seed */
    suspend fun clearAll() {
        db.messageDao().clear()
        db.sessionDao().clear()
        db.memoryFactDao().clear()
        db.targetDao().clear()
        db.modelDao().clear()
        db.providerDao().clear()
        db.profileDao().clear()   // 画像（MBTI 测评）也是用户数据，"清空全部"必须覆盖
        DesktopSettingsStore.clear()   // 设置槽位（视觉模型）一并清除，对齐手机端 clearAll
    }
}

/**
 * 桌面版轻量设置存储：Properties 文件（%APPDATA%\Wenyan\wenyan-settings.properties，与 DB 同目录）。
 * 对齐手机端 SettingsRepository（DataStore）的槽位语义；当前仅 visionModelId。
 * 线程安全：synchronized 读写，写后落盘（单文件极小，性能无虞）。
 */
private object DesktopSettingsStore {
    private val file = java.io.File(com.wenyan.app.data.db.AppDatabase.dbDir(), "wenyan-settings.properties")
    private val props = java.util.Properties()

    init {
        if (file.exists()) {
            runCatching { file.inputStream().use { props.load(it) } }
        }
    }

    @Synchronized
    fun get(key: String): String? = props.getProperty(key)?.takeIf { it.isNotBlank() }

    @Synchronized
    fun put(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        runCatching { file.outputStream().use { props.store(it, "wenyan desktop settings") } }
    }

    @Synchronized
    fun clear() {
        props.clear()
        runCatching { file.outputStream().use { props.store(it, "wenyan desktop settings") } }
    }
}

private typealias JSONArray = org.json.JSONArray

/**
 * 温言记忆导出文件头校验：app 标识（wenyan-desktop / wenyan-android）+ 版本号 ≥1。
 * 非法返回中文原因，合法返回 null。与安卓 BackupRepository.validateMemoryExportHeader 同语义（双端互读）。
 * WenyanService.importMemoryMerge（事务外快速失败）与 mergeMemoryImport（核心函数自身输入防御，
 * 亦供单测直接覆盖）共用；校验幂等，合法文件两处零额外行为。
 */
internal fun validateMemoryExportHeader(json: org.json.JSONObject): String? {
    val app = json.optString("app", "")
    if (app != "wenyan-desktop" && app != "wenyan-android") {
        return "不是温言记忆导出文件"
    }
    if (json.optInt("version", -1) < 1) {
        return "记忆文件版本无效或过低"
    }
    return null
}

/**
 * v1.9.4 合并导入核心逻辑（internal 提出为独立函数：只依赖三个 DAO，纯 JVM 可单测；
 * 生产路径由 WenyanService.importMemoryMerge 包事务 + try/catch，规则与安卓
 * BackupRepository.mergeMemoryImport 逐条一致）。
 * 合并规则：
 * - targets 按 codeName（trim 后精确匹配）对上 → 沿用本地 id；对不上 → 插入新档案
 *   （mbti/score/relationStatus/timeline/note/createdAt 原样保留）；
 * - facts 在 targetId 重映射后按 text 精确相等去重，只插增量；每档案总数 ≤50
 *   （MemoryExtractor.DEFAULT_FACT_LIMIT，与提炼链路上限一致），超出跳过；
 *   expiresAt/source/kind/createdAt 原样保留；
 * - profile 仅当本地无档案（getLatest()==null）时写入，绝不覆盖现有用户画像。
 * 本函数只做 insert（无任何 delete/clear），失败由事务层整体回滚——绝不删除任何现有数据。
 * @return (true, 中文结果描述)；DAO 异常向上抛出由事务层统一兜底为 (false, 原因)
 */
internal suspend fun mergeMemoryImport(
    targetDao: TargetDao,
    memoryFactDao: MemoryFactDao,
    profileDao: ProfileDao,
    json: org.json.JSONObject,
): Pair<Boolean, String> {
    // 文件头校验（app 标识 / 版本号）：非法直接拒绝，不触碰任何 DAO
    validateMemoryExportHeader(json)?.let { return false to it }
    val targets = json.optJSONArray("targets") ?: JSONArray()
    val facts = json.optJSONArray("facts") ?: JSONArray()

    // 档案合并索引：本地现有 + 本次已建档案（trim 后 codeName → 本地 id）。
    // 导入文件内同名条目也复用同一档案（先建者胜），不重复建档。
    val idByName = HashMap<String, Long>()
    targetDao.listAll().forEach { idByName[it.codeName.trim()] = it.id }
    val idMap = HashMap<Long, Long>() // 导出 targetId → 本地 targetId
    var newTargets = 0
    for (i in 0 until targets.length()) {
        val t = targets.getJSONObject(i)
        val name = if (t.isNull("codeName")) "" else t.optString("codeName").trim()
        if (name.isEmpty()) continue // 无名档案为垃圾条目，整体跳过（其事实随后也不可归档）
        val resolvedId = idByName[name] ?: run {
            val newId = targetDao.insert(
                TargetEntity(
                    codeName = name,
                    mbti = if (t.isNull("mbti")) null else t.optString("mbti"),
                    score = if (t.isNull("score")) null else t.optInt("score"),
                    relationStatus = if (t.isNull("relationStatus")) null else t.optString("relationStatus"),
                    timeline = t.optString("timeline", "[]"),
                    note = t.optString("note", ""),
                    createdAt = t.optLong("createdAt", System.currentTimeMillis()),
                ),
            )
            idByName[name] = newId
            newTargets++
            newId
        }
        idMap[t.optLong("id", -1)] = resolvedId
    }

    // 事实去重/计数索引：按本地 targetId 收集现有 text 集合与条数（一次全表拉取）
    val seenTexts = HashMap<Long, MutableSet<String>>()
    val counts = HashMap<Long, Int>()
    memoryFactDao.listAll().forEach { f ->
        seenTexts.getOrPut(f.targetId) { mutableSetOf() }.add(f.text)
        counts[f.targetId] = (counts[f.targetId] ?: 0) + 1
    }

    var addedFacts = 0
    var skippedDuplicates = 0
    var skippedOverLimit = 0
    for (i in 0 until facts.length()) {
        val f = facts.getJSONObject(i)
        val localTargetId = idMap[f.optLong("targetId", -1)] ?: continue // 档案缺失的事实跳过
        // 同安卓 M7 防御：text 为 JSON null 时 optString 会返回字面量 "null"，先 isNull 预检
        val text = if (f.isNull("text")) "" else f.optString("text")
        if (text.isBlank()) continue // 空文本为垃圾条目，不计入任何统计
        val seen = seenTexts.getOrPut(localTargetId) { mutableSetOf() }
        if (text in seen) {
            skippedDuplicates++
            continue
        }
        val current = counts[localTargetId] ?: 0
        if (current >= MemoryExtractor.DEFAULT_FACT_LIMIT) {
            skippedOverLimit++
            continue
        }
        memoryFactDao.insert(
            MemoryFactEntity(
                targetId = localTargetId,
                text = text,
                kind = f.optString("kind", MemoryFactEntity.KIND_FACT),
                expiresAt = if (f.isNull("expiresAt")) null else f.optLong("expiresAt"),
                source = f.optString("source", MemoryFactEntity.SOURCE_MANUAL),
                createdAt = f.optLong("createdAt", System.currentTimeMillis()),
            ),
        )
        seen.add(text)
        counts[localTargetId] = current + 1
        addedFacts++
    }

    // profile 仅当本地无档案时写入（合并语义：绝不覆盖现有用户画像）
    var profileWritten = false
    val jsonProfile = json.optJSONObject("profile")
    if (jsonProfile != null && jsonProfile !== org.json.JSONObject.NULL && profileDao.getLatest() == null) {
        profileDao.insert(
            ProfileEntity(
                createdAt = jsonProfile.optLong("createdAt", System.currentTimeMillis()),
                mbti = if (jsonProfile.isNull("mbti")) null else jsonProfile.optString("mbti"),
                score = if (jsonProfile.isNull("score")) null else jsonProfile.optInt("score"),
                strengths = if (jsonProfile.isNull("strengths")) null else jsonProfile.optString("strengths"),
                weaknesses = if (jsonProfile.isNull("weaknesses")) null else jsonProfile.optString("weaknesses"),
            ),
        )
        profileWritten = true
    }

    val message = buildString {
        append("导入 ").append(newTargets).append(" 个档案、").append(addedFacts).append(" 条记忆")
        if (skippedDuplicates > 0) append("，跳过 ").append(skippedDuplicates).append(" 条重复")
        if (skippedOverLimit > 0) append("，").append(skippedOverLimit).append(" 条超出档案上限")
        if (profileWritten) append("，已写入用户档案")
    }
    return true to message
}

/** v1.9.0 桌面版自动记忆写入日志条目（与手机端 SettingsRepository.MemoryWriteLogEntry 同构） */
data class DesktopWriteLogEntry(
    val targetId: Long,
    val factIds: List<Long>,
    val summary: String,
    val createdAt: Long,
)

/** 桌面版写入日志编解码（格式：targetId,factId:factId,summary,createdAt 换行分隔，summary 内逗号替换） */
private object DesktopWriteLogCodec {
    private const val FIELD_SEP = ","
    private const val LINE_SEP = "\n"
    private const val ID_SEP = ":"

    fun encodeAll(entries: List<DesktopWriteLogEntry>): String = entries.joinToString(LINE_SEP) { e ->
        e.targetId.toString() + FIELD_SEP +
            e.factIds.joinToString(ID_SEP) + FIELD_SEP +
            e.summary.replace('\n', ' ').replace(',', '，') + FIELD_SEP +
            e.createdAt.toString()
    }

    fun decodeAll(raw: String?): List<DesktopWriteLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(LINE_SEP).mapNotNull { line ->
            val parts = line.split(FIELD_SEP)
            if (parts.size < 4) return@mapNotNull null
            val ids = parts[1].split(ID_SEP).mapNotNull { it.toLongOrNull() }
            if (ids.isEmpty()) return@mapNotNull null
            DesktopWriteLogEntry(
                targetId = parts[0].toLongOrNull() ?: 0L,
                factIds = ids,
                summary = parts[2],
                createdAt = parts[3].toLongOrNull() ?: 0L,
            )
        }
    }

    fun decodeLast(raw: String?): DesktopWriteLogEntry? = decodeAll(raw).firstOrNull()
}
