package com.wenyan.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.ui.contract.AnalysisMode
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.ChatRepository
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.SessionSummaryUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 对话首页状态（AC-04/05/07/08/10/14/15）：
 * 消息流来自 repo.messages（后端持久化响应式刷新）；本层只维护输入/流式/错误/转述等 UI 态。
 */
class ChatViewModel(private val repo: ChatRepository) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _streamingThinking = MutableStateFlow("")
    val streamingThinking: StateFlow<String> = _streamingThinking.asStateFlow()

    private val _lastError = MutableStateFlow<LlmError?>(null)
    val lastError: StateFlow<LlmError?> = _lastError.asStateFlow()

    private val _transcription = MutableStateFlow<String?>(null)
    val transcription: StateFlow<String?> = _transcription.asStateFlow()

    private val _transcribing = MutableStateFlow(false)
    val transcribing: StateFlow<Boolean> = _transcribing.asStateFlow()

    private val _currentModelName = MutableStateFlow("未配置")
    val currentModelName: StateFlow<String> = _currentModelName.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionSummaryUi>>(emptyList())
    val sessions: StateFlow<List<SessionSummaryUi>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    /** v1.3.1 待发送图片：选图后暂存（输入框上方预览区），点发送才真正发出 */
    private val _pendingImage = MutableStateFlow<Uri?>(null)
    val pendingImage: StateFlow<Uri?> = _pendingImage.asStateFlow()

    /** 最近一次发送（v1.3.1 携带可选图片 uri，供 retry 复用图文重试） */
    private var lastSend: LastSend? = null

    init {
        viewModelScope.launch {
            repo.messages.collect { _messages.value = it }
        }
        viewModelScope.launch {
            repo.currentModelName.collect { _currentModelName.value = it }
        }
        viewModelScope.launch {
            repo.sessions.collect { _sessions.value = it }
        }
        viewModelScope.launch {
            repo.currentSessionId.collect { _currentSessionId.value = it }
        }
        // v1.3.1 流式状态中枢订阅：repo 在应用级 scope 收集（息屏/退后台不中断），
        // Activity 重建后新 VM 订阅即恢复进行中的流式状态
        viewModelScope.launch {
            repo.streamingState.collect { st ->
                _streaming.value = st.streaming
                _streamingText.value = st.text
                _streamingThinking.value = st.thinking
                _transcription.value = st.transcription
                _transcribing.value = st.transcribing
                _lastError.value = st.error
                // 预落库图片错误（读取/过大/压缩失败）→ 图片未发出，恢复待发送区与配文供重试
                if (!st.streaming && st.error != null) {
                    val last = lastSend
                    if (last?.uri != null && st.error.code in RESTORE_PENDING_CODES) {
                        _pendingImage.value = last.uri
                        _input.value = last.text
                    }
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun sendText(mode: AnalysisMode? = null) {
        val text = _input.value.trim()
        if (text.isEmpty() || _streaming.value) return
        // 启发式路由：调用方未显式指定时，按输入形态判断（v1.2 四分；v1.6 起仅决定 user 模板，
        // 输出统一四段结构）——完整聊天记录粘贴 → buildUserText；短句 → buildUserReply（轻量四段）
        //  - 完整聊天记录粘贴（多行/引号/超 40 字）→ FIVE_STEP 全量分析
        //  - 转述对方的话（"她说我们只是朋友"）→ RELAYED 先解读对方意图
        //  - 用户自己的简短输入（提问/倾诉）→ REPLY 共情 + 话术
        //  - 纯打招呼 → GREETING 轻量开场
        val resolved = mode ?: routeByInputShape(text)
        lastSend = LastSend(null, text, resolved)
        _input.value = ""
        repo.sendTextAsync(text, resolved)
    }

    /**
     * 输入四分路由（v1.2）。
     *
     * 优先级：FIVE_STEP > RELAYED > GREETING > REPLY。
     * 关键修复：把「转述对方的话」从 REPLY 里拆出来——此前"她说我们只是朋友"
     * 会被当成用户自己的发言走共情+推进话术，方向完全反了（应先解读对方在划清边界）。
     */
    internal fun routeByInputShape(text: String): AnalysisMode {
        val trimmed = text.trim()
        val isMultiLine = trimmed.contains('\n')
        val hasQuotes = trimmed.any { it == '"' || it == '“' || it == '”' || it == '\'' || it == '‘' || it == '’' }
        val looksLikeChatLog = trimmed.contains("：") && trimmed.contains("\n")

        // 完整聊天记录优先，避免大段粘贴被转述信号截胡
        if (looksLikeChatLog || isMultiLine || hasQuotes || trimmed.length > 40) {
            return AnalysisMode.FIVE_STEP
        }

        if (looksLikeRelayedQuote(trimmed)) return AnalysisMode.RELAYED
        if (looksLikeGreeting(trimmed)) return AnalysisMode.GREETING
        return AnalysisMode.REPLY
    }

    /**
     * 转述信号：第三人称主语 + 引语动词。只覆盖高置信度特征（短句前提下），
     * 拿不准的仍落 REPLY，由模型在 prompt 里做最终语境判断（uncertain 时反问）。
     */
    private fun looksLikeRelayedQuote(text: String): Boolean =
        RELAYED_PATTERN.containsMatchIn(text)

    private fun looksLikeGreeting(text: String): Boolean =
        text.length <= 10 && GREETING_PATTERN.containsMatchIn(text)

    companion object {
        /** v1.3.1 最近一次发送记录（retry 复用；uri 非空 = 图文/纯图发送） */
        private data class LastSend(
            val uri: Uri?,
            val text: String,
            val mode: AnalysisMode,
        )

        /** v1.3.1 预落库错误码：图片尚未写入，失败后恢复待发送区供重试 */
        private val RESTORE_PENDING_CODES = setOf("READ_FAILED", "TOO_LARGE", "COMPRESS_FAILED")

        /** "她说/他说/TA说/对方回/她回了句…" 等第三人称转述信号 */
        private val RELAYED_PATTERN = Regex(
            "(他|她|TA|ta|对方|那人|那个|这人|这个)[^，。！？\\n]{0,4}(说|问|回|答|讲|提|发|写)"
        )

        /** 纯打招呼：你好/hi/在吗 类，长度≤10 字 */
        private val GREETING_PATTERN = Regex(
            "^(你好|您好|hi|hello|hey|嗨|喂|在吗|在么|在不在|早|早上好|晚上好|下午好)[！!~。\\s]*$",
            RegexOption.IGNORE_CASE
        )
    }

    /** v1.3.1 选图后暂存为待发送（输入框上方预览区展示）；流式期间也允许先暂存 */
    fun setPendingImage(uri: Uri?) {
        _pendingImage.value = uri
    }

    fun dismissPendingImage() {
        _pendingImage.value = null
    }

    /**
     * v1.3.1 统一发送入口（DeepSeek 风格）：
     * - 有图 + 有字 → 图文同发（配文按输入形态路由 mode，空则纯图五步法）
     * - 有图无字 → 纯图分析；无图有字 → 走 sendText
     * 发送后清空待发送区与输入框。
     */
    fun sendPending() {
        if (_streaming.value) return
        val uri = _pendingImage.value
        val text = _input.value.trim()
        if (uri == null && text.isEmpty()) return
        if (uri == null) {
            sendText()
            return
        }
        val resolved = if (text.isEmpty()) AnalysisMode.FIVE_STEP else routeByInputShape(text)
        lastSend = LastSend(uri, text, resolved)
        _input.value = ""
        _pendingImage.value = null
        repo.analyzeImageAsync(uri, text, resolved)
    }

    fun confirmTranscription(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _streaming.value) return
        repo.confirmTranscriptionAsync(t)
    }

    fun retry() {
        val last = lastSend ?: return
        // v1.3.1 失败重试：persistUser=false——用户消息首次发送已落库，重试不再重复发一遍
        if (last.uri != null) {
            repo.analyzeImageAsync(last.uri, last.text, last.mode, persistUser = false)
        } else {
            repo.sendTextAsync(last.text, last.mode, persistUser = false)
        }
    }

    /** 长按菜单删除单条消息；Room Flow 自动刷新 messages，无需手动改 state */
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { repo.deleteMessage(messageId) }
    }

    fun switchSession(sessionId: Long) {
        viewModelScope.launch { repo.switchSession(sessionId) }
    }

    fun startNewSession() {
        viewModelScope.launch { repo.startNewSession() }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repo.deleteSession(sessionId) }
    }

    fun stop() {
        // v1.3.1 停止 = 取消 repo 应用级收集 job（流式状态中枢随之复位）
        repo.cancel()
    }
}
