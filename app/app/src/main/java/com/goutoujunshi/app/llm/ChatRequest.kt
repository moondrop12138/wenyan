package com.goutoujunshi.app.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI 兼容 Chat Completions 请求（llm-contract §2）
 * 纯 JVM 可测。
 */
/**
 * 历史消息（纯文本对话轮次，role 为 "user"/"assistant"）
 */
data class ChatHistoryMessage(
    val role: String,
    val content: String,
)

data class ChatRequest(
    val model: String,
    val system: String,
    val userText: String,
    val imageDataUrl: String? = null,
    val temperature: Double = 0.7,
    /** 同会话历史消息，注入在 system 之后、当前 user 之前 */
    val history: List<ChatHistoryMessage> = emptyList(),
)

/**
 * 输入 token 粗略估算（可观测性元数据，仅用量，不含内容）。
 * 文本按 ~4 字符/token；图片按固定常量估算（OpenAI 近似，够观测用）。
 */
fun ChatRequest.estimatedInputTokens(): Int {
    val historyLen = history.sumOf { it.content.length }
    val textTokens = (system.length + userText.length + historyLen) / 4
    val imageTokens = if (imageDataUrl != null) IMAGE_TOKEN_ESTIMATE else 0
    return textTokens + imageTokens
}

private const val IMAGE_TOKEN_ESTIMATE = 850

/**
 * 流式事件（UI 经 MutableStateFlow 消费）
 */
sealed class LlmEvent {
    data class Delta(val text: String) : LlmEvent()
    /** 深度思考模型的推理增量（reasoning_content），与正文分开 */
    data class Thinking(val text: String) : LlmEvent()
    data class Done(val fullText: String) : LlmEvent()
    data class Failed(val error: LlmErrorCode, val detail: String = "") : LlmEvent()
}

/**
 * 请求体构造器（OpenAI 兼容 JSON）
 * 多模态：user content 为数组 [text, image_url]（llm-contract §2.3）
 */
object ChatRequestBuilder {

    fun build(request: ChatRequest): String {
        val body = JSONObject()
        body.put("model", request.model)
        body.put("stream", true)
        body.put("temperature", request.temperature)

        val messages = JSONArray()
        val systemMsg = JSONObject()
        systemMsg.put("role", "system")
        systemMsg.put("content", request.system)
        messages.put(systemMsg)

        // 历史消息：system 之后、当前 user 之前
        for (h in request.history) {
            val historyMsg = JSONObject()
            historyMsg.put("role", h.role)
            historyMsg.put("content", h.content)
            messages.put(historyMsg)
        }

        val userMsg = JSONObject()
        userMsg.put("role", "user")
        if (request.imageDataUrl == null) {
            userMsg.put("content", request.userText)
        } else {
            val content = JSONArray()
            val textPart = JSONObject()
            textPart.put("type", "text")
            textPart.put("text", request.userText)
            content.put(textPart)

            val imagePart = JSONObject()
            imagePart.put("type", "image_url")
            val imageUrl = JSONObject()
            imageUrl.put("url", request.imageDataUrl)
            imagePart.put("image_url", imageUrl)
            content.put(imagePart)

            userMsg.put("content", content)
        }
        messages.put(userMsg)

        body.put("messages", messages)
        return body.toString()
    }
}
