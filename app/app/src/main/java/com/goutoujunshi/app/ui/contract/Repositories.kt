package com.goutoujunshi.app.ui.contract

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Repository 接口层编译占位（联调约定：后端在 data/repository 包提供实现，本文件为 UI 侧所需形状）。
 * 流式契约：sendText 返回 Flow<StreamEvent>，增量文本经 Delta 推送（callbackFlow + Main 线程，见 llm-contract.md §3）。
 */
interface ChatRepository {
    /** 会话消息流（响应式刷新） */
    val messages: Flow<List<ChatMessageUi>>

    /** 发送文本分析（"这句怎么回"用 mode=REPLY，其余 FIVE_STEP） */
    fun sendText(text: String, mode: AnalysisMode): Flow<StreamEvent>

    /**
     * 截图分析（双通道分流，AC-07/AC-08）：后端内部做压缩管线（≤1568px/85%）后，
     * 主模型 supportsVision=true 走通道 A 直读，否则走通道 B 视觉转述（转述结果经 TranscriptionEvent 回传）。
     */
    fun analyzeImage(uri: Uri): Flow<StreamEvent>

    /** 通道 B 转述确认后，携用户可编辑的转述文本继续主模型分析 */
    fun confirmTranscription(transcription: String): Flow<StreamEvent>

    /** 删除单条消息（长按菜单删除；Room Flow 自动刷新列表） */
    suspend fun deleteMessage(messageId: Long)

    /** 停止当前流 */
    fun cancel()

    /** 当前主模型名（顶部切换器显示） */
    val currentModelName: Flow<String>
}

enum class AnalysisMode { FIVE_STEP, REPLY }

/** 流式事件（UI 渲染依据） */
sealed interface StreamEvent {
    /** 增量文本（打字机渲染） */
    data class Delta(val text: String) : StreamEvent

    /** 五步法完整解析结果（流结束后整卡渲染；防御性解析见 prompt-architecture.md §6） */
    data class Analysis(val card: AnalysisCard) : StreamEvent

    /** 通道 B 转述文本（UI 渲染"AI 从截图中读出了这些内容"确认卡） */
    data class Transcription(val text: String) : StreamEvent

    /** 错误（LlmError 已归一，UI 只认文案+可重试标记） */
    data class Error(val error: LlmError) : StreamEvent

    data object Done : StreamEvent
}

/** 归一错误（llm-contract.md §4 错误码映射，UI 侧 ErrorCard 按 code 取标题文案） */
data class LlmError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
