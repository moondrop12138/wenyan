package com.wenyan.app.llm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ChatRequest 输入 token 估算（可观测性埋点元数据，仅用量不含内容）。
 */
class ChatRequestTokenEstimateTest {

    @Test
    fun `text only estimate scales with char count`() {
        val text = "a".repeat(400) // ~100 tokens
        val req = ChatRequest(model = "m", system = "", userText = text)
        assertEquals(100, req.estimatedInputTokens())
    }

    @Test
    fun `system text contributes to estimate`() {
        val req = ChatRequest(model = "m", system = "a".repeat(100), userText = "b".repeat(100))
        assertEquals(50, req.estimatedInputTokens())
    }

    @Test
    fun `single image adds fixed token overhead`() {
        val req = ChatRequest(model = "m", system = "", userText = "hi", imageDataUrls = listOf("data:image/png;base64,xxx"))
        // "hi" 不足 4 字符 → 文本 0 token，叠加单图固定 850
        assertEquals(850, req.estimatedInputTokens())
    }

    @Test
    fun `multiple images scale token overhead linearly`() {
        val req = ChatRequest(
            model = "m",
            system = "",
            userText = "hi",
            imageDataUrls = List(10) { "data:image/png;base64,xxx$it" },
        )
        // v1.6.1 多图：10 张 = 850 × 10
        assertEquals(8500, req.estimatedInputTokens())
    }
}
