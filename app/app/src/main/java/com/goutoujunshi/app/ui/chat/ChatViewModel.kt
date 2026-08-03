package com.goutoujunshi.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goutoujunshi.app.ui.contract.AnalysisCard
import com.goutoujunshi.app.ui.contract.AnalysisMode
import com.goutoujunshi.app.ui.contract.ChatMessageUi
import com.goutoujunshi.app.ui.contract.ChatRepository
import com.goutoujunshi.app.ui.contract.LlmError
import com.goutoujunshi.app.ui.contract.StreamEvent
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

    private val _lastError = MutableStateFlow<LlmError?>(null)
    val lastError: StateFlow<LlmError?> = _lastError.asStateFlow()

    private val _transcription = MutableStateFlow<String?>(null)
    val transcription: StateFlow<String?> = _transcription.asStateFlow()

    private val _transcribing = MutableStateFlow(false)
    val transcribing: StateFlow<Boolean> = _transcribing.asStateFlow()

    private val _currentModelName = MutableStateFlow("未配置")
    val currentModelName: StateFlow<String> = _currentModelName.asStateFlow()

    private var streamJob: Job? = null
    private var lastSend: Pair<String, AnalysisMode>? = null

    init {
        viewModelScope.launch {
            repo.messages.collect { _messages.value = it }
        }
        viewModelScope.launch {
            repo.currentModelName.collect { _currentModelName.value = it }
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun sendText(mode: AnalysisMode = AnalysisMode.FIVE_STEP) {
        val text = _input.value.trim()
        if (text.isEmpty() || _streaming.value) return
        lastSend = text to mode
        _input.value = ""
        startStream { repo.sendText(text, mode) }
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

    fun stop() {
        repo.cancel()
        streamJob?.cancel()
        streamJob = null
        _streaming.value = false
    }

    private fun startStream(producer: () -> kotlinx.coroutines.flow.Flow<StreamEvent>) {
        _streaming.value = true
        _streamingText.value = ""
        _lastError.value = null
        streamJob = viewModelScope.launch {
            producer().collect { event ->
                when (event) {
                    is StreamEvent.Delta -> _streamingText.value += event.text
                    is StreamEvent.Analysis -> {
                        // 完整结果由后端持久化为 analysis 消息；UI 流式文本清空
                        _streamingText.value = ""
                        _streaming.value = false
                    }
                    is StreamEvent.Transcription -> {
                        _transcription.value = event.text
                        _streamingText.value = ""
                        _streaming.value = false
                        _transcribing.value = false
                    }
                    is StreamEvent.Error -> {
                        _lastError.value = event.error
                        _streaming.value = false
                    }
                    StreamEvent.Done -> {
                        _streamingText.value = ""
                        _streaming.value = false
                    }
                }
            }
        }
    }
}
