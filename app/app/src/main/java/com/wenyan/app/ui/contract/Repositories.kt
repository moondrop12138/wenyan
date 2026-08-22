package com.wenyan.app.ui.contract

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository 接口层编译占位（联调约定：后端在 data/repository 包提供实现，本文件为 UI 侧所需形状）。
 * 流式契约：sendText 返回 Flow<StreamEvent>，增量文本经 Delta 推送（callbackFlow + Main 线程，见 llm-contract.md §3）。
 * v1.3.1：新增 async 发送族（应用级 scope 收集，后台/息屏不中断）+ streamingState 状态中枢；
 * persistUser=false 用于失败重试——用户消息首次发送已落库，重试不再重复落库。
 */
interface ChatRepository {
    /** 会话消息流（响应式刷新） */
    val messages: Flow<List<ChatMessageUi>>

    /** 历史会话列表（DeepSeek 风格抽屉）；按创建时间倒序，标题 = 首条 USER 消息前 30 字 */
    val sessions: Flow<List<SessionSummaryUi>>

    /** 当前选中的会话 id（顶部高亮 / 消息流来源）；null = 尚未创建 */
    val currentSessionId: Flow<Long?>

    /**
     * v1.3.1 流式状态中枢：async 发送族在应用级 scope 收集（Activity 销毁/息屏不中断），
     * 增量文本/思考/转述/错误经此推送；ViewModel 订阅后映射到 UI 态。
     * H5/M18：状态带归属 sessionId，UI 按当前查看会话门控渲染。
     */
    val streamingState: StateFlow<StreamingState>

    /** M22 修复：一次性回执改事件流（replay=0）——原 StateFlow 字段旋转后重放重复弹、相同文案被去重丢失 */
    val memoryReceiptEvents: Flow<String>

    /** M22 修复：一次性提示（解析失败兜底/历史压缩/冲突提示），同上 */
    val noticeEvents: Flow<String>

    /** 发送文本分析（"这句怎么回"用 mode=REPLY，其余 FIVE_STEP） */
    fun sendText(text: String, mode: AnalysisMode): Flow<StreamEvent>

    /**
     * 截图分析（双通道分流，AC-07/AC-08）：后端内部做压缩管线（≤1568px/85%）后，
     * 主模型 supportsVision=true 走通道 A 直读，否则走通道 B 视觉转述（转述结果经 TranscriptionEvent 回传）。
     * v1.3.1 图文同发：text 非空时图片与配文一起落库、一起进 LLM 请求（先图后文）；
     * v1.6.1 多图：uris 最多 10 张，逐张压缩落库后一次 LLM 请求（content 数组多 image_url）；
     * mode 决定通道 A 的回复形态（FIVE_STEP→五步法卡片，其余→freetext 自由文本）。
     */
    fun analyzeImages(
        uris: List<Uri>,
        text: String = "",
        mode: AnalysisMode = AnalysisMode.FIVE_STEP,
    ): Flow<StreamEvent>

    /**
     * 通道 B 转述确认后，携用户可编辑的转述文本继续主模型分析。
     * H5 修复：[sid] = 转述卡来源会话 id（跨会话确认不再落错会话）；null 回退当前会话。
     */
    fun confirmTranscription(transcription: String, sid: Long? = null): Flow<StreamEvent>

    /** v1.3.1 后台续跑发送族：应用级 scope 内收集流式事件并推送 streamingState，返回即不阻塞 */
    fun sendTextAsync(text: String, mode: AnalysisMode, persistUser: Boolean = true)

    fun analyzeImagesAsync(
        uris: List<Uri>,
        text: String = "",
        mode: AnalysisMode = AnalysisMode.FIVE_STEP,
        persistUser: Boolean = true,
    )

    fun confirmTranscriptionAsync(transcription: String, sid: Long? = null)

    /** 删除单条消息（长按菜单删除；Room Flow 自动刷新列表） */
    suspend fun deleteMessage(messageId: Long)

    /** 切换到指定历史会话 */
    suspend fun switchSession(sessionId: Long)

    /** 新建一个空会话（点击抽屉顶部"新建会话"）；sessionId 清空，下次发送时落库 */
    suspend fun startNewSession()

    /** 删除整个会话（长按抽屉条目） */
    suspend fun deleteSession(sessionId: Long)

    /** O3: 全文检索（命中消息 → 去重 sessionId），供抽屉搜索跳转 */
    suspend fun searchSessions(query: String): List<Long>

    /** 停止当前流（取消应用级收集 job；用户消息已落库的不受影响） */
    fun cancel()

    /** 当前主模型名（顶部切换器显示） */
    val currentModelName: Flow<String>
}

/**
 * v1.3.1 流式状态（repo 层状态中枢，ViewModel 订阅映射）：
 * 与 ChatViewModel 原有的 streaming/streamingText/streamingThinking/transcription/transcribing/lastError 一一对应。
 * v1.9.0：自动记忆写入回执改走 memoryReceiptEvents（M22：StateFlow 字段有重放/去重问题，已移除）。
 */
data class StreamingState(
    val streaming: Boolean = false,
    val text: String = "",
    val thinking: String = "",
    val transcription: String? = null,
    val transcribing: Boolean = false,
    val error: LlmError? = null,
    /**
     * H5/M18 修复：本状态归属的会话 id（null = 尚未落库的新会话）。
     * 原全局单份流式状态跨会话共享：切会后假「思考中」、转述卡跨会话渲染、错误卡串场；
     * UI 层按「sessionId == 当前查看会话」门控映射。
     */
    val sessionId: Long? = null,
)

/**
 * 分析模式（v1.2 四分，prompt-architecture.md §3 + SPEC §5）：
 * - FIVE_STEP：完整聊天记录粘贴 → 五步法全量分析
 * - REPLY：用户自己的简短输入（提问/倾诉/"这句怎么回"）→ 共情 + 一句成品话术
 * - RELAYED：用户在转述对方说过的话（"她说我们只是朋友"）→ 先解读对方意图，再给回应话术
 * - GREETING：纯打招呼 → 轻量开场回应
 */
enum class AnalysisMode { FIVE_STEP, REPLY, RELAYED, GREETING }

/** 流式事件（UI 渲染依据） */
sealed interface StreamEvent {
    /** 增量文本（打字机渲染） */
    data class Delta(val text: String) : StreamEvent

    /** 深度思考模型的推理增量（reasoning_content）；UI 折叠展示，不拼入正文 */
    data class Thinking(val text: String) : StreamEvent

    /** 四段结构完整解析结果（v1.6 统一卡片；流结束后整卡渲染；防御性解析见 prompt-architecture.md §6） */
    data class Analysis(val card: CoachCard) : StreamEvent

    /** 通道 B 转述文本（UI 渲染"AI 从截图中读出了这些内容"确认卡） */
    data class Transcription(val text: String) : StreamEvent

    /** 错误（LlmError 已归一，UI 只认文案+可重试标记） */
    data class Error(val error: LlmError) : StreamEvent

    data object Done : StreamEvent

    /** H1: LLM 重试前发出，UI 清空已累积的增量文本/思考再继续渲染 */
    data object Restart : StreamEvent
}

/** 归一错误（llm-contract.md §4 错误码映射，UI 侧 ErrorCard 按 code 取标题文案） */
data class LlmError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
