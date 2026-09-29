package com.wenyan.app.container

import com.wenyan.app.ui.contract.CoachCard
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.StreamEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式状态归属回归测试（v1.9.4 用户报障：新会话 AI 回答完成后打字气泡不消失、发送键卡「停止生成」）。
 *
 * 根因（修复前）：新会话首条消息以 PENDING_SESSION_KEY(-1) 注册流并置状态归属 null，
 * sendTextFlow 拿到真实 sid 后只改了「状态归属」，事件应用时 ownerKey 仍是 -1 →
 * Delta/Analysis/Done/Error/Transcription 全被归属校验丢弃，Done 的 streaming=false 永不落地；
 * cancel()/deleteSession() 按真实 sid 也取不到 job。
 *
 * 为什么测 [StreamStateHost] 而不是直接构造 RealChatRepository：
 * 本仓库的 RealChatRepository 依赖 android.content.Context + DataStore + Android Keystore
 * （ProviderRepository 需要 KeystoreAesGcmCipher，其构造即 KeyStore.getInstance("AndroidKeyStore")），
 * 在 JVM 单测里构造不起来（需 Robolectric/仪器测试）。流式状态的归属与生命周期逻辑已全部收敛到
 * StreamStateHost，这里按 RealChatRepository 里**完全相同的调用序列**驱动它：
 * `launch(viewSessionId = sessionId.value)` → 流内 `handle.retag(ensureSession())` → 事件应用 → 收尾。
 *
 * 调度器用显式 StandardTestDispatcher（同 MemoryEditViewModelTest 的既有基建）：
 * 传给 runTest 保证 mock 调度器与 host 协程共用同一 TestCoroutineScheduler，advanceUntilIdle 可控。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealChatRepositoryTest {

    private val dispatcher = StandardTestDispatcher()

    /**
     * host 的「应用级 scope」替身：测试调度器 + SupervisorJob + 吞异常处理器，
     * 与 appScope（SupervisorJob + CoroutineExceptionHandler）同形——异常由流的结束回调兜底复位，
     * 不再靠全局异常处理器复位（后者拿不到归属 key）。
     */
    private val scope = CoroutineScope(
        dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, _ -> /* 与生产一致：只记日志 */ }
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** 模拟 Room 自增 id（恒 >= 1，与 PENDING_SESSION_KEY 不冲突） */
    private val newSessionId = 42L
    private val oldSessionId = 7L

    // ===== a) 新会话首条消息：Delta/Analysis/Done 全部正常应用，streaming 最终复位 =====

    @Test
    fun `新会话首条消息 retag 后增量与 Done 正常应用并复位 streaming`() = runTest(dispatcher) {
        val host = StreamStateHost(scope)
        val gate = CompletableDeferred<Unit>()
        host.launch(viewSessionId = null) { handle ->
            flow {
                // 与 sendTextFlow 一致：ensureSession() 返回后立即 retag（PENDING → 真实 sid）
                handle.retag(newSessionId)
                emit(StreamEvent.Delta("先接住你的情绪"))
                gate.await()
                emit(StreamEvent.Analysis(CoachCard(adviceCore = "先别急着回")))
                emit(StreamEvent.Done)
            }
        }
        advanceUntilIdle()

        // 修复前：ownerKey 仍是 -1，Delta 被丢弃 → text 为空
        assertEquals("先接住你的情绪", host.state.value.text)
        assertTrue(host.state.value.streaming)
        assertEquals(newSessionId, host.state.value.sessionId)

        gate.complete(Unit)
        advanceUntilIdle()

        val st = host.state.value
        assertFalse("Done 必须落地：打字气泡/停止键由 streaming 门控", st.streaming)
        assertEquals("", st.text)
        assertNull(st.error)
    }

    // ===== b) Error：streaming 复位且错误文案保留（收尾兜底不得覆盖） =====

    @Test
    fun `新会话首条消息收到 Error 后 streaming 复位且文案保留`() = runTest(dispatcher) {
        val host = StreamStateHost(scope)
        val error = LlmError("RATE_LIMIT", "请求过于频繁，请稍后再试", true)
        host.launch(viewSessionId = null) { handle ->
            flow {
                handle.retag(newSessionId)
                emit(StreamEvent.Error(error))
                // 流在 Error 之后正常收尾：invokeOnCompletion 兜底复位不得清掉错误文案
            }
        }
        advanceUntilIdle()

        val st = host.state.value
        assertFalse(st.streaming)
        assertFalse(st.transcribing)
        assertEquals(error, st.error)
        assertEquals(newSessionId, st.sessionId)
    }

    // ===== c) 新会话流 cancel：协程被真正取消 + 状态复位 =====

    @Test
    fun `新会话流 retag 后 cancel 真正取消协程并复位状态`() = runTest(dispatcher) {
        val host = StreamStateHost(scope)
        var flowCancelled = false
        val started = CompletableDeferred<Unit>()
        host.launch(viewSessionId = null) { handle ->
            flow {
                handle.retag(newSessionId)
                emit(StreamEvent.Delta("思考中…"))
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    flowCancelled = true
                }
            }
        }
        advanceUntilIdle()
        assertTrue(started.isCompleted)
        assertTrue(host.state.value.streaming)

        // 用户点「停止生成」时 sessionId.value 已是真实 sid（ensureSession 已写回）
        host.cancel(viewSessionId = newSessionId)
        advanceUntilIdle()

        assertTrue("LLM 协程必须被真正取消（修复前 job 注册在 PENDING 下，按 sid 取不到）", flowCancelled)
        val st = host.state.value
        assertFalse(st.streaming)
        assertFalse(st.transcribing)
        assertNull(st.error)
    }

    @Test
    fun `新会话首条消息在 retag 前点停止：PENDING 下的 job 被取消且状态复位`() = runTest(dispatcher) {
        val host = StreamStateHost(scope)
        var flowCancelled = false
        host.launch(viewSessionId = null) { _ ->
            flow {
                // ensureSession 尚在落库：状态归属仍是 null（PENDING）
                try {
                    awaitCancellation()
                } finally {
                    flowCancelled = true
                }
            }
        }
        advanceUntilIdle()
        assertNull(host.state.value.sessionId)
        assertTrue(host.state.value.streaming)

        host.cancel(viewSessionId = newSessionId)
        advanceUntilIdle()

        assertTrue(flowCancelled)
        assertFalse(host.state.value.streaming)
    }

    // ===== d) deleteSession：取消该会话的流且不留僵尸 streaming 态 =====

    @Test
    fun `deleteSession 取消该会话的流并复位状态不留僵尸 streaming`() = runTest(dispatcher) {
        val host = StreamStateHost(scope)
        var flowCancelled = false
        host.launch(viewSessionId = newSessionId) { _ ->
            flow {
                emit(StreamEvent.Delta("正在分析…"))
                try {
                    awaitCancellation()
                } finally {
                    flowCancelled = true
                }
            }
        }
        advanceUntilIdle()
        assertTrue(host.state.value.streaming)

        host.cancelFor(newSessionId)
        advanceUntilIdle()

        assertTrue(flowCancelled)
        val st = host.state.value
        assertFalse("删除会话后不得留下 {streaming=true} 僵尸态", st.streaming)
        assertNull(st.sessionId)
        assertEquals("", st.text)
    }

    // ===== 异常收尾：invokeOnCompletion 兜底按归属复位（修复前无任何兜底） =====

    @Test
    fun `流内抛异常时归属守卫兜底复位 streaming`() = runTest(dispatcher) {
        val host = StreamStateHost(scope)
        host.launch(viewSessionId = null) { handle ->
            flow {
                handle.retag(newSessionId)
                emit(StreamEvent.Delta("半截回复"))
                throw IllegalStateException("DB 写失败")
            }
        }
        advanceUntilIdle()

        val st = host.state.value
        assertFalse("异常收尾也必须复位（守卫只动归属本流的 streaming）", st.streaming)
        assertFalse(st.transcribing)
        assertEquals("", st.text)
        assertEquals(newSessionId, st.sessionId)
        assertNull("异常不是 Error 事件：不臆造错误文案", st.error)
    }

    // ===== 不回归：旧会话流的迟到事件仍要被拒收（M18 归属校验） =====

    @Test
    fun `切会后旧会话流的迟到事件被丢弃不污染新会话状态`() = runTest(dispatcher) {
        val host = StreamStateHost(scope)
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        host.launch(viewSessionId = oldSessionId) { _ ->
            flow {
                emit(StreamEvent.Delta("旧流增量"))
                oldGate.await()
                emit(StreamEvent.Delta("迟到的增量"))
                emit(StreamEvent.Done)
            }
        }
        advanceUntilIdle()

        // 切到新会话并发起新流（PENDING → 新 sid）
        host.launch(viewSessionId = null) { handle ->
            flow {
                handle.retag(newSessionId)
                emit(StreamEvent.Delta("新会话增量"))
                newGate.await()
                emit(StreamEvent.Done)
            }
        }
        advanceUntilIdle()
        assertEquals(newSessionId, host.state.value.sessionId)
        assertEquals("新会话增量", host.state.value.text)

        // 旧流迟到：Delta + Done 都归属不符 → 全部丢弃，新会话状态不受影响
        oldGate.complete(Unit)
        advanceUntilIdle()
        assertEquals("新会话增量", host.state.value.text)
        assertTrue(host.state.value.streaming)

        newGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(host.state.value.streaming)
    }

    // ===== 转述通道：归属校验通过才标记等待态；Transcription 落地并复位 streaming =====

    @Test
    fun `新会话转述通道 markTranscribing 生效且 Transcription 落地复位 streaming`() = runTest(dispatcher) {
        val host = StreamStateHost(scope)
        val gate = CompletableDeferred<Unit>()
        host.launch(viewSessionId = null) { handle ->
            flow {
                handle.retag(newSessionId)
                handle.markTranscribing(true)
                gate.await()
                emit(StreamEvent.Transcription("从截图里读出的文字"))
            }
        }
        advanceUntilIdle()
        assertTrue("转述等待态（归属校验通过）", host.state.value.transcribing)

        gate.complete(Unit)
        advanceUntilIdle()

        val st = host.state.value
        assertEquals("从截图里读出的文字", st.transcription)
        assertFalse(st.streaming)
        assertFalse(st.transcribing)
    }

    // ===== 同会话单飞：已落定会话重复发送不叠加流 =====

    @Test
    fun `同会话已有活跃流时重复发送被忽略`() = runTest(dispatcher) {
        val host = StreamStateHost(scope)
        var secondStarted = false
        val firstFlow: (StreamStateHost.Handle) -> Flow<StreamEvent> = { _ ->
            flow {
                emit(StreamEvent.Delta("第一轮"))
                awaitCancellation()
            }
        }
        host.launch(viewSessionId = newSessionId, flowFactory = firstFlow)
        advanceUntilIdle()
        host.launch(viewSessionId = newSessionId) { _ ->
            flow {
                secondStarted = true
                emit(StreamEvent.Done)
            }
        }
        advanceUntilIdle()
        assertFalse(secondStarted)
        assertTrue(host.state.value.streaming)
        assertEquals("第一轮", host.state.value.text)
    }
}
