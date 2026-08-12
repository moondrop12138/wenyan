package com.wenyan.desktop

import com.wenyan.app.data.db.AppDatabase
import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.ModelEntity
import com.wenyan.app.data.db.PresetSeed
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.ProviderEntity
import com.wenyan.app.data.db.SessionEntity
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.data.security.KeystoreAesGcmCipher
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

    /** 全量导出为 JSON（Provider 的 Key 密文脱敏为 hasApiKey 布尔，绝不出密文） */
    suspend fun exportAllJson(): org.json.JSONObject {
        val providers = JSONArray().apply {
            listProviders().forEach { p ->
                put(org.json.JSONObject()
                    .put("name", p.name).put("baseUrl", p.baseUrl)
                    .put("hasApiKey", p.apiKeyEncrypted != null)
                    .put("isPreset", p.isPreset).put("sortOrder", p.sortOrder))
            }
        }
        val models = JSONArray().apply {
            listAllModels().forEach { m ->
                put(org.json.JSONObject()
                    .put("providerId", m.providerId).put("name", m.name)
                    .put("supportsVision", m.supportsVision)
                    .put("isDefault", m.isDefault).put("showInSheet", m.showInSheet)
                    .put("sortOrder", m.sortOrder))
            }
        }
        val targets = JSONArray().apply {
            listTargets().forEach { t ->
                put(org.json.JSONObject()
                    .put("id", t.id).put("codeName", t.codeName)
                    .put("mbti", t.mbti ?: org.json.JSONObject.NULL)
                    .put("score", t.score ?: org.json.JSONObject.NULL)
                    .put("relationStatus", t.relationStatus ?: org.json.JSONObject.NULL)
                    .put("timeline", t.timeline).put("note", t.note)
                    .put("createdAt", t.createdAt))
            }
        }
        val facts = JSONArray().apply {
            listTargets().forEach { t ->
                listFacts(t.id).forEach { f ->
                    put(org.json.JSONObject()
                        .put("targetId", f.targetId).put("text", f.text)
                        .put("createdAt", f.createdAt))
                }
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
        val profile = getLatestProfile()?.let { p ->
            org.json.JSONObject()
                .put("mbti", p.mbti ?: org.json.JSONObject.NULL)
                .put("score", p.score ?: org.json.JSONObject.NULL)
                .put("strengths", p.strengths ?: org.json.JSONObject.NULL)
                .put("weaknesses", p.weaknesses ?: org.json.JSONObject.NULL)
        } ?: org.json.JSONObject.NULL
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
