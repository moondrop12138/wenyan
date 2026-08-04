package com.wenyan.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

/**
 * ChatRequestBuilder 测试（llm-contract §2 请求构造）
 */
class ChatRequestBuilderTest {

    @Test
    fun `text request builds expected json`() {
        val request = ChatRequest(
            model = "deepseek-v4-pro",
            system = "system-text",
            userText = "user-text",
        )
        val body = JSONObject(ChatRequestBuilder.build(request))
        assertEquals("deepseek-v4-pro", body.getString("model"))
        assertEquals(true, body.getBoolean("stream"))
        assertEquals(0.7, body.getDouble("temperature"), 0.0001)

        val messages = body.getJSONArray("messages")
        assertEquals(2, messages.length())
        val system = messages.getJSONObject(0)
        assertEquals("system", system.getString("role"))
        assertEquals("system-text", system.getString("content"))

        val user = messages.getJSONObject(1)
        assertEquals("user", user.getString("role"))
        assertEquals("user-text", user.getString("content"))
    }

    @Test
    fun `multimodal request builds content array`() {
        val request = ChatRequest(
            model = "gpt-5.6-terra",
            system = "s",
            userText = "分析截图",
            imageDataUrl = "data:image/jpeg;base64,AAA",
        )
        val body = JSONObject(ChatRequestBuilder.build(request))
        val messages = body.getJSONArray("messages")
        val user = messages.getJSONObject(1)
        val content = user.getJSONArray("content")
        assertEquals(2, content.length())

        val textPart = content.getJSONObject(0)
        assertEquals("text", textPart.getString("type"))
        assertEquals("分析截图", textPart.getString("text"))

        val imagePart = content.getJSONObject(1)
        assertEquals("image_url", imagePart.getString("type"))
        val imageUrl = imagePart.getJSONObject("image_url")
        assertTrue(imageUrl.getString("url").startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun `json is valid and parseable`() {
        val request = ChatRequest("m", "s", "u")
        val raw = ChatRequestBuilder.build(request)
        assertTrue(raw.contains("\"stream\":true"))
    }
}
