package com.wenyan.app.llm

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

/** 回复形态已随 v1.6 统一为四段结构 JSON（原 FREETEXT/STRUCTURED 双模式已删除） */
data class ChatRequest(
    val model: String,
    val system: String,
    val userText: String,
    /** v1.6.1 多图：一次请求可携带最多 [MAX_IMAGES_PER_REQUEST] 张图（content 数组多 image_url part） */
    val imageDataUrls: List<String> = emptyList(),
    /**
     * 历史兼容字段：v1.7.x 起不再写入请求体（thinking-only 模型只允许 temperature=1）。
     * 保留参数避免改调用方；如需服务端调整，请直接用 system prompt 约束。
     */
    val temperature: Double = 0.7,
    /** 同会话历史消息，注入在 system 之后、当前 user 之前 */
    val history: List<ChatHistoryMessage> = emptyList(),
)

/** v1.6.1 单次 LLM 请求图片上限（与选图上限一致，防超长请求体） */
const val MAX_IMAGES_PER_REQUEST = 10

/**
 * 输入 token 粗略估算（可观测性元数据，仅用量，不含内容）。
 * 文本按 ~4 字符/token；图片按固定常量估算（OpenAI 近似，够观测用）。
 */
fun ChatRequest.estimatedInputTokens(): Int {
    val historyLen = history.sumOf { it.content.length }
    val textTokens = (system.length + userText.length + historyLen) / 4
    val imageTokens = imageDataUrls.size * IMAGE_TOKEN_ESTIMATE
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
    /** H1: 对可重试错误发起重试前发出，通知 UI 清空已累积的增量文本，避免与重试后新流重复拼接 */
    data object Restart : LlmEvent()
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
        // v1.7.x 不再发送 temperature：Kimi Code 等 thinking-only 模型只允许 temperature=1，
        // 发送 0.7/0.3 会被 400 拒绝（invalid temperature: only 1 is allowed）。不传则服务端用默认值，
        // 对所有 OpenAI 兼容服务通用。temperature 字段保留仅作历史兼容，不再入 body。

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
        if (request.imageDataUrls.isEmpty()) {
            userMsg.put("content", request.userText)
        } else {
            val content = JSONArray()
            val textPart = JSONObject()
            textPart.put("type", "text")
            textPart.put("text", request.userText)
            content.put(textPart)

            // v1.6.1 多图：逐张追加 image_url part（顺序与选图一致）
            for (dataUrl in request.imageDataUrls) {
                val imagePart = JSONObject()
                imagePart.put("type", "image_url")
                val imageUrl = JSONObject()
                imageUrl.put("url", dataUrl)
                imagePart.put("image_url", imageUrl)
                content.put(imagePart)
            }

            userMsg.put("content", content)
        }
        messages.put(userMsg)

        body.put("messages", messages)
        return body.toString()
    }
}
