package com.wenyan.app.data.repository

import androidx.room.withTransaction
import com.wenyan.app.data.db.AppDatabase
import com.wenyan.app.data.db.MemoryFactDao
import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.ModelEntity
import com.wenyan.app.data.db.ProfileDao
import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.ProviderEntity
import com.wenyan.app.data.db.SessionEntity
import com.wenyan.app.data.db.TargetDao
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.domain.MemoryExtractor
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

    /**
     * v1.9.4 记忆导出（换机迁移）：只导出记忆数据（用户档案 profile + 目标档案 targets + 记忆条目 facts），
     * 不含聊天记录（sessions/messages）与 API Key（providers/models）。
     * 字段拼写与桌面端 WenyanService.exportAllJson 完全一致（app 标记为 wenyan-android）；
     * 可空字段统一写 JSONObject.NULL（保持键存在，与桌面端一致），桌面端/本端均可读取。
     */
    suspend fun exportMemoryJson(): String {
        val targets = db.targetDao().listAll()
        // 全表一次拉取按 targetId 分组，避免逐档案查询（顺序与桌面端逐档案遍历一致）
        val factsByTarget = db.memoryFactDao().listAll().groupBy { it.targetId }
        val now = System.currentTimeMillis()
        val targetsJson = JSONArray()
        val factsJson = JSONArray()
        for (t in targets) {
            targetsJson.put(
                JSONObject()
                    .put("id", t.id)
                    .put("codeName", t.codeName)
                    .put("mbti", t.mbti ?: JSONObject.NULL)
                    .put("score", t.score ?: JSONObject.NULL)
                    .put("relationStatus", t.relationStatus ?: JSONObject.NULL)
                    .put("timeline", t.timeline)
                    .put("note", t.note)
                    .put("createdAt", t.createdAt),
            )
            factsByTarget[t.id].orEmpty().forEach { f ->
                factsJson.put(
                    JSONObject()
                        .put("targetId", f.targetId)
                        .put("text", f.text)
                        .put("kind", f.kind)
                        .put("expiresAt", f.expiresAt ?: JSONObject.NULL)
                        .put("source", f.source)
                        .put("createdAt", f.createdAt),
                )
            }
        }
        // profile 仅四个字段（与桌面端 exportAllJson 一致，不含 createdAt——本端合并导入缺省回当前时间）
        val profile = db.profileDao().getLatest()?.let { p ->
            JSONObject()
                .put("mbti", p.mbti ?: JSONObject.NULL)
                .put("score", p.score ?: JSONObject.NULL)
                .put("strengths", p.strengths ?: JSONObject.NULL)
                .put("weaknesses", p.weaknesses ?: JSONObject.NULL)
        } ?: JSONObject.NULL
        return JSONObject()
            .put("app", "wenyan-android")
            .put("version", 1)
            .put("exportedAt", now)
            .put("profile", profile)
            .put("targets", targetsJson)
            .put("facts", factsJson)
            .toString()
    }

    /**
     * v1.9.4 记忆合并导入（换机迁移）：绝不清表、绝不删除现有数据（与 restore() 的清表重建相反）。
     * 兼容两种文件：本端记忆导出（wenyan-android）与桌面端全量导出（wenyan-desktop，
     * 其 targets/facts/profile 段字段同构，providers/models/sessions/messages 段直接忽略——
     * 顺带支持「桌面 → 手机」换机只带走记忆）。
     * 事务包裹（withTransaction）：任一步失败整体回滚，不产生半份导入。
     * @return (是否成功, 中文结果描述)；文件不是温言导出/版本非法返回 (false, 原因)
     */
    suspend fun importMemoryMerge(json: JSONObject): Pair<Boolean, String> {
        validateMemoryExportHeader(json)?.let { return false to it }
        return runCatchingCancellable {
            db.withTransaction {
                mergeMemoryImport(db.targetDao(), db.memoryFactDao(), db.profileDao(), json)
            }
        }.fold(
            onSuccess = { it },
            onFailure = { false to "导入失败：${it.message ?: "数据损坏"}" },
        )
    }
}

/**
 * 温言记忆导出文件头校验：app 标识（wenyan-android / wenyan-desktop）+ 版本号 ≥1。
 * 非法返回中文原因，合法返回 null。
 * importMemoryMerge（事务外快速失败）与 mergeMemoryImport（核心函数自身输入防御，
 * 亦供纯 JVM 单测直接覆盖）共用；校验幂等，合法文件两处零额外行为。
 */
private fun validateMemoryExportHeader(json: JSONObject): String? {
    val app = json.optString("app", "")
    if (app != "wenyan-android" && app != "wenyan-desktop") {
        return "不是温言记忆导出文件"
    }
    if (json.optInt("version", -1) < 1) {
        return "记忆文件版本无效或过低"
    }
    return null
}

/**
 * v1.9.4 合并导入核心逻辑（internal 提出为独立函数：只依赖三个 DAO，纯 JVM 可单测；
 * 生产路径由 BackupRepository.importMemoryMerge 包 withTransaction + runCatchingCancellable，行为不变）。
 * 合并规则：
 * - targets 按 codeName（trim 后精确匹配）对上 → 沿用本地 id；对不上 → 插入新档案
 *   （mbti/score/relationStatus/timeline/note/createdAt 原样保留）；
 * - facts 在 targetId 重映射后按 text 精确相等去重，只插增量；每档案总数 ≤50
 *   （MemoryExtractor.DEFAULT_FACT_LIMIT，与提炼链路上限一致），超出跳过；
 *   expiresAt/source/kind/createdAt 原样保留；
 * - profile 仅当本地无档案（getLatest()==null）时写入，不覆盖现有用户档案。
 * @return (true, 中文结果描述)；DAO 异常向上抛出由事务层统一兜底为 (false, 原因)
 */
internal suspend fun mergeMemoryImport(
    targetDao: TargetDao,
    memoryFactDao: MemoryFactDao,
    profileDao: ProfileDao,
    json: JSONObject,
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
        // M7 同款防御：text 为 JSON null 时 optString 会返回字面量 "null"，先 isNull 预检
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
    if (jsonProfile != null && jsonProfile !== JSONObject.NULL && profileDao.getLatest() == null) {
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
