package com.wenyan.app.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * 五步法输出解析（prompt-architecture §4 JSON Schema + §6 防御性解析）
 *
 * - strip ```json 围栏
 * - 五个 key 缺失/非法 → 对应卡片显示"模型未输出该部分"（steps 缺项以空占位）
 * - steps 非 5 项 → 按实际渲染，缺失步骤隐藏
 * 纯 JVM 可测。
 */
data class FiveStepAnalysis(
    val steps: List<Step>,
    val reply: String,
    val replyTiming: String,
    val citations: List<String>,
    val safetyOverride: Boolean,
    val safetyMessage: String,
    val tokenEstimate: Int?,
    /** 输入语境判断（v1.2，prompt-architecture §4）；旧模型无此字段时回落 UNKNOWN，不崩 */
    val inputKind: InputKind = InputKind.UNKNOWN,
) {
    data class Step(
        val key: String,
        val title: String,
        val content: String,
        val items: List<String>,
    )

    /** 输入语境（v1.2）。UNCERTAIN 时 reply 是反问句而非成品话术，前端据此隐藏复制按钮。 */
    enum class InputKind {
        USER_QUESTION, RELAYED_QUOTE, PASTED_CHAT, GREETING, UNCERTAIN, UNKNOWN;

        companion object {
            fun fromRaw(raw: String): InputKind = when (raw.trim().lowercase()) {
                "user_question" -> USER_QUESTION
                "relayed_quote" -> RELAYED_QUOTE
                "pasted_chat" -> PASTED_CHAT
                "greeting" -> GREETING
                "uncertain" -> UNCERTAIN
                else -> UNKNOWN
            }
        }
    }

    companion object {
        val STEP_KEYS = listOf("emotion", "facts", "interests", "advice", "action")
    }
}

object AnalysisParser {

    private const val FENCE = "```"

    /**
     * 解析模型输出 JSON；失败抛 AnalysisParseException
     */
    @Throws(AnalysisParseException::class)
    fun parse(raw: String): FiveStepAnalysis {
        val cleaned = stripFence(raw)
        val json = try {
            JSONObject(cleaned)
        } catch (e: Exception) {
            throw AnalysisParseException("invalid json")
        }

        val steps = parseSteps(json.optJSONArray("steps"))
        return FiveStepAnalysis(
            steps = steps,
            reply = json.optString("reply", ""),
            replyTiming = json.optString("reply_timing", ""),
            citations = parseStringArray(json.optJSONArray("citations")),
            safetyOverride = json.optBoolean("safety_override", false),
            safetyMessage = json.optString("safety_message", ""),
            tokenEstimate = if (json.has("token_estimate")) json.optInt("token_estimate") else null,
            inputKind = FiveStepAnalysis.InputKind.fromRaw(json.optString("input_kind", "")),
        )
    }

    private fun parseSteps(array: JSONArray?): List<FiveStepAnalysis.Step> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val key = obj.optString("key", "")
                val items = parseStringArray(obj.optJSONArray("items"))
                add(
                    FiveStepAnalysis.Step(
                        key = key,
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        items = items,
                    )
                )
            }
        }
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i, "")
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    /**
     * strip ```json 围栏（prompt-architecture §6）
     */
    fun stripFence(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith(FENCE)) {
            val body = trimmed.removePrefix(FENCE)
                .removeSuffix(FENCE)
                .trim()
            // 去掉可能的 "json" 语言标记行
            return body.removePrefix("json").trim()
        }
        return trimmed
    }

    class AnalysisParseException(message: String) : Exception(message)
}
