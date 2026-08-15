package com.wenyan.app.domain

import com.wenyan.app.json.Json
import com.wenyan.app.json.JsonObject

/**
 * 对话状态（v1.3 本地结构化跟踪，SPEC 外新增——skill 级连续对话体感）。
 *
 * 挂在会话上随 sessionId 持久化，解决"同一话题连续追问时重复给结论/话术"的问题：
 * 模型每轮经 PromptBuilder 注入本状态，知道"这个话题已经下过什么结论、给过哪句话术"，
 * 从而推进而非重复。纯数据类 + JSON 序列化，JVM 可测。
 */
data class ConversationState(
    /** 当前话题一句话摘要（如"她划清朋友边界"）；空串 = 尚无进行中的话题 */
    val topicSummary: String = "",
    /** 本轮话题已下的结论（如"尊重边界，别再推进"），用于追问时避免重复 */
    val conclusionGiven: String = "",
    /** 本轮话题最近一次给出的可发送话术原文，用于查重 */
    val lastReplyText: String = "",
    /** 本轮话题是否已给过话术 */
    val replyAlreadyGiven: Boolean = false,
    /** 当前话题的连续轮次（同一话题追问时 +1） */
    val turnCount: Int = 0,
) {
    val hasActiveTopic: Boolean get() = topicSummary.isNotBlank()

    fun toJson(): String = Json.obj().apply {
        put("topicSummary", topicSummary)
        put("conclusionGiven", conclusionGiven)
        put("lastReplyText", lastReplyText)
        put("replyAlreadyGiven", replyAlreadyGiven)
        put("turnCount", turnCount)
    }.toString()

    companion object {
        val EMPTY = ConversationState()

        fun fromJson(raw: String?): ConversationState {
            if (raw.isNullOrBlank()) return EMPTY
            return runCatching {
                val o = Json.obj(raw)
                ConversationState(
                    topicSummary = o.optString("topicSummary", ""),
                    conclusionGiven = o.optString("conclusionGiven", ""),
                    lastReplyText = o.optString("lastReplyText", ""),
                    replyAlreadyGiven = o.optBoolean("replyAlreadyGiven", false),
                    turnCount = o.optInt("turnCount", 0),
                )
            }.getOrDefault(EMPTY)
        }
    }
}
