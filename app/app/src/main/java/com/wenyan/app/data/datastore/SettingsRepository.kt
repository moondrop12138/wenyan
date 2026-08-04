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
 */
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CURRENT_MODEL_ID = longPreferencesKey("current_model_id")
        val VISION_MODEL_ID = longPreferencesKey("vision_model_id")
        val THEME = stringPreferencesKey("theme")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PRIVACY_ACK = booleanPreferencesKey("privacy_ack")
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

    suspend fun getCurrentModelId(): Long? = currentModelId.first()
    suspend fun getVisionModelId(): Long? = visionModelId.first()
    suspend fun getTheme(): String = theme.first()
    suspend fun isOnboardingCompleted(): Boolean = onboardingCompleted.first()
    suspend fun isPrivacyAcked(): Boolean = privacyAck.first()

    /**
     * 一键清除全部设置（AC-12 隐私清除）
     */
    suspend fun clearAll() {
        context.settingsDataStore.edit { it.clear() }
    }
}
