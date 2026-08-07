package com.wenyan.app.ui.settings

import android.net.Uri
import com.wenyan.app.data.update.UpdateCheckResult
import com.wenyan.app.data.update.UpdateInfo
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.contract.ChatRepository
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.MemoryFactUi
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.OnboardingRepository
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.SettingsRepository
import com.wenyan.app.ui.contract.TargetUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** v1.7.3 androidTest 共用内存 SettingsRepository（镜像契约行为） */
class FakeSettingsRepository : SettingsRepository {

    val targetsFlow = MutableStateFlow<List<TargetUi>>(emptyList())
    val activeFlow = MutableStateFlow<Long?>(null)
    val memoryAutoFlow = MutableStateFlow(true)
    val factsFlow = MutableStateFlow<List<MemoryFactUi>>(emptyList())
    val created = mutableListOf<String>()
    val activated = mutableListOf<Long>()
    val deleted = mutableListOf<Long>()
    val edited = mutableListOf<Triple<Long, String, String>>()

    override val providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    override val models = MutableStateFlow<List<ModelInfo>>(emptyList())
    override val currentModelId = MutableStateFlow<Long?>(null)
    override val visionModelId = MutableStateFlow<Long?>(null)
    override val themeMode = MutableStateFlow("system")
    override val privacyAck = MutableStateFlow(false)
    override val targets: Flow<List<TargetUi>> = targetsFlow
    override val activeTargetId: Flow<Long?> = activeFlow
    override val memoryAutoEnabled: Flow<Boolean> = memoryAutoFlow

    override fun observeFacts(targetId: Long): Flow<List<MemoryFactUi>> = factsFlow

    override suspend fun createTarget(name: String): Long {
        created.add(name)
        val id = (targetsFlow.value.maxOfOrNull { it.id } ?: 0L) + 1
        targetsFlow.value = targetsFlow.value + TargetUi(
            id = id, name = name.trim(), note = "", createdAt = 0L,
            isActive = activeFlow.value == null,
        )
        if (activeFlow.value == null) activeFlow.value = id
        return id
    }

    override suspend fun updateTarget(id: Long, name: String, note: String) {
        edited.add(Triple(id, name, note))
        targetsFlow.value = targetsFlow.value.map {
            if (it.id == id) it.copy(name = name.trim()) else it
        }
    }

    override suspend fun deleteTarget(id: Long) {
        deleted.add(id)
        targetsFlow.value = targetsFlow.value.filterNot { it.id == id }
        if (activeFlow.value == id) {
            activeFlow.value = targetsFlow.value.firstOrNull()?.id
        }
    }

    override suspend fun setActiveTarget(id: Long) {
        activated.add(id)
        activeFlow.value = id
        targetsFlow.value = targetsFlow.value.map { it.copy(isActive = it.id == id) }
    }

    override suspend fun ensureMigrated(targetId: Long) = Unit

    override suspend fun setMemoryAutoEnabled(enabled: Boolean) {
        memoryAutoFlow.value = enabled
    }

    override suspend fun addFact(targetId: Long, text: String) = Unit
    override suspend fun updateFact(factId: Long, text: String) = Unit
    override suspend fun deleteFact(factId: Long) = Unit
    override suspend fun updateTargetDetails(
        id: Long, name: String, mbti: String?, score: Int?, relationStatus: String?, timelineJson: String,
    ) = Unit
    override suspend fun exportCrashLog(): Uri? = null
    override suspend fun checkUpdate(): UpdateCheckResult = UpdateCheckResult.UpToDate
    override suspend fun downloadUpdateApk(info: UpdateInfo): java.io.File? = null
    override suspend fun installApk(file: java.io.File): Boolean = false

    override suspend fun setCurrentModel(id: Long) = Unit
    override suspend fun setVisionModel(id: Long) = Unit
    override suspend fun setThemeMode(mode: String) = Unit
    override suspend fun testConnection(providerId: Long): LlmError? = null
    override suspend fun saveProvider(name: String, baseUrl: String, apiKey: String, isPreset: Boolean): Long = 0L
    override suspend fun updateProvider(id: Long, name: String, baseUrl: String, apiKey: String?) = Unit
    override suspend fun deleteProvider(id: Long) = Unit
    override suspend fun addModel(providerId: Long, name: String, supportsVision: Boolean) = Unit
    override suspend fun deleteModel(id: Long) = Unit
    override suspend fun toggleSheetVisible(id: Long) = Unit
    override suspend fun setVisionFlag(id: Long, supportsVision: Boolean) = Unit
    override suspend fun markConnectionStatus(providerId: Long, ok: Boolean) = Unit
    override suspend fun wipeAll() = Unit
    override suspend fun setPrivacyAck(ack: Boolean) = Unit
}

/** v1.7.3 androidTest 共用内存 AppContainer（settings 注入 FakeSettingsRepository） */
class FakeAppContainer(
    val settings: FakeSettingsRepository = FakeSettingsRepository(),
) : AppContainer {
    override val settingsRepository: SettingsRepository = settings
    override val onboardingRepository: OnboardingRepository = object : OnboardingRepository {
        override val onboardingCompleted: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun submit(draft: com.wenyan.app.ui.contract.OnboardingDraft) = Unit
        override suspend fun skip() = Unit
    }
    override val chatRepository: ChatRepository = object : ChatRepository {
        override val messages: Flow<List<com.wenyan.app.ui.contract.ChatMessageUi>> = MutableStateFlow(emptyList())
        override val sessions: Flow<List<com.wenyan.app.ui.contract.SessionSummaryUi>> = MutableStateFlow(emptyList())
        override val currentSessionId: Flow<Long?> = MutableStateFlow(null)
        override val streamingState = MutableStateFlow(com.wenyan.app.ui.contract.StreamingState())
        override val currentModelName: Flow<String> = MutableStateFlow("未配置")
        override fun sendText(text: String, mode: com.wenyan.app.ui.contract.AnalysisMode) = kotlinx.coroutines.flow.flowOf(com.wenyan.app.ui.contract.StreamEvent.Done)
        override fun analyzeImages(uris: List<Uri>, text: String, mode: com.wenyan.app.ui.contract.AnalysisMode) = kotlinx.coroutines.flow.flowOf(com.wenyan.app.ui.contract.StreamEvent.Done)
        override fun confirmTranscription(transcription: String) = kotlinx.coroutines.flow.flowOf(com.wenyan.app.ui.contract.StreamEvent.Done)
        override fun sendTextAsync(text: String, mode: com.wenyan.app.ui.contract.AnalysisMode, persistUser: Boolean) = Unit
        override fun analyzeImagesAsync(uris: List<Uri>, text: String, mode: com.wenyan.app.ui.contract.AnalysisMode, persistUser: Boolean) = Unit
        override fun confirmTranscriptionAsync(transcription: String) = Unit
        override suspend fun deleteMessage(messageId: Long) = Unit
        override suspend fun switchSession(sessionId: Long) = Unit
        override suspend fun startNewSession() = Unit
        override suspend fun deleteSession(sessionId: Long) = Unit
        override fun cancel() = Unit
    }
}
