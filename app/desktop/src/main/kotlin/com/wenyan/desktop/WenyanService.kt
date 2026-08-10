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
        db.memoryFactDao().insert(MemoryFactEntity(targetId = targetId, text = text))

    suspend fun updateFact(factId: Long, text: String) {
        val current = db.memoryFactDao().getById(factId) ?: return
        db.memoryFactDao().update(current.copy(text = text))
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
    }
}

private typealias JSONArray = org.json.JSONArray
