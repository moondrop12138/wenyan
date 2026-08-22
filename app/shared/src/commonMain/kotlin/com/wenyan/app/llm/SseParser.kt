package com.wenyan.app.llm

import com.wenyan.app.json.Json
import com.wenyan.app.json.JsonObject

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
     * 解析单个 SSE data 帧（okhttp-sse onEvent 回调；规范允许多行 data:，
     * 客户端会以 \n 连接后一次性回调——L12 修复：按行拆分逐行解析后合并）。
     * @return null 表示该帧无需处理（空 data / 注释 / 未知格式）；parseError=true 表示非法 JSON
     */
    fun parseDataLine(data: String): Chunk? {
        // L12 修复：原把整帧当单行解析，多行 data 帧第二行起不是 { 开头或非独立 JSON
        // 被误判 PARSE_ERROR 致命错。现拆行合并：任一行非法即致命；delta 拼接；
        // error / finishReason 取首个出现的。
        if (!data.contains('\n')) return parseSingleLine(data)
        var content: String? = null
        var reasoning: String? = null
        var finish: String? = null
        var error: StreamError? = null
        for (line in data.split('\n')) {
            val chunk = parseSingleLine(line) ?: continue
            if (chunk.parseError) return chunk
            chunk.contentDelta?.let { content = (content ?: "") + it }
            chunk.reasoningDelta?.let { reasoning = (reasoning ?: "") + it }
            if (finish == null && chunk.finishReason != null) finish = chunk.finishReason
            if (error == null) error = chunk.streamError
        }
        return Chunk(
            contentDelta = content,
            finishReason = finish,
            streamError = error,
            reasoningDelta = reasoning,
        )
    }

    /** 解析单个 data 行 */
    private fun parseSingleLine(data: String): Chunk? {
        if (data.isBlank() || data == DONE) return null
        // L3: 仅对以 { 开头的行做 JSON 解析（data: ping 等 keepalive 行直接忽略，不误判 PARSE_ERROR）
        if (!data.trimStart().startsWith("{")) return null

        val json = try {
            Json.obj(data)
        } catch (e: Exception) {
            // 非法 JSON → PARSE_ERROR（llm-contract §4：响应格式异常，不可重试）
            return Chunk(contentDelta = null, finishReason = null, streamError = null, parseError = true)
        }

        // M9 修复：原 try/catch 只包 Json.obj(data)——其后 getJSONObject/getString 强类型取值
        // 遇 "error":null、字符串 error、content 为数组等畸形结构抛 JSONException 冲出监听器，
        // 进 okhttp-sse onFailure → 已收 200+response → UNKNOWN（可重试）→ 整流重发最多
        // 3 次重复计费，违反「非法 chunk = 不可重试 PARSE_ERROR」契约。
        // 现全改 opt*/isNull 防御取值；类型不符的结构一律归一为 parseError（不可重试）。

        // 顶层含 error 键：流中错误（llm-contract §3.4）；值为 null 的 error 是部分网关的
        // 心跳/占位帧——既非错误也非非法，忽略该帧
        if (json.has("error")) {
            if (json.isNull("error")) return Chunk(null, null, null)
            val err = json.optJSONObject("error")
            return if (err != null) {
                Chunk(
                    contentDelta = null,
                    finishReason = null,
                    streamError = StreamError(
                        message = err.optString("message", ""),
                        // type 桌面端 org.json 的 getString 对非字符串会抛异常 → opt+toString 兜底
                        type = if (err.has("type") && !err.isNull("type")) err.optScalarString("type") else null,
                    ),
                )
            } else {
                // error 为字符串/数字等非对象：字面量即错误消息
                Chunk(null, null, StreamError(message = json.optScalarString("error") ?: "", type = null))
            }
        }

        val choices = json.optJSONArray("choices")
        val first = choices?.optJSONObject(0)
        if (first == null) {
            // 缺 choices / 元素非对象 = 非法 chunk（llm-contract §4：不可重试）
            return Chunk(contentDelta = null, finishReason = null, streamError = null, parseError = true)
        }

        return try {
            val delta = first.optJSONObject("delta")
            // delta.content 为 null 或缺失时跳过（部分模型第一帧只有 role）；类型不符 → parseError
            fun readTextField(obj: JsonObject, key: String): String? {
                if (!obj.has(key) || obj.isNull(key)) return null
                // M9: 数字等标量也宽容收下（optScalarString），仅缺失/显式 null 才跳过——
                // 个别网关把 content 序列化成数字字面量时不再整帧 PARSE_ERROR
                return obj.optScalarString(key)
            }
            val content = delta?.let { readTextField(it, "content") }
            // reasoning_content（深度思考模型）单独抽出：拼入推理通道，不进正文（llm-contract §3.2）
            val reasoning = delta?.let { readTextField(it, "reasoning_content") }
            val finishReason = readTextField(first, "finish_reason")

            Chunk(
                contentDelta = content,
                finishReason = finishReason,
                streamError = null,
                reasoningDelta = reasoning,
            )
        } catch (e: SseFormatException) {
            Chunk(contentDelta = null, finishReason = null, streamError = null, parseError = true)
        }
    }
}

/** M9: 字段类型不符契约（期望字符串）——上层捕获后归一为 parseError */
private class SseFormatException(val field: String) : Exception("field '$field' has unexpected type")
