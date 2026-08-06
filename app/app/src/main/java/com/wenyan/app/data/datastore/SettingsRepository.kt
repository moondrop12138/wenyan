package com.wenyan.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    suspend fun getCurrentModelId(): Long? = currentModelId.first()
    suspend fun getVisionModelId(): Long? = visionModelId.first()
    suspend fun getTheme(): String = theme.first()
    suspend fun isOnboardingCompleted(): Boolean = onboardingCompleted.first()
    suspend fun isPrivacyAcked(): Boolean = privacyAck.first()
    suspend fun getActiveTargetId(): Long? = activeTargetId.first()
    suspend fun isMemoryAutoEnabled(): Boolean = memoryAutoEnabled.first()

    /**
     * 一键清除全部设置（AC-12 隐私清除；自动覆盖 v1.7.2 新 key）
     */
    suspend fun clearAll() {
        context.settingsDataStore.edit { it.clear() }
    }
}
