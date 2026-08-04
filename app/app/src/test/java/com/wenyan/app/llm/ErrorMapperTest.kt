package com.wenyan.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错误码映射测试（llm-contract §4）
 */
class ErrorMapperTest {

    @Test
    fun `http status mapping`() {
        assertEquals(LlmErrorCode.UNAUTHORIZED, ErrorMapper.fromHttpStatus(401))
        assertEquals(LlmErrorCode.FORBIDDEN, ErrorMapper.fromHttpStatus(403))
        assertEquals(LlmErrorCode.MODEL_NOT_FOUND, ErrorMapper.fromHttpStatus(404))
        assertEquals(LlmErrorCode.RATE_LIMITED, ErrorMapper.fromHttpStatus(429))
        assertEquals(LlmErrorCode.SERVER_ERROR, ErrorMapper.fromHttpStatus(500))
        assertEquals(LlmErrorCode.SERVER_ERROR, ErrorMapper.fromHttpStatus(503))
        assertEquals(LlmErrorCode.UNKNOWN, ErrorMapper.fromHttpStatus(418))
    }

    @Test
    fun `stream error type mapping`() {
        assertEquals(LlmErrorCode.RATE_LIMITED, ErrorMapper.fromStreamErrorType("rate_limit"))
        assertEquals(LlmErrorCode.RATE_LIMITED, ErrorMapper.fromStreamErrorType("insufficient_quota"))
        assertEquals(LlmErrorCode.UNAUTHORIZED, ErrorMapper.fromStreamErrorType("invalid_api_key"))
        assertEquals(LlmErrorCode.MODEL_NOT_FOUND, ErrorMapper.fromStreamErrorType("model_not_found"))
        assertEquals(LlmErrorCode.STREAM_ERROR, ErrorMapper.fromStreamErrorType("server_error"))
        assertEquals(LlmErrorCode.STREAM_ERROR, ErrorMapper.fromStreamErrorType(null))
    }

    @Test
    fun `retryable flags correct`() {
        assertFalse(LlmErrorCode.UNAUTHORIZED.retryable)
        assertFalse(LlmErrorCode.FORBIDDEN.retryable)
        assertFalse(LlmErrorCode.MODEL_NOT_FOUND.retryable)
        assertTrue(LlmErrorCode.RATE_LIMITED.retryable)
        assertTrue(LlmErrorCode.SERVER_ERROR.retryable)
        assertTrue(LlmErrorCode.CONNECT_TIMEOUT.retryable)
        assertTrue(LlmErrorCode.READ_TIMEOUT.retryable)
        assertFalse(LlmErrorCode.STREAM_ERROR.retryable)
        assertTrue(LlmErrorCode.EMPTY_CONTENT.retryable)
        assertFalse(LlmErrorCode.PARSE_ERROR.retryable)
    }

    @Test
    fun `user messages are pure text no emoji`() {
        for (code in LlmErrorCode.entries) {
            val hasEmoji = code.userMessage.codePoints().anyMatch { it in 0x1F300..0x1FAFF }
            assertTrue("code ${code.name} contains emoji", !hasEmoji)
        }
    }
}
