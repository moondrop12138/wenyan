package com.wenyan.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.ui.contract.AnalysisCard
import com.wenyan.app.ui.contract.AnalysisMode
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.ChatRepository
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.contract.StreamEvent
import kotlinx.coroutines.Job
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

    private var streamJob: Job? = null
    private var lastSend: Pair<String, AnalysisMode>? = null

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
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun sendText(mode: AnalysisMode? = null) {
        val text = _input.value.trim()
        if (text.isEmpty() || _streaming.value) return
        // 启发式路由：调用方未显式指定时，按输入形态判断（v1.2 四分）
        //  - 完整聊天记录粘贴（多行/引号/超 40 字）→ FIVE_STEP 五步法
        //  - 转述对方的话（"她说我们只是朋友"）→ RELAYED 先解读对方意图
        //  - 用户自己的简短输入（提问/倾诉）→ REPLY 共情 + 话术
        //  - 纯打招呼 → GREETING 轻量开场
        val resolved = mode ?: routeByInputShape(text)
        lastSend = text to resolved
        _input.value = ""
        startStream { repo.sendText(text, resolved) }
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

    fun analyzeImage(uri: Uri) {
        if (_streaming.value) return
        startStream { repo.analyzeImage(uri) }
    }

    fun confirmTranscription(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _streaming.value) return
        _transcription.value = null
        startStream { repo.confirmTranscription(t) }
    }

    fun retry() {
        val last = lastSend ?: return
        startStream { repo.sendText(last.first, last.second) }
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
        repo.cancel()
        streamJob?.cancel()
        streamJob = null
        _streaming.value = false
    }

    private fun startStream(producer: () -> kotlinx.coroutines.flow.Flow<StreamEvent>) {
        _streaming.value = true
        _streamingText.value = ""
        _streamingThinking.value = ""
        _lastError.value = null
        streamJob = viewModelScope.launch {
            producer().collect { event ->
                when (event) {
                    is StreamEvent.Delta -> _streamingText.value += event.text
                    is StreamEvent.Thinking -> _streamingThinking.value += event.text
                    is StreamEvent.Analysis -> {
                        // 完整结果由后端持久化为 analysis 消息；UI 流式文本清空
                        _streamingText.value = ""
                        _streamingThinking.value = ""
                        _streaming.value = false
                    }
                    is StreamEvent.Transcription -> {
                        _transcription.value = event.text
                        _streamingText.value = ""
                        _streamingThinking.value = ""
                        _streaming.value = false
                        _transcribing.value = false
                    }
                    is StreamEvent.Error -> {
                        _lastError.value = event.error
                        _streaming.value = false
                    }
                    StreamEvent.Done -> {
                        _streamingText.value = ""
                        _streamingThinking.value = ""
                        _streaming.value = false
                    }
                    StreamEvent.FreeTextDone -> {
                        // v1.3 freetext：完整文本已由 repo 持久化为 freetext 消息，
                        // Room Flow 自动刷新列表；这里只需清流式态
                        _streamingText.value = ""
                        _streamingThinking.value = ""
                        _streaming.value = false
                    }
                }
            }
        }
    }
}
