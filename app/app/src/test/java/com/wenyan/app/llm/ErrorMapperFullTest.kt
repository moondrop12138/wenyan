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
    fun `all 12 error codes exist with non empty user message`() {
        val codes = LlmErrorCode.entries
        // v1.7.1 终检：新增 UNSUPPORTED_URL（公网明文地址被网络安全策略拦截，提示改 https/localhost）
        assertEquals("应有 12 个错误码", 12, codes.size)

        val expected = setOf(
            "UNAUTHORIZED", "FORBIDDEN", "MODEL_NOT_FOUND", "RATE_LIMITED",
            "SERVER_ERROR", "CONNECT_TIMEOUT", "READ_TIMEOUT", "UNSUPPORTED_URL",
            "STREAM_ERROR", "EMPTY_CONTENT", "PARSE_ERROR", "UNKNOWN",
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
