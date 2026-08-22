package com.wenyan.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA 独立补充：错误码映射全量（llm-contract §4）
 *
 * 覆盖 401/403/404/429/500/502/503 + 超时/断流语义 + 全部 11 个错误码的
 * 用户文案非空与可重试标记。
 */
class ErrorMapperFullTest {

    // ---- HTTP 状态全量 ----

    @Test
    fun `all documented http status codes map correctly`() {
        assertEquals(LlmErrorCode.UNAUTHORIZED, ErrorMapper.fromHttpStatus(401))
        assertEquals(LlmErrorCode.FORBIDDEN, ErrorMapper.fromHttpStatus(403))
        assertEquals(LlmErrorCode.MODEL_NOT_FOUND, ErrorMapper.fromHttpStatus(404))
        assertEquals(LlmErrorCode.RATE_LIMITED, ErrorMapper.fromHttpStatus(429))
        assertEquals(LlmErrorCode.SERVER_ERROR, ErrorMapper.fromHttpStatus(500))
        assertEquals(LlmErrorCode.SERVER_ERROR, ErrorMapper.fromHttpStatus(502))
        assertEquals(LlmErrorCode.SERVER_ERROR, ErrorMapper.fromHttpStatus(503))
    }

    @Test
    fun `any 5xx maps to server error`() {
        for (code in 500..599) {
            assertEquals("$code 应映射 SERVER_ERROR", LlmErrorCode.SERVER_ERROR, ErrorMapper.fromHttpStatus(code))
        }
    }

    @Test
    fun `unexpected status maps to unknown`() {
        assertEquals(LlmErrorCode.UNKNOWN, ErrorMapper.fromHttpStatus(200))
        assertEquals(LlmErrorCode.UNKNOWN, ErrorMapper.fromHttpStatus(418))
        assertEquals(LlmErrorCode.UNKNOWN, ErrorMapper.fromHttpStatus(0))
    }

    @Test
    fun `context length statuses map to non retryable context too long`() {
        // L2/L11: 400/422 按响应体细分；413 恒为上下文过长（不可重试，重试仍会超）
        assertEquals(
            LlmErrorCode.CONTEXT_TOO_LONG,
            ErrorMapper.fromHttpStatus(400, "This model's maximum context length is 8192 tokens"),
        )
        assertEquals(LlmErrorCode.BAD_REQUEST, ErrorMapper.fromHttpStatus(400, "invalid image data url"))
        assertEquals(LlmErrorCode.BAD_REQUEST, ErrorMapper.fromHttpStatus(400))
        assertEquals(LlmErrorCode.CONTEXT_TOO_LONG, ErrorMapper.fromHttpStatus(413))
        assertEquals(LlmErrorCode.CONTEXT_TOO_LONG, ErrorMapper.fromHttpStatus(422, "context_length_exceeded"))
        assertEquals(LlmErrorCode.BAD_REQUEST, ErrorMapper.fromHttpStatus(422, "validation failed"))
        assertFalse(LlmErrorCode.CONTEXT_TOO_LONG.retryable)
        assertFalse(LlmErrorCode.BAD_REQUEST.retryable)
    }

    // ---- 超时/断流语义（通过 LlmErrorCode 的文案与可重试标记表达） ----

    @Test
    fun `timeout and stream interruption semantics`() {
        assertTrue("连接超时应可重试", LlmErrorCode.CONNECT_TIMEOUT.retryable)
        assertTrue("读超时/断流应可重试", LlmErrorCode.READ_TIMEOUT.retryable)
        assertEquals("连接超时，请检查网络或服务地址", LlmErrorCode.CONNECT_TIMEOUT.userMessage)
        assertEquals("连接中断，可重试或停止", LlmErrorCode.READ_TIMEOUT.userMessage)
    }

    // ---- 12 个错误码齐全 ----

    @Test
    fun `all error codes exist with non empty user message`() {   // L11: 14→15（新增 BAD_REQUEST）
        val codes = LlmErrorCode.entries
        // v1.7.1 终检：新增 UNSUPPORTED_URL（公网明文地址被网络安全策略拦截，提示改 https/localhost）
        // H2：新增 OUTPUT_TRUNCATED（finish_reason=length 截断）；L2：新增 CONTEXT_TOO_LONG（400/413/422）
        assertEquals("应有 15 个错误码", 15, codes.size)

        val expected = setOf(
            "UNAUTHORIZED", "FORBIDDEN", "MODEL_NOT_FOUND", "RATE_LIMITED",
            "SERVER_ERROR", "CONNECT_TIMEOUT", "READ_TIMEOUT", "UNSUPPORTED_URL",
            "STREAM_ERROR", "EMPTY_CONTENT", "PARSE_ERROR", "OUTPUT_TRUNCATED", "CONTEXT_TOO_LONG",
            "BAD_REQUEST", "UNKNOWN",
        )
        assertEquals(expected, codes.map { it.name }.toSet())

        for (code in codes) {
            assertTrue("${code.name} 文案不能为空", code.userMessage.isNotBlank())
        }
    }

    @Test
    fun `retryable flag consistent with contract`() {
        val retryable = setOf(
            "RATE_LIMITED", "SERVER_ERROR", "CONNECT_TIMEOUT", "READ_TIMEOUT", "EMPTY_CONTENT", "UNKNOWN",
        )
        for (code in LlmErrorCode.entries) {
            assertEquals("${code.name} retryable 标记", retryable.contains(code.name), code.retryable)
        }
    }

    // ---- 流中错误类型 ----

    @Test
    fun `stream error types normalize`() {
        assertEquals(LlmErrorCode.RATE_LIMITED, ErrorMapper.fromStreamErrorType("rate_limit"))
        assertEquals(LlmErrorCode.RATE_LIMITED, ErrorMapper.fromStreamErrorType("insufficient_quota"))
        assertEquals(LlmErrorCode.UNAUTHORIZED, ErrorMapper.fromStreamErrorType("invalid_api_key"))
        assertEquals(LlmErrorCode.UNAUTHORIZED, ErrorMapper.fromStreamErrorType("authentication_error"))
        assertEquals(LlmErrorCode.MODEL_NOT_FOUND, ErrorMapper.fromStreamErrorType("model_not_found"))
        assertEquals(LlmErrorCode.STREAM_ERROR, ErrorMapper.fromStreamErrorType("server_error"))
        assertEquals(LlmErrorCode.STREAM_ERROR, ErrorMapper.fromStreamErrorType(null))
        assertEquals(LlmErrorCode.STREAM_ERROR, ErrorMapper.fromStreamErrorType(""))
    }

    @Test
    fun `user messages contain no emoji`() {
        for (code in LlmErrorCode.entries) {
            val hasEmoji = code.userMessage.codePoints().anyMatch { it in 0x1F300..0x1FAFF }
            assertFalse("${code.name} 文案含 emoji", hasEmoji)
        }
    }
}
