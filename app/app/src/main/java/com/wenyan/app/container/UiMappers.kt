package com.wenyan.app.container

import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.ModelEntity
import com.wenyan.app.data.db.ProviderEntity
import com.wenyan.app.llm.AnalysisParser
import com.wenyan.app.llm.CoachAnalysis
import com.wenyan.app.llm.InputKind
import com.wenyan.app.llm.LlmErrorCode
import com.wenyan.app.ui.contract.ActionItemUi
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.ChatRole
import com.wenyan.app.ui.contract.CoachCard
import com.wenyan.app.ui.contract.InputKindUi
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.MessageType
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.ScriptStyle

/**
 * data 层实体 → UI 契约模型映射（container 装配层职责，纯函数）。
 */
object UiMappers {

    fun toProviderInfo(e: ProviderEntity): ProviderInfo = ProviderInfo(
        id = e.id,
        name = e.name,
        baseUrl = e.baseUrl,
        apiKeyConfigured = !e.apiKeyEncrypted.isNullOrBlank(),
        isPreset = e.isPreset,
        sortOrder = e.sortOrder,
    )

    fun toModelInfo(m: ModelEntity, providerName: String): ModelInfo = ModelInfo(
        id = m.id,
        providerId = m.providerId,
        providerName = providerName,
        name = m.name,
        supportsVision = m.supportsVision,
        isDefault = m.isDefault,
        sortOrder = m.sortOrder,
    )

    fun toChatMessage(e: MessageEntity): ChatMessageUi = ChatMessageUi(
        id = e.id,
        role = if (e.role == "USER") ChatRole.USER else ChatRole.ASSISTANT,
        type = when (e.type) {
            "image" -> MessageType.IMAGE
            "analysis" -> MessageType.ANALYSIS
            "transcription" -> MessageType.TRANSCRIPTION
            "freetext" -> MessageType.FREETEXT
            else -> MessageType.TEXT
        },
        content = e.content,
        createdAt = e.createdAt,
    )

    /** CoachAnalysis → UI CoachCard（prompt-architecture §4/§5） */
    fun toCoachCard(a: CoachAnalysis): CoachCard = CoachCard(
        empathy = a.empathy,
        factsKnown = a.facts.known,
        factsAssumed = a.facts.assumed,
        factsUnknown = a.facts.unknown,
        adviceTag = a.advice.tag,
        adviceCore = a.advice.core,
        reasons = a.advice.reasons,
        styles = a.advice.styles.map { ScriptStyle(key = it.key, label = it.label, text = it.text) },
        actions = a.actions.map { ActionItemUi(label = it.label, text = it.text) },
        reply = a.reply,
        replyTiming = a.replyTiming,
        citations = a.citations,
        safetyOverride = a.safetyOverride,
        safetyMessage = a.safetyMessage,
        tokenEstimate = a.tokenEstimate ?: 0,
        inputKind = toInputKindUi(a.inputKind),
    )

    /**
     * 防御性解析模型输出为 CoachCard（v1.6 统一入口，新老 schema 均兼容）：
     * 解析失败返回 null（UI 回落纯文本气泡）。
     */
    fun parseCoachCard(json: String): CoachCard? = try {
        toCoachCard(AnalysisParser.parseAny(json))
    } catch (e: Exception) {
        null
    }

    private fun toInputKindUi(k: InputKind): InputKindUi = when (k) {
        InputKind.USER_QUESTION -> InputKindUi.USER_QUESTION
        InputKind.RELAYED_QUOTE -> InputKindUi.RELAYED_QUOTE
        InputKind.PASTED_CHAT -> InputKindUi.PASTED_CHAT
        InputKind.GREETING -> InputKindUi.GREETING
        InputKind.UNCERTAIN -> InputKindUi.UNCERTAIN
        InputKind.UNKNOWN -> InputKindUi.UNKNOWN
    }

    /** LlmErrorCode → UI LlmError（llm-contract §4） */
    fun toLlmError(code: LlmErrorCode): LlmError = LlmError(
        code = code.name,
        message = code.userMessage,
        retryable = code.retryable,
    )

    /**
     * v1.6.2 分析卡 → 可选择的纯文本（"部分选择"模式渲染用，纯函数可单测）：
     * 四段结构按【段落】拼接为连续文本，段落间空行；styles 只取默认（第一个）风格话术——
     * 三风格是 UI 互斥切换项，全拼会造成三份冗余；safetyOverride 时只输出安全指引。
     */
    fun coachCardToSelectableText(card: CoachCard): String = buildString {
        if (card.safetyOverride) {
            append(card.safetyMessage.ifBlank { "先处理安全，再处理关系" })
            return@buildString
        }
        if (card.empathy.isNotBlank()) {
            append(card.empathy)
            append("\n\n")
        }
        listOf("事实" to card.factsKnown, "推测" to card.factsAssumed, "未知" to card.factsUnknown)
            .filter { it.second.isNotEmpty() }
            .forEach { (label, items) ->
                append("【$label】\n")
                items.forEach { append("· $it\n") }
                append("\n")
            }
        if (card.adviceTag.isNotBlank() || card.adviceCore.isNotBlank() || card.reasons.isNotEmpty()) {
            append("【军师建议】\n")
            if (card.adviceTag.isNotBlank()) append("策略：${card.adviceTag}\n")
            if (card.adviceCore.isNotBlank()) append(card.adviceCore).append("\n")
            card.reasons.forEachIndexed { index, reason -> append("${index + 1}. $reason\n") }
            append("\n")
        }
        if (card.actions.isNotEmpty()) {
            append("【现在可以做什么】\n")
            card.actions.forEach { append("· ${it.label}：${it.text}\n") }
            append("\n")
        }
        if (card.replyTiming.isNotBlank()) append("发送时机：${card.replyTiming}\n")
        card.styles.firstOrNull()?.text?.takeIf { it.isNotBlank() }?.let {
            append("\n可以直接发：\n$it\n")
        }
    }.trim()
}
