package com.wenyan.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 设置项 DataStore（db-schema §3）
 * current_model_id / vision_model_id / theme / onboarding_completed / privacy_ack
 * v1.7.2 新增：active_target_id（激活记忆档案）/ memory_auto_enabled（自动记忆开关，默认开）
 * v1.9.0 新增：memory_write_log（最近自动写入日志，供撤销最近一次；JSON 数组，最多 5 条）
 * v1.9.4 新增：current_session_id（冷启动恢复）/ fluid_background_enabled（流光背景开关）
 * v1.9.4 三改新增：fluid_hue（流光色相 0-360，默认 0）/ bg_brightness（背景亮度 0-100，默认 50）
 * clearAll() 一键清全部 key（含新 key），隐私清除自动覆盖
 */
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CURRENT_MODEL_ID = longPreferencesKey("current_model_id")
        val VISION_MODEL_ID = longPreferencesKey("vision_model_id")
        val THEME = stringPreferencesKey("theme")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PRIVACY_ACK = booleanPreferencesKey("privacy_ack")
        /** v1.7.2 激活记忆档案 id（新会话默认归属；null = 无激活档案） */
        val ACTIVE_TARGET_ID = longPreferencesKey("active_target_id")
        /** v1.7.2 自动记忆开关（默认开；关闭后回复完成不再提炼） */
        val MEMORY_AUTO_ENABLED = booleanPreferencesKey("memory_auto_enabled")
        /** v1.9.0 自动记忆写入日志（JSON 数组字符串，最近在前，≤5 条） */
        val MEMORY_WRITE_LOG = stringPreferencesKey("memory_write_log")
        /** v1.9.4 上次打开的会话 id（隔夜冷启动恢复上次对话；不存在 = 无） */
        val CURRENT_SESSION_ID = longPreferencesKey("current_session_id")
        /** v1.9.4 流光背景开关（默认开；关闭后 FluidBackground 不绘制，露出主题底色） */
        val FLUID_BACKGROUND_ENABLED = booleanPreferencesKey("fluid_background_enabled")
        /** v1.9.4 三改 流光色相（0-360 度，默认 0 = 主题原色；旋转 fluidA/B/C 三基色） */
        val FLUID_HUE = intPreferencesKey("fluid_hue")
        /** v1.9.4 三改 背景亮度（0-100，默认 50 = 不叠 veil，观感与不可调版本完全一致） */
        val BG_BRIGHTNESS = intPreferencesKey("bg_brightness")
    }

    val currentModelId: Flow<Long?> =
        context.settingsDataStore.data.map { it[Keys.CURRENT_MODEL_ID] }

    val visionModelId: Flow<Long?> =
        context.settingsDataStore.data.map { it[Keys.VISION_MODEL_ID] }

    val theme: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.THEME] ?: "system" }

    val onboardingCompleted: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }

    val privacyAck: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.PRIVACY_ACK] ?: false }

    /** v1.7.2 激活记忆档案 id（null = 无激活档案） */
    val activeTargetId: Flow<Long?> =
        context.settingsDataStore.data.map { it[Keys.ACTIVE_TARGET_ID] }

    /** v1.7.2 自动记忆开关（默认 true） */
    val memoryAutoEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.MEMORY_AUTO_ENABLED] ?: true }

    /** v1.9.4 流光背景开关（默认 true） */
    val fluidBackgroundEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.FLUID_BACKGROUND_ENABLED] ?: true }

    /**
     * v1.9.4 三改 流光色相（度，默认 0；越界值在读取与写入两侧都夹到 0..360：
     * 读到脏数据（旧版本写入 / 手工改 prefs）也不会把 UI 滑条顶出范围）
     */
    val fluidHue: Flow<Int> =
        context.settingsDataStore.data.map { (it[Keys.FLUID_HUE] ?: 0).coerceIn(0, 360) }

    /** v1.9.4 三改 背景亮度（0-100，默认 50 = 中点 = 不叠 veil；同样双向夹取） */
    val bgBrightness: Flow<Int> =
        context.settingsDataStore.data.map { (it[Keys.BG_BRIGHTNESS] ?: 50).coerceIn(0, 100) }

    suspend fun setCurrentModelId(id: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (id != null) prefs[Keys.CURRENT_MODEL_ID] = id else prefs.remove(Keys.CURRENT_MODEL_ID)
        }
    }

    suspend fun setVisionModelId(id: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (id != null) prefs[Keys.VISION_MODEL_ID] = id else prefs.remove(Keys.VISION_MODEL_ID)
        }
    }

    suspend fun setTheme(value: String) {
        context.settingsDataStore.edit { it[Keys.THEME] = value }
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }

    suspend fun setPrivacyAck(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.PRIVACY_ACK] = value }
    }

    /** v1.7.2 设置激活记忆档案（null = 清除激活，用于删空档案后回退） */
    suspend fun setActiveTargetId(id: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (id != null) prefs[Keys.ACTIVE_TARGET_ID] = id else prefs.remove(Keys.ACTIVE_TARGET_ID)
        }
    }

    /** v1.7.2 自动记忆开关 */
    suspend fun setMemoryAutoEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MEMORY_AUTO_ENABLED] = enabled }
    }

    /** v1.9.4 流光背景开关 */
    suspend fun setFluidBackgroundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.FLUID_BACKGROUND_ENABLED] = enabled }
    }

    /** v1.9.4 三改 流光色相（度）：写前夹到 0..360（UI 滑条已受限，此处兜底防越界值落盘） */
    suspend fun setFluidHue(degrees: Int) {
        context.settingsDataStore.edit { it[Keys.FLUID_HUE] = degrees.coerceIn(0, 360) }
    }

    /** v1.9.4 三改 背景亮度（0-100，50 = 中点）：写前夹取 */
    suspend fun setBgBrightness(value: Int) {
        context.settingsDataStore.edit { it[Keys.BG_BRIGHTNESS] = value.coerceIn(0, 100) }
    }

    // ===== v1.9.4 当前会话 id 持久化（隔夜冷启动恢复上次对话） =====

    /** v1.9.4 读取上次持久化的会话 id（不存在 = 无，冷启动保持空态） */
    suspend fun getCurrentSessionId(): Long? =
        context.settingsDataStore.data.map { it[Keys.CURRENT_SESSION_ID] }.first()

    /** v1.9.4 写穿当前会话 id（null = 清键：新建空会话 / 删除当前会话 / 残留键清理） */
    suspend fun setCurrentSessionId(id: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (id != null) prefs[Keys.CURRENT_SESSION_ID] = id else prefs.remove(Keys.CURRENT_SESSION_ID)
        }
    }

    // ===== v1.9.0 自动记忆写入日志（撤销最近一次） =====

    /** 一次自动写入的日志条目 */
    data class MemoryWriteLogEntry(
        val targetId: Long,
        val factIds: List<Long>,
        val summary: String,
        val createdAt: Long,
    )

    /** 最近一次自动写入日志（无则 null） */
    suspend fun lastMemoryWrite(): MemoryWriteLogEntry? =
        readMemoryWriteLog().firstOrNull()

    /**
     * 撤销最近一次自动写入：返回被撤销的 fact id 列表（空 = 无日志可撤销）。
     * L22 修复：读取原在 edit 事务外——与 recordMemoryWrite（edit 内读）并发时 TOCTOU：
     * 撤销日志条目丢失，对应事实永远无法撤销。现把读移进 edit 内，读-改-写原子化。
     */
    suspend fun undoLastMemoryWrite(): List<Long> {
        var undoneFactIds: List<Long> = emptyList()
        context.settingsDataStore.edit { prefs ->
            val raw = prefs[Keys.MEMORY_WRITE_LOG] ?: ""
            val log = MemoryWriteLogCodec.decode(raw)
            val last = log.firstOrNull()
            if (last != null) {
                undoneFactIds = last.factIds
                val rest = log.drop(1)
                if (rest.isEmpty()) prefs.remove(Keys.MEMORY_WRITE_LOG)
                else prefs[Keys.MEMORY_WRITE_LOG] = MemoryWriteLogCodec.encode(rest)
            }
        }
        return undoneFactIds
    }

    /** 记录一次自动写入（最近在前，截断保留 5 条） */
    suspend fun recordMemoryWrite(targetId: Long, factIds: List<Long>, summary: String) {
        if (factIds.isEmpty()) return
        val entry = MemoryWriteLogEntry(
            targetId = targetId,
            factIds = factIds,
            summary = summary,
            createdAt = System.currentTimeMillis(),
        )
        context.settingsDataStore.edit { prefs ->
            val updated = (listOf(entry) + readMemoryWriteLog()).take(5)
            prefs[Keys.MEMORY_WRITE_LOG] = MemoryWriteLogCodec.encode(updated)
        }
    }

    private suspend fun readMemoryWriteLog(): List<MemoryWriteLogEntry> {
        val raw = context.settingsDataStore.data.map { it[Keys.MEMORY_WRITE_LOG] ?: "" }.first()
        return MemoryWriteLogCodec.decode(raw)
    }

    /** 内存级编解码（纯函数，JVM 可测；格式：targetId,factId:factId,summary,createdAt 换行分隔） */
    object MemoryWriteLogCodec {
        private const val FIELD_SEP = ","
        private const val LINE_SEP = "\n"
        private const val ID_SEP = ":"

        fun encode(entries: List<MemoryWriteLogEntry>): String = entries.joinToString(LINE_SEP) { e ->
            e.targetId.toString() + FIELD_SEP +
                e.factIds.joinToString(ID_SEP) + FIELD_SEP +
                e.summary.replace('\n', ' ').replace(',', '，') + FIELD_SEP +
                e.createdAt.toString()
        }

        fun decode(raw: String): List<MemoryWriteLogEntry> {
            if (raw.isBlank()) return emptyList()
            return raw.split(LINE_SEP).mapNotNull { line ->
                val parts = line.split(FIELD_SEP)
                if (parts.size < 4) return@mapNotNull null
                val ids = parts[1].split(ID_SEP).mapNotNull { it.toLongOrNull() }
                if (ids.isEmpty()) return@mapNotNull null
                MemoryWriteLogEntry(
                    targetId = parts[0].toLongOrNull() ?: 0L,
                    factIds = ids,
                    summary = parts[2],
                    createdAt = parts[3].toLongOrNull() ?: 0L,
                )
            }
        }
    }

    suspend fun getCurrentModelId(): Long? = currentModelId.first()
    suspend fun getVisionModelId(): Long? = visionModelId.first()
    suspend fun getTheme(): String = theme.first()
    suspend fun isOnboardingCompleted(): Boolean = onboardingCompleted.first()
    suspend fun isPrivacyAcked(): Boolean = privacyAck.first()
    suspend fun getActiveTargetId(): Long? = activeTargetId.first()
    suspend fun isMemoryAutoEnabled(): Boolean = memoryAutoEnabled.first()

    /**
     * 一键清除全部设置（AC-12 隐私清除；自动覆盖 v1.7.2/v1.9.4 新 key，
     * 含 current_session_id —— 清空数据后冷启动不再恢复已删除会话）
     */
    suspend fun clearAll() {
        context.settingsDataStore.edit { it.clear() }
    }
}
