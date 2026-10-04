package com.wenyan.app.ui.settings

import com.wenyan.app.llm.LlmErrorCode
import com.wenyan.app.ui.contract.LlmError
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProviderEditSessions Holder 规约测试（与既有测试并列，不改既有测试）。
 *
 * 覆盖：四路并发只插一行（单行保证）、key 分流（save 首插 null→"" / update 透传 null）、
 * 取消语义（CE 清旗重抛、不进映射）、全 UUID 零串扰、VM 侧 NO_NETWORK 映射断言。
 * 凭据约束：全用假 key 占位（FAKE_KEY 常量，非可用凭据）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderEditSessionsTest {

    companion object {
        /** 假 key 占位：单元测试专用，无任何可用性 */
        private const val FAKE_KEY = "fake-test-key"
    }

    private fun newKey(): String = "pe-" + UUID.randomUUID()

    private fun snapshot(
        isNew: Boolean = true,
        keyToPersist: String? = FAKE_KEY,
        name: String = "测试服务",
    ) = ProviderEditSnapshot(
        name = name,
        baseUrl = "https://api.example.com",
        keyToPersist = keyToPersist,
        apiKeyBlank = keyToPersist == null,
        providerIdArg = 0L,
        isNew = isNew,
    )

    @Test
    fun `concurrent ensureEffectiveId inserts only one row`() = runTest {
        val repo = FakeSettingsRepository()
        repo.saveDelayMillis = 50
        val key = newKey()
        val snap = snapshot()
        val ids = (1..4).map {
            async {
                // Holder scope 是 Dispatchers.IO；此处直接调挂起点验证单行保证
                ProviderEditSessions.ensureEffectiveId(key, snap, repo)
            }
        }.awaitAll()
        assertEquals(1, repo.savedProviders.size)
        assertTrue(ids.toSet().size == 1)
        ProviderEditSessions.release(key)
    }

    @Test
    fun `save first insert maps null key to empty string`() = runTest {
        val repo = FakeSettingsRepository()
        val key = newKey()
        ProviderEditSessions.ensureEffectiveId(key, snapshot(keyToPersist = null), repo)
        assertEquals(1, repo.savedProviders.size)
        assertEquals("", repo.savedProviders.single().third)
        ProviderEditSessions.release(key)
    }

    @Test
    fun `edit update passes null key through untouched`() = runTest {
        val repo = FakeSettingsRepository()
        val key = newKey()
        val snap = snapshot(isNew = false, keyToPersist = null).copy(providerIdArg = 5L)
        val id = ProviderEditSessions.ensureEffectiveId(key, snap, repo)
        assertEquals(5L, id)
        assertEquals(0, repo.savedProviders.size)
        assertEquals(listOf(Triple(5L, "测试服务", null)), repo.providerUpdates)
        ProviderEditSessions.release(key)
    }

    @Test
    fun `cancelled runTest clears testing flag without producing outcome`() {
        // Holder job 跑在真实 Dispatchers.IO：用 runBlocking 真实等待（runTest 虚拟时钟
        // 轮询 delay 会与 IO 线程置旗互锁）。VM 的 viewModelScope 用 Main，需临时代理。
        val mainDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
        try {
            runBlocking {
                val repo = FakeSettingsRepository()
                repo.testDelayMillis = 10_000
                val key = newKey()
                val job = ProviderEditSessions.runTest(key, snapshot(), repo)
                withTimeout(5_000) {
                    while (!ProviderEditSessions.session(key).testing.value) delay(10)
                }
                job.cancel()
                try {
                    job.join()
                } catch (_: CancellationException) {
                    // join 本身不抛；保取消语义不断言此处
                }
                // CE 清旗后重抛、不进映射：旗必清，outcome 永不由 CE 产生
                assertEquals(false, ProviderEditSessions.session(key).testing.value)
                assertNull(ProviderEditSessions.session(key).outcome.value)
                ProviderEditSessions.release(key)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `different keys do not interfere`() = runTest {
        val repo = FakeSettingsRepository()
        val keyA = newKey()
        val keyB = newKey()
        ProviderEditSessions.ensureEffectiveId(keyA, snapshot(name = "服务A"), repo)
        // 释放 A 后 B 照常可用；全局唯一键下 release 无需 token 比对
        ProviderEditSessions.release(keyA)
        ProviderEditSessions.ensureEffectiveId(keyB, snapshot(name = "服务B"), repo)
        assertEquals(2, repo.savedProviders.size)
        ProviderEditSessions.release(keyB)
    }

    @Test
    fun `vm maps NO_NETWORK error to warn result with contract message`() {
        // 同上：Holder runTest 在真实 IO 跑，VM 构造需 Main，用 runBlocking 真实等待。
        // 镜像收集跑在 viewModelScope（Main）：StandardTestDispatcher 下 collect 协程永不被调度，
        // vm.testResult 永远等不到；此处用 UnconfinedTestDispatcher 让收集即时执行。
        val mainDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
        try {
            runBlocking {
                val repo = FakeSettingsRepository()
                repo.testConnectionResult = LlmError(
                    LlmErrorCode.NO_NETWORK.name,
                    LlmErrorCode.NO_NETWORK.userMessage,
                    retryable = true,
                )
                val vm = ProviderEditViewModel(repo, providerId = 0L)
                vm.baseUrl = "https://api.example.com"
                vm.privacyAck = true
                vm.apiKey = FAKE_KEY
                vm.testConnection()
                // Holder 在 IO scope 跑；等 outcome 落定
                val s = ProviderEditSessions.session(vm.sessionKey)
                withTimeout(5_000) {
                    while (s.outcome.value == null) delay(10)
                }
                // VM 镜像收集把原始 outcome 唯一映射为 TestResult
                withTimeout(5_000) {
                    while (vm.testResult == null) delay(10)
                }
                val result = vm.testResult!!
                assertTrue(result.warn)
                assertEquals(LlmErrorCode.NO_NETWORK.userMessage, result.message)
                ProviderEditSessions.release(vm.sessionKey)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `successful runTest stores success outcome and clears flag`() {
        // 同上：真实 IO 等待
        val mainDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
        try {
            runBlocking {
                val repo = FakeSettingsRepository()
                val key = newKey()
                val job = ProviderEditSessions.runTest(key, snapshot(), repo)
                job.join()
                assertEquals(ProviderTestOutcome.Success, ProviderEditSessions.session(key).outcome.value)
                assertEquals(false, ProviderEditSessions.session(key).testing.value)
                ProviderEditSessions.release(key)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
