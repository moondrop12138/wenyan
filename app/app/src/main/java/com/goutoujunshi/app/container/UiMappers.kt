package com.goutoujunshi.app.container

import com.goutoujunshi.app.data.db.MessageEntity
import com.goutoujunshi.app.data.db.ModelEntity
import com.goutoujunshi.app.data.db.ProviderEntity
import com.goutoujunshi.app.llm.AnalysisParser
import com.goutoujunshi.app.llm.FiveStepAnalysis
import com.goutoujunshi.app.llm.LlmErrorCode
import com.goutoujunshi.app.ui.contract.AnalysisCard
import com.goutoujunshi.app.ui.contract.AnalysisStep
import com.goutoujunshi.app.ui.contract.ChatMessageUi
import com.goutoujunshi.app.ui.contract.ChatRole
import com.goutoujunshi.app.ui.contract.LlmError
import com.goutoujunshi.app.ui.contract.MessageType
import com.goutoujunshi.app.ui.contract.ModelInfo
import com.goutoujunshi.app.ui.contract.ProviderInfo

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
            else -> MessageType.TEXT
        },
        content = e.content,
        createdAt = e.createdAt,
    )

    /** FiveStepAnalysis → UI AnalysisCard（prompt-architecture §4/§5） */
    fun toAnalysisCard(a: FiveStepAnalysis): AnalysisCard = AnalysisCard(
        steps = a.steps.map { s ->
            AnalysisStep(
                key = s.key,
                title = s.title.ifBlank { defaultTitle(s.key) },
                content = s.content,
                items = s.items,
            )
        },
        reply = a.reply,
        replyTiming = a.replyTiming,
        citations = a.citations,
        safetyOverride = a.safetyOverride,
        safetyMessage = a.safetyMessage,
        tokenEstimate = a.tokenEstimate ?: 0,
    )

    /**
     * 防御性解析模型输出为 AnalysisCard（prompt-architecture §6）：
     * 解析失败返回 null（UI 显示错误卡）。收敛了原 ui/chat/AnalysisParser 职责。
     */
    fun parseAnalysisCard(json: String): AnalysisCard? = try {
        toAnalysisCard(AnalysisParser.parse(json))
    } catch (e: Exception) {
        null
    }

    private fun defaultTitle(key: String): String = when (key) {
        "emotion" -> "情绪落地"
        "facts" -> "事实拆分"
        "interests" -> "利益判断"
        "advice" -> "明确建议"
        "action" -> "行动收束"
        else -> "模型未输出该部分"
    }

    /** LlmErrorCode → UI LlmError（llm-contract §4） */
    fun toLlmError(code: LlmErrorCode): LlmError = LlmError(
        code = code.name,
        message = code.userMessage,
        retryable = code.retryable,
    )
}
