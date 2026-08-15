package com.wenyan.app.llm

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LLM Client 集成测试（MockWebServer 模拟 OpenAI 兼容端点）
 * 验证 SSE 流式解析、done 判定、错误映射、重试（llm-contract §3/§4/§5）
 */
class LlmClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: LlmClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = LlmClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKey = "test-key",
            retryPolicy = RetryPolicy(maxRetries = 1, random = kotlin.random.Random(1)),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sseResponse(vararg events: String): MockResponse {
        val body = events.joinToString("\n\n") + "\n\n"
        return MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)
    }

    @Test
    fun `streams content deltas and completes`() = runBlocking {
        server.enqueue(
            sseResponse(
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"},\"finish_reason\":null}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"},\"finish_reason\":null}]}",
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]",
            )
        )

        val events = client.stream(ChatRequest("m", "s", "u")).toList()
        val deltas = events.filterIsInstance<LlmEvent.Delta>().map { it.text }
        assertEquals(listOf("你", "好"), deltas)
        val done = events.filterIsInstance<LlmEvent.Done>().single()
        assertEquals("你好", done.fullText)
    }

    @Test
    fun `401 maps to unauthorized fatal`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        val events = client.stream(ChatRequest("m", "s", "u")).toList()
        val failed = events.filterIsInstance<LlmEvent.Failed>().single()
        assertEquals(LlmErrorCode.UNAUTHORIZED, failed.error)
    }

    @Test
    fun `429 retries then succeeds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        server.enqueue(
            sseResponse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]",
            )
        )
        val events = client.stream(ChatRequest("m", "s", "u")).toList()
        assertEquals(2, server.requestCount)
        val done = events.filterIsInstance<LlmEvent.Done>().single()
        assertEquals("ok", done.fullText)
        assertTrue(events.none { it is LlmEvent.Failed })
    }

    @Test
    fun `stream error with rate_limit type retries`() = runBlocking {
        server.enqueue(
            sseResponse("data: {\"error\":{\"message\":\"limit\",\"type\":\"rate_limit\"}}")
        )
        server.enqueue(
            sseResponse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]",
            )
        )
        val events = client.stream(ChatRequest("m", "s", "u")).toList()
        assertEquals(2, server.requestCount)
        val done = events.filterIsInstance<LlmEvent.Done>().single()
        assertEquals("ok", done.fullText)
    }

    @Test
    fun `stream error fatal does not retry`() = runBlocking {
        server.enqueue(
            sseResponse("data: {\"error\":{\"message\":\"bad\",\"type\":\"invalid_request_error\"}}")
        )
        val events = client.stream(ChatRequest("m", "s", "u")).toList()
        assertEquals(1, server.requestCount)
        val failed = events.filterIsInstance<LlmEvent.Failed>().single()
        assertEquals(LlmErrorCode.STREAM_ERROR, failed.error)
        assertEquals("bad", failed.detail)
    }

    @Test
    fun `invalid json chunk maps to parse error fatal`() = runBlocking {
        server.enqueue(
            sseResponse("data: not-json-at-all")
        )
        val events = client.stream(ChatRequest("m", "s", "u")).toList()
        assertEquals(1, server.requestCount)
        val failed = events.filterIsInstance<LlmEvent.Failed>().single()
        assertEquals(LlmErrorCode.PARSE_ERROR, failed.error)
    }

    @Test
    fun `empty content is retryable and eventually fails after retries`() = runBlocking {
        server.enqueue(
            sseResponse("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}")
        )
        server.enqueue(
            sseResponse("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}")
        )
        val events = client.stream(ChatRequest("m", "s", "u")).toList()
        assertEquals(2, server.requestCount)
        val failed = events.filterIsInstance<LlmEvent.Failed>().single()
        assertEquals(LlmErrorCode.EMPTY_CONTENT, failed.error)
    }

    @Test
    fun `H1 retry after partial stream emits Restart between delta batches`() = runBlocking {
        // 第一段流：先出部分增量，随后返回可重试错误（等价于断流触发重试）
        server.enqueue(
            sseResponse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"},\"finish_reason\":null}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"},\"finish_reason\":null}]}",
                "data: {\"error\":{\"message\":\"limit\",\"type\":\"rate_limit\"}}",
            )
        )
        // 第二段流：完整输出
        server.enqueue(
            sseResponse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"再见\"},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]",
            )
        )
        val events = client.stream(ChatRequest("m", "s", "u")).toList()
        assertEquals(2, server.requestCount)
        val deltas = events.filterIsInstance<LlmEvent.Delta>().map { it.text }
        assertEquals(listOf("你", "好", "再见"), deltas)
        val restarts = events.filterIsInstance<LlmEvent.Restart>()
        assertEquals(1, restarts.size)
        // Restart 位于第一批 Delta 之后、第二批 Delta 之前（UI 据此清空累积文本，避免重复拼接）
        val restartIndex = events.indexOf(restarts.single())
        val firstBatchEnd = events.indexOfLast { it is LlmEvent.Delta && it.text == "好" }
        assertTrue(restartIndex > firstBatchEnd)
        val done = events.filterIsInstance<LlmEvent.Done>().single()
        assertEquals("再见", done.fullText)
        assertTrue(events.none { it is LlmEvent.Failed })
    }

    @Test
    fun `H2 finish_reason length maps to output truncated fatal without retry`() = runBlocking {
        server.enqueue(
            sseResponse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"半截话术\"},\"finish_reason\":\"length\"}]}",
                "data: [DONE]",
            )
        )
        val events = client.stream(ChatRequest("m", "s", "u")).toList()
        assertEquals(1, server.requestCount)
        val failed = events.filterIsInstance<LlmEvent.Failed>().single()
        assertEquals(LlmErrorCode.OUTPUT_TRUNCATED, failed.error)
    }
}
