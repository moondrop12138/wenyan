package com.wenyan.app.ui.contract

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
    /** v1.6.3 连接状态："ok"=测试成功（绿灯），""=未测试/失败（红灯） */
    val connectionStatus: String = "",
)

data class ModelInfo(
    val id: Long,
    val providerId: Long,
    val providerName: String,
    val name: String,
    val supportsVision: Boolean,
    val isDefault: Boolean,
    val sortOrder: Int,
    /** v1.6.3 是否在主页"选择模型"弹层展示（模型管理里切换） */
    val showInSheet: Boolean = true,
)

enum class ChatRole { USER, ASSISTANT }

enum class MessageType { TEXT, IMAGE, ANALYSIS, TRANSCRIPTION, FREETEXT }

/** 抽屉里的会话列表条目（首条 USER 消息前 30 字当标题） */
data class SessionSummaryUi(
    val id: Long,
    val title: String,
    val createdAt: Long,
    /** v1.7.2 会话归属记忆档案名；老会话（targetId=null）为 null，抽屉不显示 Tag */
    val targetName: String? = null,
    /** v1.7.3-fix 会话归属档案 id（分组键）；老会话为 null（归「未关联」组） */
    val targetId: Long? = null,
)

/** v1.7.2 记忆档案（设置页「记忆」分组）；v1.7.3 扩展结构化字段（详情页可编辑） */
data class TargetUi(
    val id: Long,
    val name: String,          // = codeName（档案名称，用户可编辑）
    val note: String,          // 记忆正文（跨会话记忆内容；v1.7.3 起代码层废弃，列保留）
    val createdAt: Long,
    val isActive: Boolean,     // id == 激活档案
    // v1.7.3 结构化字段（详情页编辑）
    val mbti: String? = null,
    val score: Int? = null,
    val relationStatus: String? = null,
    val timeline: String = "[]",   // 关键事件 JSON 数组字符串
    /** v1.7.3-fix 该档案已记住事实条数（设置页 caption 展示，替代已废弃的 note.length） */
    val factCount: Int = 0,
)

/** v1.7.3 单条已记住事实（MemoryEditScreen 列表管理）；v1.9.0 加 kind（fact/hypothesis） */
data class MemoryFactUi(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val kind: String = "fact",
)

data class ChatMessageUi(
    val id: Long,
    val role: ChatRole,
    val type: MessageType,
    val content: String,
    val createdAt: Long,
)

/** 输入语境（v1.2，与 llm InputKind 对齐） */
enum class InputKindUi { USER_QUESTION, RELAYED_QUOTE, PASTED_CHAT, GREETING, UNCERTAIN, UNKNOWN }

/** 话术风格（v1.6 三风格：稳健/会撩/强势；切换纯本地不重请求） */
data class ScriptStyle(
    val key: String,
    val label: String,
    val text: String,
)

/** 行动清单项（v1.6：小动作/观察窗口/停止条件） */
data class ActionItemUi(
    val label: String,
    val text: String,
)

/** v1.6 四段结构回答卡（UI 渲染契约，prompt-architecture.md §4 schema v2） */
data class CoachCard(
    /** 接住你：共情段落 */
    val empathy: String = "",
    /** 先分清事实：事实/推测/未知 三组 */
    val factsKnown: List<String> = emptyList(),
    val factsAssumed: List<String> = emptyList(),
    val factsUnknown: List<String> = emptyList(),
    /** 军师建议：策略标签（可空）+ 核心建议句 + 编号理由 + 三风格话术 */
    val adviceTag: String = "",
    val adviceCore: String = "",
    val reasons: List<String> = emptyList(),
    val styles: List<ScriptStyle> = emptyList(),
    /** 现在可以做什么：行动清单（纯展示） */
    val actions: List<ActionItemUi> = emptyList(),
    val reply: String = "",
    val replyTiming: String = "",
    val citations: List<String> = emptyList(),
    /** v1.7.3 记忆引用溯源：实际依据的已记住事实原文（≤3 条，未使用空数组） */
    val memoryCitations: List<String> = emptyList(),
    val safetyOverride: Boolean = false,
    val safetyMessage: String = "",
    val tokenEstimate: Int = 0,
    val inputKind: InputKindUi = InputKindUi.UNKNOWN,
) {
    /** UNCERTAIN 时 reply 是反问句而非成品话术：前端据此隐藏"复制话术"按钮 */
    val isClarification: Boolean get() = inputKind == InputKindUi.UNCERTAIN
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
