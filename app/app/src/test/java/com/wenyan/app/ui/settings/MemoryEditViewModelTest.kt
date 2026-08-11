package com.wenyan.app.ui.settings

import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.MemoryFactUi
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.SettingsRepository
import com.wenyan.app.ui.contract.TargetUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * v1.7.4 MemoryEditViewModel 前置搬移测试（fake SettingsRepository）：
 * 打开档案详情页 init 即触发 ensureMigrated（老 note 数据不再因手工加事实而丢失）。
 * 注意：fake 类名避开 FakeSettingsRepository（同包 JVM 类名冲突会引发符号解析错乱）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init triggers ensureMigrated for target`() = runTest(dispatcher) {
        val repo = FakeSettingsRepoForMemoryEdit().apply {
            targetsFlow.value = listOf(TargetUi(1, "小A", "她喜欢猫", 0, isActive = false))
        }
        val vm = MemoryEditViewModel(repo, targetId = 1L)
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.ensureMigratedCalls)
        assertEquals("小A", vm.target?.name)
    }
}

/** 最小内存 SettingsRepository（仅 MemoryEditViewModel 所需行为，其余空实现） */
private class FakeSettingsRepoForMemoryEdit : SettingsRepository {
    val targetsFlow = MutableStateFlow<List<TargetUi>>(emptyList())
    val ensureMigratedCalls = mutableListOf<Long>()

    override val providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    override val models = MutableStateFlow<List<ModelInfo>>(emptyList())
    override val currentModelId = MutableStateFlow<Long?>(null)
    override val visionModelId = MutableStateFlow<Long?>(null)
    override val themeMode = MutableStateFlow("system")
    override val privacyAck = MutableStateFlow(false)
    override val targets: Flow<List<TargetUi>> = targetsFlow
    override val activeTargetId = MutableStateFlow<Long?>(null)
    override val memoryAutoEnabled = MutableStateFlow(true)

    override fun observeFacts(targetId: Long): Flow<List<MemoryFactUi>> = MutableStateFlow(emptyList())

    override suspend fun ensureMigrated(targetId: Long) {
        ensureMigratedCalls.add(targetId)
    }

    override suspend fun setCurrentModel(id: Long) = Unit
    override suspend fun setVisionModel(id: Long) = Unit
    override suspend fun setThemeMode(mode: String) = Unit
    override suspend fun testConnection(providerId: Long): LlmError? = null
    override suspend fun saveProvider(name: String, baseUrl: String, apiKey: String, isPreset: Boolean): Long = 0L
    override suspend fun updateProvider(id: Long, name: String, baseUrl: String, apiKey: String?) = Unit
    override suspend fun deleteProvider(id: Long) = Unit
    override suspend fun getProviderApiKey(providerId: Long): String? = null
    override suspend fun addModel(providerId: Long, name: String, supportsVision: Boolean) = Unit
    override suspend fun deleteModel(id: Long) = Unit
    override suspend fun toggleSheetVisible(id: Long) = Unit
    override suspend fun setVisionFlag(id: Long, supportsVision: Boolean) = Unit
    override suspend fun markConnectionStatus(providerId: Long, ok: Boolean) = Unit
    override suspend fun wipeAll() = Unit
    override suspend fun setPrivacyAck(ack: Boolean) = Unit
    override suspend fun createTarget(name: String): Long = 0L
    override suspend fun updateTarget(id: Long, name: String, note: String) = Unit
    override suspend fun deleteTarget(id: Long) = Unit
    override suspend fun setActiveTarget(id: Long) = Unit
    override suspend fun setMemoryAutoEnabled(enabled: Boolean) = Unit
    override suspend fun addFact(targetId: Long, text: String) = Unit
    override suspend fun updateFact(factId: Long, text: String) = Unit
    override suspend fun deleteFact(factId: Long) = Unit
    override suspend fun undoLastMemoryWrite(): List<Long> = emptyList()
    override suspend fun updateTargetDetails(
        id: Long, name: String, mbti: String?, score: Int?, relationStatus: String?, timelineJson: String,
    ) = Unit
    override suspend fun exportCrashLog(): android.net.Uri? = null
    override suspend fun checkUpdate(): com.wenyan.app.data.update.UpdateCheckResult =
        com.wenyan.app.data.update.UpdateCheckResult.UpToDate
    override suspend fun downloadUpdateApk(info: com.wenyan.app.data.update.UpdateInfo): java.io.File? = null
    override suspend fun installApk(file: java.io.File): Boolean = false
}
