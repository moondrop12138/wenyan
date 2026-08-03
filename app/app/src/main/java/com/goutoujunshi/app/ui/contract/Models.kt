package com.goutoujunshi.app.ui.contract

/**
 * UI 层所需数据模型（接口层编译占位）。
 * 联调约定：后端在 data 包实现同名实体（Room 表/JSON 解析产物）后，
 * 删除本文件的对应声明并改为 import 后端类型；字段名与 db-schema.md / prompt-architecture.md 对齐。
 */
data class ProviderInfo(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val apiKeyConfigured: Boolean,
    val isPreset: Boolean,
    val sortOrder: Int,
)

data class ModelInfo(
    val id: Long,
    val providerId: Long,
    val providerName: String,
    val name: String,
    val supportsVision: Boolean,
    val isDefault: Boolean,
    val sortOrder: Int,
)

enum class ChatRole { USER, ASSISTANT }

enum class MessageType { TEXT, IMAGE, ANALYSIS, TRANSCRIPTION }

/** 抽屉里的会话列表条目（首条 USER 消息前 30 字当标题） */
data class SessionSummaryUi(
    val id: Long,
    val title: String,
    val createdAt: Long,
)

data class ChatMessageUi(
    val id: Long,
    val role: ChatRole,
    val type: MessageType,
    val content: String,
    val createdAt: Long,
)

/** 五步法单段（prompt-architecture.md §4 steps[]） */
data class AnalysisStep(
    val key: String,
    val title: String,
    val content: String,
    val items: List<String> = emptyList(),
)

/** 五步法结果卡片（UI 渲染契约） */
data class AnalysisCard(
    val steps: List<AnalysisStep> = emptyList(),
    val reply: String = "",
    val replyTiming: String = "",
    val citations: List<String> = emptyList(),
    val safetyOverride: Boolean = false,
    val safetyMessage: String = "",
    val tokenEstimate: Int = 0,
) {
    val conclusion: String get() = steps.firstOrNull { it.key == "advice" }?.content.orEmpty()
}

/** 问卷建档草稿（onboarding 四屏收集，提交到后端入库） */
data class OnboardingDraft(
    val meMbti: String? = null,
    val meScore: Int? = null,
    val strengths: String = "",
    val weaknesses: String = "",
    val targetCodeName: String = "",
    val targetMbti: String? = null,
    val targetScore: Int? = null,
    val relationStatus: String? = null,
    val meetWay: String? = null,
    val duration: String? = null,
    val keyEvents: String = "",
    val investment: String? = null,
    val goal: String? = null,
    val painPoint: String = "",
    val emotionIntensity: Int = 0,
    val urgentReply: Boolean = false,
    val urgentText: String = "",
)
