package com.wenyan.app.ui.settings

import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.ProviderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * F77 精简：类内私有 FakeSettingsRepository 副本删除，改用同包共享 [FakeSettingsRepository]。
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
        vm.baseUrl = "https://api.example.com" // M27 起 save/addModel/test 均做 URL 预检，空地址会被拦截
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
        vm.baseUrl = "https://api.example.com"
        vm.apiKey = "sk-test"

        vm.save {}
        assertTrue(vm.showPrivacyDialog)
        assertTrue(vm.pendingAction is ProviderEditViewModel.PendingAction.Save)  // M23: Save 为 data class（携带回调）

        vm.acceptPrivacy()
        advanceUntilIdle()

        assertEquals(true, repo.privacyAckValue)
        assertTrue(repo.addedModels.isEmpty())
    }

    @Test
    fun `addModel without privacy gate adds directly`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = ProviderEditViewModel(repo, providerId = 0L)
        vm.baseUrl = "https://api.example.com"
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
        vm.baseUrl = "https://api.example.com"
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
        vm.baseUrl = "https://api.example.com"
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
        vm.baseUrl = "https://api.example.com"
        vm.privacyAck = true
        vm.apiKey = "" // 未填 Key：直接红灯，不发起测试

        vm.save {}
        advanceUntilIdle()

        assertEquals(0, repo.testConnectionCalls)
        assertEquals(listOf(1L to false), repo.connectionStatusCalls)
    }

    // ===== v1.7.5 编辑模式 API Key 掩码回显 =====

    @Test
    fun `edit mode reveals saved api key`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            providers.value = listOf(
                ProviderInfo(5, "OpenAI", "https://api.openai.com", apiKeyConfigured = true, isPreset = false, sortOrder = 0),
            )
            apiKeyValue = "sk-test-123"
        }
        val vm = ProviderEditViewModel(repo, providerId = 5L)
        advanceUntilIdle()
        assertEquals("sk-test-123", vm.apiKey)
        assertEquals("OpenAI", vm.name)
    }

    @Test
    fun `saving unchanged api key does not re-encrypt`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            providers.value = listOf(
                ProviderInfo(5, "OpenAI", "https://api.openai.com", apiKeyConfigured = true, isPreset = false, sortOrder = 0),
            )
            apiKeyValue = "sk-test-123"
        }
        val vm = ProviderEditViewModel(repo, providerId = 5L)
        advanceUntilIdle()
        vm.privacyAck = true
        vm.save {}
        advanceUntilIdle()
        // key 未修改 → updateProvider 收到 null（不重加密）；名称正常更新
        assertEquals(listOf(Triple(5L, "OpenAI", null)), repo.providerUpdates)
    }

    @Test
    fun `changing api key persists new value`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            providers.value = listOf(
                ProviderInfo(5, "OpenAI", "https://api.openai.com", apiKeyConfigured = true, isPreset = false, sortOrder = 0),
            )
            apiKeyValue = "sk-old"
        }
        val vm = ProviderEditViewModel(repo, providerId = 5L)
        advanceUntilIdle()
        vm.privacyAck = true
        vm.apiKey = "sk-new"
        vm.save {}
        advanceUntilIdle()
        assertEquals(listOf(Triple(5L, "OpenAI", "sk-new")), repo.providerUpdates)
    }
}
