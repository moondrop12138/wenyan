package com.wenyan.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 解析测试（llm-contract §3）
 */
class SseParserTest {

    @Test
    fun `done marker returns null`() {
        assertNull(SseParser.parseDataLine("[DONE]"))
        assertNull(SseParser.parseDataLine(""))
        assertNull(SseParser.parseDataLine("  "))
    }

    @Test
    fun `content delta extracted`() {
        val chunk = SseParser.parseDataLine(
            """{"id":"x","choices":[{"index":0,"delta":{"content":"你好"},"finish_reason":null}]}"""
        )
        assertNotNull(chunk)
        assertEquals("你好", chunk?.contentDelta)
        assertNull(chunk?.finishReason)
    }

    @Test
    fun `null content delta skipped`() {
        val chunk = SseParser.parseDataLine(
            """{"choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}"""
        )
        assertNull(chunk?.contentDelta)
    }

    @Test
    fun `reasoning content ignored`() {
        val chunk = SseParser.parseDataLine(
            """{"choices":[{"index":0,"delta":{"reasoning_content":"思考中","content":"正文"},"finish_reason":null}]}"""
        )
        assertEquals("正文", chunk?.contentDelta)
    }

    @Test
    fun `finish reason extracted`() {
        val chunk = SseParser.parseDataLine(
            """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
        )
        assertEquals("stop", chunk?.finishReason)
    }

    @Test
    fun `finish reason length extracted`() {
        val chunk = SseParser.parseDataLine(
            """{"choices":[{"index":0,"delta":{},"finish_reason":"length"}]}"""
        )
        assertEquals("length", chunk?.finishReason)
    }

    @Test
    fun `stream error extracted`() {
        val chunk = SseParser.parseDataLine(
            """{"error":{"message":"rate limit exceeded","type":"rate_limit"}}"""
        )
        assertNotNull(chunk?.streamError)
        assertEquals("rate limit exceeded", chunk?.streamError?.message)
        assertEquals("rate_limit", chunk?.streamError?.type)
    }

    @Test
    fun `invalid json marks parse error`() {
        val chunk = SseParser.parseDataLine("{not-json")
        assertTrue(chunk?.parseError == true)
        assertNull(chunk?.contentDelta)
        assertNull(chunk?.finishReason)
        assertNull(chunk?.streamError)
    }

    @Test
    fun `keepalive non json line ignored`() {
        // L3: data: ping 等非 JSON keepalive 行不再误判 PARSE_ERROR
        assertNull(SseParser.parseDataLine("ping"))
        assertNull(SseParser.parseDataLine(": keep-alive"))
    }

    @Test
    fun `empty choices returns null chunk`() {
        val chunk = SseParser.parseDataLine("""{"choices":[]}""")
        assertNull(chunk?.contentDelta)
    }
}
