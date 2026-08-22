package com.wenyan.app.ui.settings

import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.SettingsRepository
import com.wenyan.app.ui.contract.TargetUi
import com.wenyan.app.ui.contract.UsageMetricsUi
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * v1.7.2 SettingsViewModel「记忆」分组状态装配测试（fake SettingsRepository）：
 * 列表装配 / 激活+Toast / 新建自动激活 / 改名 / 删除回退 / 自动记忆开关。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelMemoryTest {

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
    fun `targets collected from repository`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(
                TargetUi(1, "小A", "", 0, isActive = false),
                TargetUi(2, "小B", "", 0, isActive = true, factCount = 3),
            )
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        assertEquals(2, vm.targets.value.size)
        assertEquals(true, vm.targets.value[1].isActive)
        // v1.7.3-fix：事实数字段透传到 VM（设置页 caption 展示「已记住 3 条」）
        assertEquals(3, vm.targets.value[1].factCount)
    }

    @Test
    fun `setActiveTarget delegates and emits toast`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(TargetUi(1, "小A", "", 0, isActive = false))
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setActiveTarget(TargetUi(1, "小A", "", 0, isActive = false))
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.activated)
        assertEquals("已切换到「小A」的记忆", vm.toastMessage.value)
    }

    @Test
    fun `createTarget auto activates when none active`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.createTarget("小A")
        advanceUntilIdle()
        assertEquals(listOf("小A"), repo.created)
        assertEquals(1L, repo.activeFlow.value)
    }

    @Test
    fun `updateTarget delegates and closes edit dialog`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(TargetUi(1, "小A", "旧正文", 0, isActive = false))
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.requestEditTarget(TargetUi(1, "小A", "旧正文", 0, isActive = false))
        assertEquals(1L, vm.editTarget.value?.id)
        vm.updateTarget(1, "小A2", "新正文")
        advanceUntilIdle()
        assertEquals(Triple(1L, "小A2", "新正文"), repo.updated.single())
        assertNull(vm.editTarget.value)
    }

    @Test
    fun `delete active target falls back to first remaining`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(
                TargetUi(1, "小A", "", 0, isActive = false),
                TargetUi(2, "小B", "", 0, isActive = true),
            )
            activeFlow.value = 2L
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.deleteTarget(2L)
        advanceUntilIdle()
        assertEquals(listOf(2L), repo.deleted)
        assertEquals(1L, repo.activeFlow.value)
    }

    @Test
    fun `delete non-active target keeps active unchanged`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(
                TargetUi(1, "小A", "", 0, isActive = false),
                TargetUi(2, "小B", "", 0, isActive = true),
            )
            activeFlow.value = 2L
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.deleteTarget(1L)
        advanceUntilIdle()
        assertEquals(2L, repo.activeFlow.value)
    }

    @Test
    fun `memory auto toggle collected and delegated`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        assertEquals(true, vm.memoryAutoEnabled.value)
        vm.setMemoryAutoEnabled(false)
        advanceUntilIdle()
        assertEquals(false, repo.memoryAutoFlow.value)
        assertEquals(false, vm.memoryAutoEnabled.value)
    }

    @Test
    fun `consumeToast clears message`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setActiveTarget(TargetUi(1, "小A", "", 0, isActive = false))
        advanceUntilIdle()
        assertEquals("已切换到「小A」的记忆", vm.toastMessage.value)
        vm.consumeToast()
        assertNull(vm.toastMessage.value)
    }
}

/**
 * 内存 SettingsRepository（v1.7.2 记忆契约镜像 RealSettingsRepository 行为：
 * 新建自动激活 / 删激活项回退第一个 / 激活标记刷新）。
 */
private class FakeSettingsRepository : SettingsRepository {

    val targetsFlow = MutableStateFlow<List<TargetUi>>(emptyList())
    val activeFlow = MutableStateFlow<Long?>(null)
    val memoryAutoFlow = MutableStateFlow(true)
    val created = mutableListOf<String>()
    val updated = mutableListOf<Triple<Long, String, String>>()
    val deleted = mutableListOf<Long>()
    val activated = mutableListOf<Long>()

    override val providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    override val models = MutableStateFlow<List<ModelInfo>>(emptyList())
    override val currentModelId = MutableStateFlow<Long?>(null)
    override val visionModelId = MutableStateFlow<Long?>(null)
    override val themeMode = MutableStateFlow("system")
    override val privacyAck = MutableStateFlow(false)
    override val targets: Flow<List<TargetUi>> = targetsFlow
    override val activeTargetId: Flow<Long?> = activeFlow
    override val memoryAutoEnabled: Flow<Boolean> = memoryAutoFlow

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
        updated.add(Triple(id, name, note))
        targetsFlow.value = targetsFlow.value.map {
            if (it.id == id) it.copy(name = name.trim(), note = note.trim()) else it
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

    override fun observeFacts(targetId: Long): Flow<List<com.wenyan.app.ui.contract.MemoryFactUi>> =
        MutableStateFlow(emptyList())
    override suspend fun addFact(targetId: Long, text: String) = Unit
    override suspend fun updateFact(factId: Long, text: String) = Unit
    override suspend fun deleteFact(factId: Long) = Unit
    override suspend fun makePermanent(factId: Long) = Unit
    override suspend fun undoLastMemoryWrite(): List<Long> = emptyList()
    override suspend fun updateTargetDetails(
        id: Long, name: String, mbti: String?, score: Int?, relationStatus: String?, timelineJson: String,
    ) = Unit
    override suspend fun exportCrashLog(): android.net.Uri? = null
    override suspend fun checkUpdate(): com.wenyan.app.data.update.UpdateCheckResult =
        com.wenyan.app.data.update.UpdateCheckResult.UpToDate
    override suspend fun downloadUpdateApk(info: com.wenyan.app.data.update.UpdateInfo): java.io.File? = null
    override suspend fun installApk(file: java.io.File): Boolean = false

    override suspend fun setCurrentModel(id: Long) = Unit
    override suspend fun setVisionModel(id: Long) = Unit
    override suspend fun setThemeMode(mode: String) = Unit
    override suspend fun testConnection(providerId: Long): LlmError? = null
    override suspend fun saveProvider(name: String, baseUrl: String, apiKey: String, isPreset: Boolean): Long = 0L
    override suspend fun updateProvider(id: Long, name: String, baseUrl: String, apiKey: String?) = Unit
    override suspend fun deleteProviderApiKey(providerId: Long) = Unit
    override suspend fun deleteProvider(id: Long) = Unit
    override suspend fun getProviderApiKey(providerId: Long): String? = null
    override suspend fun addModel(providerId: Long, name: String, supportsVision: Boolean) = Unit
    override suspend fun deleteModel(id: Long) = Unit
    override suspend fun toggleSheetVisible(id: Long) = Unit
    override suspend fun setVisionFlag(id: Long, supportsVision: Boolean) = Unit
    override suspend fun markConnectionStatus(providerId: Long, ok: Boolean) = Unit
    override suspend fun wipeAll() = Unit
    override suspend fun importBackup(uri: android.net.Uri): Pair<Boolean, String> = false to "测试未实现"
    override fun usageMetrics(): UsageMetricsUi = UsageMetricsUi(0L, 0L, 0L, 0L, emptyMap())
    override suspend fun setPrivacyAck(ack: Boolean) = Unit
}
