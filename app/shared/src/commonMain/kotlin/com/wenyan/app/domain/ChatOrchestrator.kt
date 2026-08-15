package com.wenyan.app.domain

import com.wenyan.app.container.SessionTitle
import com.wenyan.app.llm.ChatHistoryMessage
import com.wenyan.app.llm.CoachAnalysis

/**
 * O4/M2: 双端编排共享层（纯逻辑，JVM 可测）。
 * 两端只保留平台接缝：消息/档案存储、LLM 客户端工厂、流式事件发射。
 */
object ChatOrchestrator {

    /** M2: 过短输入（<10 字）不提炼记忆 */
    const val MEMORY_MIN_LENGTH = 10

    /** M2: 自动记忆节流判定（两端统一）：开关 + 归属档案 + 长度门槛 + 新话题 */
    fun shouldExtractMemory(
        state: ConversationState,
        userInput: String,
        memoryAutoEnabled: Boolean,
        hasTarget: Boolean,
    ): Boolean {
        if (!memoryAutoEnabled || !hasTarget) return false
        if (userInput.length < MEMORY_MIN_LENGTH) return false
        if (!state.hasActiveTopic) return true
        return !ConversationStateTracker().isSameTopic(state, userInput)
    }

    /**
     * 新话题时提炼话题摘要：取用户输入的前 24 字；模型输出仅作补充——
     * empathy 首句（空则 advice.core）关键词追加在后面，总长控制在 40 字内。
     */
    fun summarizeTopic(userInput: String, analysis: CoachAnalysis): String {
        val base = userInput.replace(Regex("\\s+"), " ").take(24)
        val firstSentence = analysis.empathy.ifBlank { analysis.advice.core }
            .replace(Regex("[#*>`\\-]"), "")
            .split(Regex("[。！？\n]"))
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(16)
            .orEmpty()
        return if (firstSentence.isBlank()) base else "$base｜$firstSentence".take(40)
    }

    /**
     * 提炼本轮结论摘要：advice.core（空则 empathy 首句，至多 40 字），作为"已给结论"记入状态。
     */
    fun summarizeConclusion(analysis: CoachAnalysis): String =
        analysis.advice.core.ifBlank {
            analysis.empathy.split(Regex("[。！？\n]")).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        }.replace(Regex("[#*>`\\-]"), "").take(40)

    /** M2: 两端统一的标题素材/prompt 构建 */
    fun buildTitleMaterial(userText: String, replyText: String): Pair<String, String> =
        SessionTitle.buildTitleMaterial(userText, replyText)

    fun buildTitlePrompt(userLine: String, replyLine: String): String =
        SessionTitle.buildTitlePrompt(userLine, replyLine)
}
