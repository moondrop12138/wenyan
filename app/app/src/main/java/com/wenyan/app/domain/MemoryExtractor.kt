package com.wenyan.app.domain

import org.json.JSONObject

/**
 * 自动记忆提炼纯逻辑（v1.7.2，无 Android 依赖，JVM 可测）：
 * - buildPrompt：从本轮（用户输入 + 军师回复）提炼「关于咨询对象的新事实」，输出 JSON {"facts":[...]}
 * - parseFacts：防御性解析（对齐 AnalysisParser：stripFence + opt 系列 + runCatching），失败返回空列表
 * - mergeNote：追加式去重合并（上限 2000 字），幂等兜底——重复触发不会重复追加
 */
object MemoryExtractor {

    const val DEFAULT_NOTE_LIMIT = 2000

    /**
     * 提炼 prompt：从本轮（用户输入 + 军师回复）提炼「关于咨询对象的新事实」。
     * 已存在于 existingNote 的重复事实不输出；无新事实输出 {"facts":[]}。
     * 输出 JSON 契约：{"facts":["…","…"]}（每条 ≤40 字，≤5 条）。
     */
    fun buildPrompt(userInput: String, replyText: String, existingNote: String): String = buildString {
        append("你是记忆提炼器。从下面这段用户与军师的对话中，提炼出「关于咨询对象的新事实」。\n")
        append("要求：\n")
        append("- 只输出一个 JSON 对象：{\"facts\":[\"事实1\",\"事实2\",...]}，不加 markdown 代码块围栏，不加任何解释；\n")
        append("- 每条事实 ≤40 字，最多 5 条；\n")
        append("- 只提炼客观、可长期记住的信息（性格、偏好、关系进展、关键事件），不提炼一次性情绪或建议；\n")
        if (existingNote.isNotBlank()) {
            append("- 以下事实已记住，重复内容不要再输出：\n").append(existingNote.take(2000)).append("\n")
        } else {
            append("- 没有已记住的内容。\n")
        }
        append("- 没有新事实时输出 {\"facts\":[]}。\n\n")
        append("用户输入：").append(userInput.take(1000)).append("\n")
        append("军师回复：").append(replyText.take(2000)).append("\n")
        append("输出：")
    }

    /**
     * 防御性解析：非 JSON / 缺 facts / 字段非法 → 返回空列表，绝不抛异常。
     * 对齐 AnalysisParser：stripFence + opt 系列 + runCatching。
     */
    fun parseFacts(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(stripFence(json))
            val array = root.optJSONArray("facts") ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i, "").trim()
                    if (value.isNotEmpty()) add(value.take(40))
                }
            }.take(5)
        }.getOrDefault(emptyList())
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

    /** 重叠判定：整句互含 或 长度 ≥6 字片段包含（幂等兜底，确定性可测） */
    private fun overlaps(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a.contains(b) || b.contains(a)) return true
        val prefix = b.take(6)
        return prefix.length >= 6 && a.contains(prefix)
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
