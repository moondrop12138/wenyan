package com.wenyan.app.ui.settings

import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ProviderEditViewModel AC-18 隐私门意图保留测试（v1.1 修复）：
 * addModel 触发隐私门后，确认时应先存 provider 再续加模型，不丢失添加意图。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderEditViewModelTest {

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
    fun `addModel behind privacy gate saves provider then adds model`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = ProviderEditViewModel(repo, providerId = 0L) // isNew
        vm.apiKey = "sk-test"
        vm.newModelName = "gpt-test"

        vm.addModel()

        // 隐私门拦截，意图以完整 SaveAndAddModel 保留
        assertTrue(vm.showPrivacyDialog)
        val pending = vm.pendingAction
        assertTrue(pending is ProviderEditViewModel.PendingAction.SaveAndAddModel)
        pending as ProviderEditViewModel.PendingAction.SaveAndAddModel
        assertEquals("gpt-test", pending.modelName)

        vm.acceptPrivacy()
        advanceUntilIdle()

        // 先持久化 ack，再按原意图：存 provider + 加模型（v1.6.3 新增默认非视觉）
        assertEquals(true, repo.privacyAckValue)
        assertEquals(listOf(Triple(1L, "gpt-test", false)), repo.addedModels)
        assertEquals("", vm.newModelName)
    }

    @Test
    fun `save behind privacy gate still only saves`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = ProviderEditViewModel(repo, providerId = 0L)
        vm.apiKey = "sk-test"

        vm.save {}
        assertTrue(vm.showPrivacyDialog)
        assertEquals(ProviderEditViewModel.PendingAction.Save, vm.pendingAction)

        vm.acceptPrivacy()
        advanceUntilIdle()

        assertEquals(true, repo.privacyAckValue)
        assertTrue(repo.addedModels.isEmpty())
    }

    @Test
    fun `addModel without privacy gate adds directly`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = ProviderEditViewModel(repo, providerId = 0L)
        vm.privacyAck = true // 已确认过
        vm.newModelName = "gpt-fast"

        vm.addModel()
        advanceUntilIdle()

        assertFalse(vm.showPrivacyDialog)
        assertEquals(listOf(Triple(1L, "gpt-fast", false)), repo.addedModels)
    }

    // v1.6.3 保存后自动测试连接并写入红绿灯状态
    @Test
    fun `save tests connection and marks green when ok`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = ProviderEditViewModel(repo, providerId = 0L)
        vm.privacyAck = true
        vm.apiKey = "sk-ok"
        var done = false

        vm.save { done = true }
        advanceUntilIdle()

        assertEquals(1, repo.testConnectionCalls)
        assertEquals(listOf(1L to true), repo.connectionStatusCalls)
        assertTrue(done)
    }

    @Test
    fun `save tests connection and marks red when failed`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        repo.testConnectionResult = LlmError("401", "invalid key", retryable = false)
        val vm = ProviderEditViewModel(repo, providerId = 0L)
        vm.privacyAck = true
        vm.apiKey = "sk-bad"

        vm.save {}
        advanceUntilIdle()

        assertEquals(listOf(1L to false), repo.connectionStatusCalls)
    }

    @Test
    fun `save without api key marks red and skips test`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = ProviderEditViewModel(repo, providerId = 0L)
        vm.privacyAck = true
        vm.apiKey = "" // 未填 Key：直接红灯，不发起测试

        vm.save {}
        advanceUntilIdle()

        assertEquals(0, repo.testConnectionCalls)
        assertEquals(listOf(1L to false), repo.connectionStatusCalls)
    }

    /** 轻量内存版 SettingsRepository（仅测试 ProviderEditViewModel 所需行为） */
    private class FakeSettingsRepository : SettingsRepository {
        override val providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
        override val models = MutableStateFlow<List<ModelInfo>>(emptyList())
        override val currentModelId = MutableStateFlow<Long?>(null)
        override val visionModelId = MutableStateFlow<Long?>(null)
        override val themeMode = MutableStateFlow("system")
        override val privacyAck = MutableStateFlow(false)

        val addedModels = mutableListOf<Triple<Long, String, Boolean>>()
        var privacyAckValue = false
        var testConnectionCalls = 0
        var testConnectionResult: LlmError? = null
        val connectionStatusCalls = mutableListOf<Pair<Long, Boolean>>()

        override suspend fun setCurrentModel(id: Long) = Unit
        override suspend fun setVisionModel(id: Long) = Unit
        override suspend fun setThemeMode(mode: String) = Unit
        override suspend fun testConnection(providerId: Long): LlmError? {
            testConnectionCalls++
            return testConnectionResult
        }
        override suspend fun saveProvider(name: String, baseUrl: String, apiKey: String, isPreset: Boolean): Long = 1L
        override suspend fun updateProvider(id: Long, name: String, baseUrl: String, apiKey: String?) = Unit
        override suspend fun deleteProvider(id: Long) = Unit
        override suspend fun addModel(providerId: Long, name: String, supportsVision: Boolean) {
            addedModels.add(Triple(providerId, name, supportsVision))
        }
        override suspend fun deleteModel(id: Long) = Unit
        override suspend fun toggleSheetVisible(id: Long) = Unit
        override suspend fun setVisionFlag(id: Long, supportsVision: Boolean) = Unit
        override suspend fun markConnectionStatus(providerId: Long, ok: Boolean) {
            connectionStatusCalls.add(providerId to ok)
        }
        override suspend fun wipeAll() = Unit
        override suspend fun setPrivacyAck(ack: Boolean) {
            privacyAckValue = ack
            privacyAck.value = ack
        }
    }
}
