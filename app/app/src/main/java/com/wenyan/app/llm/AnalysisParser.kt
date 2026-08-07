package com.wenyan.app.llm

import org.json.JSONArray
import org.json.JSONObject

/** 输入语境（v1.2，prompt-architecture §4）。UNCERTAIN 时 reply 是反问句而非成品话术，前端据此隐藏复制按钮。 */
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

/**
 * 五步法输出解析（prompt-architecture §4 JSON Schema v1 + §6 防御性解析）
 *
 * - strip ```json 围栏
 * - 五个 key 缺失/非法 → 对应卡片显示"模型未输出该部分"（steps 缺项以空占位）
 * - steps 非 5 项 → 按实际渲染，缺失步骤隐藏
 * v1.6 起保留仅用于：老数据解析（buildHistory/title/safety）与新老分流映射。
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

    companion object {
        val STEP_KEYS = listOf("emotion", "facts", "interests", "advice", "action")
    }
}

object AnalysisParser {

    private const val FENCE = "```"

    /**
     * 解析老五步法 JSON；失败抛 AnalysisParseException
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
            inputKind = InputKind.fromRaw(json.optString("input_kind", "")),
        )
    }

    /**
     * 解析任意模型输出（v1.6 统一入口）：按 key 结构识别新老 schema——含 `steps` 数组 → 老五步法（映射进 v2）；
     * 否则按 v2 四段解析。失败抛 AnalysisParseException。
     */
    @Throws(AnalysisParseException::class)
    fun parseAny(raw: String): CoachAnalysis {
        val cleaned = stripFence(raw)
        val json = try {
            JSONObject(cleaned)
        } catch (e: Exception) {
            throw AnalysisParseException("invalid json")
        }
        return if (json.has("steps")) {
            parse(cleaned).toCoachAnalysis()
        } else {
            parseV2Json(json)
        }
    }

    /** 解析 v2 四段 JSON；失败抛 AnalysisParseException */
    @Throws(AnalysisParseException::class)
    fun parseV2(raw: String): CoachAnalysis {
        val json = try {
            JSONObject(stripFence(raw))
        } catch (e: Exception) {
            throw AnalysisParseException("invalid json")
        }
        return parseV2Json(json)
    }

    private fun parseV2Json(json: JSONObject): CoachAnalysis {
        val factsObj = json.optJSONObject("facts")
        val adviceObj = json.optJSONObject("advice")
        return CoachAnalysis(
            inputKind = InputKind.fromRaw(json.optString("input_kind", "")),
            empathy = json.optString("empathy", ""),
            reply = json.optString("reply", ""),
            replyTiming = json.optString("reply_timing", ""),
            facts = CoachAnalysis.Facts(
                known = parseStringArray(factsObj?.optJSONArray("known")),
                assumed = parseStringArray(factsObj?.optJSONArray("assumed")),
                unknown = parseStringArray(factsObj?.optJSONArray("unknown")),
            ),
            advice = CoachAnalysis.Advice(
                tag = adviceObj?.optString("tag", "").orEmpty(),
                core = adviceObj?.optString("core", "").orEmpty(),
                reasons = parseStringArray(adviceObj?.optJSONArray("reasons")),
                styles = parseStyles(adviceObj?.optJSONArray("styles")),
            ),
            actions = parseActions(json.optJSONArray("actions")),
            citations = parseStringArray(json.optJSONArray("citations")),
            // v1.7.3 记忆引用溯源（防御默认空，≤3 条）
            memoryCitations = parseStringArray(json.optJSONArray("memory_citations")).take(3),
            safetyOverride = json.optBoolean("safety_override", false),
            safetyMessage = json.optString("safety_message", ""),
            tokenEstimate = if (json.has("token_estimate")) json.optInt("token_estimate") else null,
        )
    }

    private fun parseStyles(array: JSONArray?): List<CoachAnalysis.Advice.Style> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val text = obj.optString("text", "")
                if (text.isBlank()) continue
                add(
                    CoachAnalysis.Advice.Style(
                        key = obj.optString("key", ""),
                        label = obj.optString("label", ""),
                        text = text,
                    )
                )
            }
        }
    }

    private fun parseActions(array: JSONArray?): List<CoachAnalysis.ActionItem> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val text = obj.optString("text", "")
                if (text.isBlank()) continue
                add(
                    CoachAnalysis.ActionItem(
                        label = obj.optString("label", ""),
                        text = text,
                    )
                )
            }
        }
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
