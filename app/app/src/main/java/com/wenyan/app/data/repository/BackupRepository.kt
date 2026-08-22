package com.wenyan.app.data.repository

import androidx.room.withTransaction
import com.wenyan.app.data.db.AppDatabase
import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.ModelEntity
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.ProviderEntity
import com.wenyan.app.data.db.SessionEntity
import com.wenyan.app.data.db.TargetEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * L27 修复：runCatching 会吞掉 CancellationException——协程取消后代码继续跑完并返回
 * 「失败」，取消语义被破坏（结构化并发泄漏/重复下载）。此变体把 CE 原样重抛。
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/**
 * O1: Android 端「从备份恢复」数据层。
 * 复用桌面端导出 JSON schema（app/version/providers/models/targets/facts/sessions/messages/profile）。
 * 导入策略：清空后重建（与桌面端 importAllJson 对齐）；FK 逐表重映射；API Key 脱敏，导入后需重新输入。
 */
class BackupRepository(private val db: AppDatabase) {

    /** @return (成功, 错误信息)；错误信息为空串 = 成功 */
    suspend fun restore(json: JSONObject): Pair<Boolean, String> {
        val app = json.optString("app", "")
        if (app != "wenyan-desktop" && app != "wenyan-android") {
            return false to "备份文件不是温言导出文件"
        }
        if (json.optInt("version", -1) < 1) {
            return false to "备份文件版本无效或过低"
        }
        return runCatchingCancellable {
            db.withTransaction {
                // 先清空（顺序：消息→会话→事实→档案→模型→提供商→画像）
                db.messageDao().clear()
                db.sessionDao().clear()
                db.memoryFactDao().clear()
                db.targetDao().clear()
                db.modelDao().clear()
                db.providerDao().clear()
                db.profileDao().clear()

                val providerIdMap = mutableMapOf<Long, Long>()
                val providers = json.optJSONArray("providers") ?: JSONArray()
                for (i in 0 until providers.length()) {
                    val p = providers.getJSONObject(i)
                    val newId = db.providerDao().insert(
                        ProviderEntity(
                            name = p.optString("name"),
                            baseUrl = p.optString("baseUrl"),
                            apiKeyEncrypted = null, // 脱敏：导入后重新输入
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
                if (profile != null && profile !== JSONObject.NULL) {
                    db.profileDao().insert(
                        ProfileEntity(
                            // L28 修复：回传备份中的 createdAt（原用 now() → 恢复后画像时间漂移）
                            createdAt = profile.optLong("createdAt", System.currentTimeMillis()),
                            mbti = if (profile.isNull("mbti")) null else profile.optString("mbti"),
                            score = if (profile.isNull("score")) null else profile.optInt("score"),
                            strengths = if (profile.isNull("strengths")) null else profile.optString("strengths"),
                            weaknesses = if (profile.isNull("weaknesses")) null else profile.optString("weaknesses"),
                        )
                    )
                }
            }
        }.fold(
            onSuccess = { true to "" },
            onFailure = { false to "导入失败：${it.message ?: "数据损坏"}" },
        )
    }
}
