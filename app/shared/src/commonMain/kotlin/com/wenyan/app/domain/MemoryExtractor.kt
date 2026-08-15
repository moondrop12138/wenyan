package com.wenyan.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import com.wenyan.app.json.Json
import com.wenyan.app.json.JsonObject

/**
 * 自动记忆提炼纯逻辑（v1.7.2，无 Android 依赖，JVM 可测）：
 * - buildPrompt：从本轮（用户输入 + 军师回复）提炼「关于咨询对象的新事实」，输出 JSON {"facts":[...]}
 * - parseFacts：防御性解析（对齐 AnalysisParser：stripFence + opt 系列 + runCatching），失败返回空列表
 * - mergeNote：追加式去重合并（上限 2000 字），幂等兜底——重复触发不会重复追加
 * - v1.9.0 契约升级：facts 条目可为 {"text":"…","kind":"fact|hypothesis"}；兼容旧纯字符串格式；
 *   推断类信息（性格、依恋、意图等暂定解释）标 kind=hypothesis，与客观事实分开持久化。
 * - v1.9.1 契约升级：facts 条目可带 "expires_in":"today|week"（临时时效信息，如"今天/本周"）；
 *   computeExpiryMillis 本地换算到期时间戳（today→次日 0 点，week→下周一 0 点，系统时区）。
 */
object MemoryExtractor {

    const val DEFAULT_NOTE_LIMIT = 2000
    /** v1.7.3 每档案事实条数上限（超出静默丢弃新事实） */
    const val DEFAULT_FACT_LIMIT = 50
    const val KIND_FACT = "fact"
    const val KIND_HYPOTHESIS = "hypothesis"

    /** v1.9.1 时效档位（expires_in 取值） */
    const val EXPIRES_TODAY = "today"
    const val EXPIRES_WEEK = "week"

    /** 提炼结果单条：text 事实文本 + kind 分层 + expiresIn 时效档位（null=永久） */
    data class ExtractedFact(
        val text: String,
        val kind: String = KIND_FACT,
        val expiresIn: String? = null,
    )

    /**
     * 提炼 prompt：从本轮（用户输入 + 军师回复）提炼「关于咨询对象的新事实」。
     * 已存在于 existingNote 的重复事实不输出；无新事实输出 {"facts":[]}。
     * 输出 JSON 契约：{"facts":[{"text":"…","kind":"fact|hypothesis","expires_in":"today|week"}]}（每条 ≤40 字，≤5 条）。
     */
    fun buildPrompt(userInput: String, replyText: String, existingNote: String): String = buildString {
        append("你是记忆提炼器。从下面这段用户与军师的对话中，提炼出「关于咨询对象的新事实」。\n")
        append("要求：\n")
        append("- 只输出一个 JSON 对象：{\"facts\":[{\"text\":\"事实\",\"kind\":\"fact\",\"expires_in\":null},...]}，不加 markdown 代码块围栏，不加任何解释；\n")
        append("- 每条 ≤40 字，最多 5 条；\n")
        append("- kind=fact：用户明确陈述或可核验的客观信息（性格、偏好、关系进展、关键事件）；kind=hypothesis：模型推断的暂定解释（如对方性格倾向、依恋类型、意图猜测），推断必须带依据可被纠正；\n")
        append("- expires_in：仅当信息明确有时效（如\"今天\"\"这周\"\"今晚\"相关），填 today（次日零点失效）或 week（下周一零点失效）；无时效信息填 null 或省略；\n")
        append("- 只提炼客观、可长期记住的信息，不提炼一次性情绪或建议；\n")
        if (existingNote.isNotBlank()) {
            append("- 以下事实已记住，重复内容不要再输出：\n").append(takeCodePoints(existingNote, 2000)).append("\n")
        } else {
            append("- 没有已记住的内容。\n")
        }
        append("- 没有新事实时输出 {\"facts\":[]}。\n\n")
        append("用户输入：").append(takeCodePoints(userInput, 1000)).append("\n")
        append("军师回复：").append(takeCodePoints(replyText, 2000)).append("\n")
        append("输出：")
    }

    /**
     * 防御性解析：非 JSON / 缺 facts / 字段非法 → 返回空列表，绝不抛异常。
     * v1.9.0 支持两种格式：新 {"text","kind"} 对象 与 旧纯字符串（视为 fact），混用亦可。
     * v1.9.1 支持 {"text","kind","expires_in"}；expires_in 仅认 today/week，其余视为永久。
     * 对齐 AnalysisParser：stripFence + opt 系列 + runCatching。
     */
    fun parseFacts(json: String): List<ExtractedFact> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = Json.obj(stripFence(json))
            val array = root.optJSONArray("facts") ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    when (item) {
                        is JsonObject -> {
                            val text = item.optString("text", "").trim()
                            if (text.isNotEmpty()) {
                                val kind = item.optString("kind", KIND_FACT).trim()
                                val expiresIn = item.optString("expires_in", "").trim()
                                add(
                                    ExtractedFact(
                                        text = text.take(40),
                                        kind = if (kind == KIND_HYPOTHESIS) KIND_HYPOTHESIS else KIND_FACT,
                                        expiresIn = if (expiresIn == EXPIRES_TODAY || expiresIn == EXPIRES_WEEK) expiresIn else null,
                                    ),
                                )
                            }
                        }
                        is String -> {
                            val text = item.trim()
                            if (text.isNotEmpty()) add(ExtractedFact(text.take(40), KIND_FACT))
                        }
                    }
                }
            }.take(5)
        }.getOrDefault(emptyList())
    }

    /**
     * v1.9.1 时效档位 → 到期毫秒时间戳（纯函数，JVM 可测）：
     * - today：次日 00:00（系统时区）
     * - week：下周一 00:00（系统时区；周一当天则为次周一）
     * - 其他/null：null（永久）
     */
    fun computeExpiryMillis(
        expiresIn: String?,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (expiresIn != EXPIRES_TODAY && expiresIn != EXPIRES_WEEK) return null
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val target = when (expiresIn) {
            EXPIRES_TODAY -> today.plusDays(1)
            EXPIRES_WEEK -> {
                val daysUntilMonday = (DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7
                // daysUntilMonday==0 表示今天就是周一 → 下周一
                today.plusDays(if (daysUntilMonday == 0) 7L else daysUntilMonday.toLong())
            }
            else -> return null
        }
        return ZonedDateTime.of(target, java.time.LocalTime.MIDNIGHT, zone).toInstant().toEpochMilli()
    }

    /**
     * 追加式合并：trim+去重（保序）；与已有 note 任一片段（按 \n；。切分）互含重叠则跳过；
     * 无新事实返回原 note；追加用「；」分隔；整体 take(limit) 截断（默认 2000 字）。
     * 幂等兜底：重复触发不会重复追加。
     */
    fun mergeNote(existingNote: String, facts: List<String>, limit: Int = DEFAULT_NOTE_LIMIT): String {
        val existing = existingNote.trim()
        val cleanFacts = facts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanFacts.isEmpty()) return existing
        val segments = splitSegments(existing)
        val toAppend = cleanFacts.filter { fact -> segments.none { seg -> overlaps(seg, fact) } }
        if (toAppend.isEmpty()) return existing
        val merged = if (existing.isEmpty()) {
            toAppend.joinToString("；")
        } else {
            existing + "；" + toAppend.joinToString("；")
        }
        return merged.take(limit)
    }

    /** 按 \n、。、；切分已有记忆为片段（用于重叠判定） */
    private fun splitSegments(note: String): List<String> =
        note.split(Regex("[\\n。；]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * v1.7.3 事实列表合并（替代 mergeNote 的新链路）：
     * trim+去空；facts 逐条与 existing 全部片段做 overlaps 判定去重（保序追加）；
     * 总条数 take(limit)（默认 50）。幂等兜底：重复触发不会重复追加。
     * 纯函数，JVM 可测。
     */
    fun mergeFacts(
        existing: List<String>,
        facts: List<String>,
        limit: Int = DEFAULT_FACT_LIMIT,
    ): List<String> {
        val cleanExisting = existing.map { it.trim() }.filter { it.isNotEmpty() }
        val cleanFacts = facts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanFacts.isEmpty()) return cleanExisting.take(limit)
        val segments = cleanExisting.flatMap { splitSegments(it) }.ifEmpty { cleanExisting }
        val toAppend = cleanFacts.filter { fact -> segments.none { seg -> overlaps(seg, fact) } }
        return (cleanExisting + toAppend).take(limit)
    }

    /**
     * v1.7.3 note → 事实列表拆分（老数据惰性搬移用）：
     * 按 \n。；切分 + trim + 去空 + 单条 ≤40 字。原 splitSegments 提为 public 工具。
     */
    fun splitNoteToFacts(note: String): List<String> =
        splitSegments(note).map { it.take(40) }

    /** 重叠判定：整句互含 或 长度 ≥6 字片段包含（幂等兜底，确定性可测） */
    private fun overlaps(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a.contains(b) || b.contains(a)) return true
        val prefix = b.take(6)
        return prefix.length >= 6 && a.contains(prefix)
    }

    /** L10: codePoint 安全截断（不切断 UTF-16 代理对，如 emoji） */
    private fun takeCodePoints(text: String, max: Int): String {
        if (text.length <= max) return text
        var end = max
        if (end < text.length && Character.isLowSurrogate(text[end])) end--
        return text.substring(0, end)
    }

    /** 去掉 ```json 围栏（对齐 AnalysisParser.stripFence） */
    private fun stripFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.removePrefix("```")
            .removeSuffix("```")
            .trim()
            .removePrefix("json")
            .trim()
    }
}
