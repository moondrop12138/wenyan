package com.wenyan.app.llm

import com.wenyan.app.llm.LlmEvent.Delta
import com.wenyan.app.llm.LlmEvent.Done
import com.wenyan.app.llm.LlmEvent.Failed
import com.wenyan.app.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

/**
 * LLM Client（OkHttp + SSE callbackFlow，llm-contract §2/§3/§5）
 *
 * - 连接超时 15s / 读超时 60s
 * - SSE 流式：每个 contentDelta 立即推送 Delta（AC-14 实时增量渲染）
 * - 指数退避重试：仅可重试错误（429/5xx/超时/断流/空内容），其余直接失败
 * - 用户取消：collect 取消 → eventSource.cancel()，已渲染内容保留
 */
class LlmClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val client: OkHttpClient = defaultClient(),
) {

    fun stream(request: ChatRequest): Flow<LlmEvent> = callbackFlow {
        var attempt = 0
        val startedAt = System.currentTimeMillis()
        var deltaCount = 0
        var firstTokenRecorded = false
        // v1.8.1 B1 修复：持有当前 EventSource 引用。重试时 launchAttempt 会创建新实例，
        // 若仍 cancel 首次的旧引用，重试中的 OkHttp 长连接在用户取消时不释放（连接泄漏）
        var currentEventSource: EventSource? = null
        val provider = runCatching { java.net.URI(baseUrl).host }.getOrNull() ?: baseUrl
        AppLogger.i(
            "llm_request_start",
            "model" to request.model,
            "provider" to provider,
            "est_tokens" to request.estimatedInputTokens(),
        )
        // O6: 记录请求与输入 token 估算
        UsageMetrics.recordRequestStart(request.estimatedInputTokens())

        fun launchAttempt() {
            val eventSource = startEventSource(request) { event ->
                when (event) {
                    is SingleResult.Success -> {
                        AppLogger.i(
                            "llm_request_success",
                            "model" to request.model,
                            "attempt" to attempt,
                            "duration_ms" to (System.currentTimeMillis() - startedAt),
                            "delta_count" to deltaCount,
                        )
                        // O6: 完成记录输出 token（delta 计数近似）
                        UsageMetrics.recordCompletion(deltaCount)
                        trySend(Done(event.text))
                        close()
                    }
                    is SingleResult.Retryable -> {
                        if (attempt >= retryPolicy.maxRetries) {
                            AppLogger.w(
                                "llm_request_failed",
                                "model" to request.model,
                                "error" to event.error.name,
                                "attempt" to attempt,
                                "retries" to retryPolicy.maxRetries,
                                "duration_ms" to (System.currentTimeMillis() - startedAt),
                            )
                            UsageMetrics.recordFailure(event.error.name)
                            trySend(Failed(event.error, event.detail))
                            close()
                        } else {
                            attempt++
                            // H1: 重试前通知 UI 清空已渲染增量，避免与重试后新流拼接重复
                            trySend(LlmEvent.Restart)
                            val delay = retryPolicy.delayWithRetryAfter(attempt, event.retryAfterSeconds)
                            this@callbackFlow.launch {
                                kotlinx.coroutines.delay(delay)
                                launchAttempt()
                            }
                        }
                    }
                    is SingleResult.Fatal -> {
                        AppLogger.w(
                            "llm_request_failed",
                            "model" to request.model,
                            "error" to event.error.name,
                            "attempt" to attempt,
                            "retries" to retryPolicy.maxRetries,
                            "duration_ms" to (System.currentTimeMillis() - startedAt),
                        )
                        UsageMetrics.recordFailure(event.error.name)
                        trySend(Failed(event.error, event.detail))
                        close()
                    }
                    is SingleResult.Delta -> {
                        deltaCount++
                        if (!firstTokenRecorded) {
                            firstTokenRecorded = true
                            UsageMetrics.recordFirstToken(System.currentTimeMillis() - startedAt)
                        }
                        trySend(Delta(event.text))
                    }
                    is SingleResult.Thinking -> {
                        if (!firstTokenRecorded) {
                            firstTokenRecorded = true
                            UsageMetrics.recordFirstToken(System.currentTimeMillis() - startedAt)
                        }
                        trySend(LlmEvent.Thinking(event.text))
                    }
                }
            }
            // 每次发起都更新为当前实例，取消时 cancel 的永远是最新的那个
            currentEventSource = eventSource
        }

        launchAttempt()
        // v1.8.1 B1 修复：flow 取消/关闭时释放当前连接（含重试中的新连接）
        awaitClose { currentEventSource?.cancel() }
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(Failed(LlmErrorCode.UNKNOWN, e.message ?: ""))
    }

    /**
     * 启动单次 SSE 请求，事件经回调返回。
     * @return 可取消的 EventSource
     */
    private fun startEventSource(
        request: ChatRequest,
        onResult: (SingleResult) -> Unit,
    ): EventSource {
        val requestBody = ChatRequestBuilder.build(request)
            .toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            // L15: 统一 User-Agent（与 L8 UpdateClient 一致；部分网关按 UA 做访问控制）
            .header("User-Agent", "wenyan-llm-client")
            .post(requestBody)
            .build()

        val accumulator = StringBuilder()
        var settled = false

        fun settle(result: SingleResult) {
            if (settled) return
            settled = true
            onResult(result)
        }

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val chunk = SseParser.parseDataLine(data) ?: return
                // 非法 JSON chunk → PARSE_ERROR（llm-contract §4，不可重试）
                if (chunk.parseError) {
                    settle(SingleResult.Fatal(LlmErrorCode.PARSE_ERROR, "invalid chunk"))
                    eventSource.cancel()
                    return
                }
                chunk.streamError?.let { err ->
                    val code = ErrorMapper.fromStreamErrorType(err.type)
                    settle(
                        if (code.retryable) SingleResult.Retryable(code, err.message)
                        else SingleResult.Fatal(code, err.message)
                    )
                    eventSource.cancel()
                    return
                }
                chunk.reasoningDelta?.let {
                    onResult(SingleResult.Thinking(it))
                }
                chunk.contentDelta?.let {
                    accumulator.append(it)
                    onResult(SingleResult.Delta(it))
                }
                if (chunk.finishReason != null) {
                    eventSource.cancel()
                    settle(resolveDone(accumulator.toString(), chunk.finishReason))
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                // L10：已收到增量后断流（200 头之后连接中断）→ 传 midStream 提示，
                // 归 READ_TIMEOUT 而非 UNKNOWN（两者都 retryable，但文案不再误导）
                // 已收到正文增量（accumulator 非空）才视为「中途断流」
                settle(classifyFailure(t, response, midStream = accumulator.isNotEmpty()))
            }

            // M10 修复：服务端/网关发完 200 响应头后干净关流且从未发 finish_reason——
            // 原实现唯一收尾入口是 finish_reason != null，此时 flow 永不 close、
            // readTimeout 不触发（连接已正常关闭）→ 双端聊天永久「思考中」。
            // 未 settle 时按可重试的 EMPTY_CONTENT 收尾（与空回复同路径）。
            override fun onClosed(eventSource: EventSource) {
                if (!settled) {
                    settle(SingleResult.Retryable(LlmErrorCode.EMPTY_CONTENT, "stream closed without finish_reason"))
                }
            }
        }

        return EventSources.createFactory(client).newEventSource(httpRequest, listener)
    }

    private fun resolveDone(text: String, finishReason: String?): SingleResult = when {
        // H2: finish_reason=length 是模型因上下文/输出上限截断，不是成功——透出不可重试的截断错误
        finishReason == "length" -> SingleResult.Fatal(LlmErrorCode.OUTPUT_TRUNCATED)
        text.isBlank() -> SingleResult.Retryable(LlmErrorCode.EMPTY_CONTENT)
        else -> SingleResult.Success(text)
    }

    private fun classifyFailure(t: Throwable?, response: Response?, midStream: Boolean = false): SingleResult {
        if (response != null) {
            // L10 修复：断流中途失败因 response != null 优先走状态码分支归 UNKNOWN（文案误导）；
            // 已开始输出后的连接中断本质是读中断，归 READ_TIMEOUT。
            if (midStream && response.code == 200) {
                return SingleResult.Retryable(LlmErrorCode.READ_TIMEOUT, "stream broken after headers")
            }
            // L11: peek 响应体前 512 字符做 400/422 细分（peekBody 不消耗 body，可安全重试读取）
            val bodyHint = runCatching { response.peekBody(512).string() }.getOrNull()
            val code = ErrorMapper.fromHttpStatus(response.code, bodyHint)
            val retryAfter = response.header("Retry-After")?.toIntOrNull()
            return if (code.retryable) {
                SingleResult.Retryable(code, "", retryAfter)
            } else {
                SingleResult.Fatal(code)
            }
        }
        return if (t is IOException) {
            val msg = t.message ?: ""
            val code = when {
                // 非 localhost 的明文地址被网络安全策略拦截（UnknownServiceException: CLEARTEXT...）
                msg.contains("cleartext", ignoreCase = true) -> LlmErrorCode.UNSUPPORTED_URL
                msg.contains("timeout", ignoreCase = true) -> LlmErrorCode.READ_TIMEOUT
                else -> LlmErrorCode.CONNECT_TIMEOUT
            }
            if (code.retryable) SingleResult.Retryable(code, msg)
            else SingleResult.Fatal(code, msg)
        } else {
            SingleResult.Fatal(LlmErrorCode.UNKNOWN, t?.message ?: "")
        }
    }

    private sealed class SingleResult {
        data class Success(val text: String) : SingleResult()
        data class Delta(val text: String) : SingleResult()
        data class Thinking(val text: String) : SingleResult()
        data class Retryable(
            val error: LlmErrorCode,
            val detail: String = "",
            val retryAfterSeconds: Int? = null,
        ) : SingleResult()

        data class Fatal(val error: LlmErrorCode, val detail: String = "") : SingleResult()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .apply {
                // 桌面版（jpackage 已开启 useSystemProxies）：OkHttp 默认不读系统代理，
                // 显式用 ProxySelector.getDefault() 走系统代理；Android 上 getDefault() 为 null 时保持默认（直连）
                val ps = ProxySelector.getDefault()
                if (ps != null) proxySelector(ps)
            }
            .build()
    }
}
