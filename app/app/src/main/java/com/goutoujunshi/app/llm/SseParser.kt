package com.goutoujunshi.app.llm

import org.json.JSONObject

/**
 * SSE chunk 解析（llm-contract §3）
 * 纯 JVM 可测，无 Android 依赖。
 */
object SseParser {

    data class Chunk(
        val contentDelta: String?,
        val finishReason: String?,
        val streamError: StreamError?,
        /** 深度思考模型的推理增量（reasoning_content），与正文分开上报；UI 折叠展示，不拼入正文 */
        val reasoningDelta: String? = null,
        /** 非法 JSON chunk → 按 llm-contract §4 PARSE_ERROR 处理（不可重试） */
        val parseError: Boolean = false,
    )

    data class StreamError(
        val message: String,
        val type: String?,
    )

    const val DONE = "[DONE]"

    /**
     * 解析单个 SSE data 行（okhttp-sse onEvent 每次回调 = 一个 data 行）
     * @return null 表示该帧无需处理（空 data / 注释 / 未知格式）；parseError=true 表示非法 JSON
     */
    fun parseDataLine(data: String): Chunk? {
        if (data.isBlank() || data == DONE) return null

        val json = try {
            JSONObject(data)
        } catch (e: Exception) {
            // 非法 JSON → PARSE_ERROR（llm-contract §4：响应格式异常，不可重试）
            return Chunk(contentDelta = null, finishReason = null, streamError = null, parseError = true)
        }

        // 顶层含 error 键：流中错误（llm-contract §3.4）
        if (json.has("error")) {
            val err = json.getJSONObject("error")
            return Chunk(
                contentDelta = null,
                finishReason = null,
                streamError = StreamError(
                    message = err.optString("message", ""),
                    type = if (err.has("type")) err.getString("type") else null,
                ),
            )
        }

        val choices = json.optJSONArray("choices") ?: return Chunk(null, null, null)
        if (choices.length() == 0) return Chunk(null, null, null)
        val first = choices.getJSONObject(0)

        val delta = first.optJSONObject("delta")
        // delta.content 为 null 或缺失时跳过（部分模型第一帧只有 role）
        val content = if (delta != null && delta.has("content") && !delta.isNull("content")) {
            delta.getString("content")
        } else null
        // reasoning_content（深度思考模型）单独抽出：拼入推理通道，不进正文（llm-contract §3.2）
        val reasoning = if (delta != null && delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
            delta.getString("reasoning_content")
        } else null
        val finishReason = if (first.has("finish_reason") && !first.isNull("finish_reason")) {
            first.getString("finish_reason")
        } else null

        return Chunk(
            contentDelta = content,
            finishReason = finishReason,
            streamError = null,
            reasoningDelta = reasoning,
        )
    }
}
