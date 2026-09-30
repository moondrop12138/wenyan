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
import kotlinx.coroutines.flow.combine
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

    private val _lastError = MutableStateFlow<LlmError?>(null)
    val lastError: StateFlow<LlmError?> = _lastError.asStateFlow()

    /** v1.9.0 自动记忆写入回执（一次性 toast，UI 消费后调 consumeMemoryReceipt 清空） */
    private val _memoryReceipt = MutableStateFlow<String?>(null)
    val memoryReceipt: StateFlow<String?> = _memoryReceipt.asStateFlow()

    fun consumeMemoryReceipt() {
        _memoryReceipt.value = null
    }

    /** H3: 一次性提示（如解析失败兜底「已展示原文」），UI toast 后清空 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun consumeNotice() {
        _notice.value = null
    }

    private val _transcription = MutableStateFlow<String?>(null)
    val transcription: StateFlow<String?> = _transcription.asStateFlow()

    private val _transcribing = MutableStateFlow(false)
    val transcribing: StateFlow<Boolean> = _transcribing.asStateFlow()

    /** v1.9.2 确认转述后的分析阶段（等待文案三档：军师分析中…） */
    private val _confirming = MutableStateFlow(false)
    val confirming: StateFlow<Boolean> = _confirming.asStateFlow()

    private val _currentModelName = MutableStateFlow("未配置")
    val currentModelName: StateFlow<String> = _currentModelName.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionSummaryUi>>(emptyList())
    val sessions: StateFlow<List<SessionSummaryUi>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    /** O3: 全文搜索（查询 + 命中去重 sessionId） */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchResults = MutableStateFlow<List<Long>>(emptyList())
    val searchResults: StateFlow<List<Long>> = _searchResults.asStateFlow()

    /** F34 修复：searchResults 所属的查询词——抽屉只在两者对应时才并入全文命中，
     *  消除 300ms 去抖窗口内「旧关键词结果 ∪ 新关键词标题匹配」的混排 */
    private val _searchResultsQuery = MutableStateFlow("")
    val searchResultsQuery: StateFlow<String> = _searchResultsQuery.asStateFlow()

    /** v1.3.1 待发送图片（v1.6.1 多图：最多 10 张，选图后暂存，点发送才真正发出） */
    private val _pendingImages = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImages: StateFlow<List<Uri>> = _pendingImages.asStateFlow()

    /** 最近一次发送（v1.3.1 携带可选图片 uri；v1.6.1 多图列表，供 retry 复用图文重试） */
    private var lastSend: LastSend? = null

    /**
     * F32 修复：预落库失败的待恢复标记（发送置位、错误到达即消费）。
     * 错误态在 repo 中长期驻留、combine 任一入流变化即重发射——若恢复逻辑随重发射重放，
     * 切走再切回该会话会拿 lastSend 覆盖用户此后编辑过的输入并找回已删除的图片。
     */
    private var pendingRestore = false

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
        // v1.3.1 流式状态中枢订阅（H5/M18 门控版）：repo 在应用级 scope 收集（息屏/退后台不中断），
        // Activity 重建后新 VM 订阅即恢复进行中的流式状态。
        // 状态带归属 sessionId——仅当归属会话 == 当前查看会话才映射到 UI 态：
        // 其他会话的后台流不再产生假「思考中」、跨会话转述卡与错误卡；
        // combine 保证切会话瞬间立即重新求值（而非等下一次状态发射才纠正）。
        viewModelScope.launch {
            combine(repo.streamingState, repo.currentSessionId) { st, viewSid -> st to viewSid }
                .collect { (st, viewSid) ->
                    val mine = st.sessionId == viewSid
                    _streaming.value = mine && st.streaming
                    _transcribing.value = mine && st.transcribing
                    _transcription.value = if (mine) st.transcription else null
                    _lastError.value = if (mine) st.error else null
                    // v1.9.2 确认转述后的分析结束（done/error/transcription 后本会话视角空闲）→ 复位 confirming
                    if (!_streaming.value) {
                        _confirming.value = false
                    }
                    // 预落库图片错误（读取/过大/压缩失败）→ 图片未发出，恢复待发送区与配文供重试。
                    // F32 修复：恢复是一次性动作——pendingRestore 在发送时置位、此处消费；
                    // 错误驻留 + 切走再切回的 combine 重发射不再重放恢复（不再覆盖当前输入）
                    if (!st.streaming && st.error != null && mine && pendingRestore) {
                        pendingRestore = false
                        val last = lastSend
                        if (last?.uris?.isNotEmpty() == true && st.error.code in RESTORE_PENDING_CODES) {
                            _pendingImages.value = last.uris
                            _input.value = last.text
                        }
                    }
                }
        }
        // M22 修复：回执/提示改事件订阅（repo SharedFlow replay=0）——原 StateFlow 字段
        // 旋转后重放导致 toast 重复弹，且相同文案被去重导致第二次丢失。
        viewModelScope.launch {
            repo.memoryReceiptEvents.collect { _memoryReceipt.value = it }
        }
        viewModelScope.launch {
            repo.noticeEvents.collect { _notice.value = it }
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    /** O3: 全文搜索（空白清空结果）。
     *  L21 修复：原每次按键独立 launch 无取消/去抖——慢的旧查询结果覆盖新结果
     *  （显示与输入不一致）。改 debounce(300) + collectLatest：新查询自动取消旧查询。 */
    private var searchJobs: kotlinx.coroutines.Job? = null

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJobs?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchResultsQuery.value = ""
            return
        }
        searchJobs = viewModelScope.launch {
            kotlinx.coroutines.delay(300)   // 去抖：停顿 300ms 才发起
            // F34 修复：先落结果、后落归属 query——两发射之间结果仍标记为旧词、
            // 抽屉不会把旧结果并入新词的过滤
            _searchResults.value = repo.searchSessions(query)
            _searchResultsQuery.value = query
        }
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
        // （F75：路由逻辑抽到无状态 InputShapeRouter，RouteByInputShapeTest 直接测真实实现）
        val resolved = mode ?: InputShapeRouter.route(text)
        lastSend = LastSend(emptyList(), text, resolved)
        pendingRestore = false // 纯文本发送无待恢复图片（F32 标记只服务图文发送）
        _input.value = ""
        repo.sendTextAsync(text, resolved)
    }

    companion object {
        /** v1.3.1 最近一次发送记录（retry 复用；uris 非空 = 图文/纯图发送） */
        private data class LastSend(
            val uris: List<Uri>,
            val text: String,
            val mode: AnalysisMode,
        )

        /** v1.6.1 待发送图片上限：一次最多选 10 张 */
        const val MAX_PENDING_IMAGES = 10

        /** v1.3.1 预落库错误码：图片尚未写入，失败后恢复待发送区供重试 */
        private val RESTORE_PENDING_CODES = setOf("READ_FAILED", "TOO_LARGE", "COMPRESS_FAILED")
    }

    /**
     * v1.6.1 选图后追加为待发送（多图合并：去重 + 上限 10 张；超出部分静默截断）。
     * 流式期间也允许先暂存。
     */
    fun addPendingImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val merged = (_pendingImages.value + uris).distinct()
        _pendingImages.value = merged.take(MAX_PENDING_IMAGES)
    }

    /** v1.6.1 移除指定待发送图片（预览区缩略图右上角删除角标） */
    fun removePendingImage(uri: Uri) {
        _pendingImages.value = _pendingImages.value.filterNot { it == uri }
    }

    /**
     * v1.3.1 统一发送入口（DeepSeek 风格；v1.6.1 多图）：
     * - 有图 + 有字 → 图文同发（配文按输入形态路由 mode，空则纯图五步法）
     * - 有图无字 → 纯图分析；无图有字 → 走 sendText
     * 发送后清空待发送区与输入框。
     */
    fun sendPending() {
        if (_streaming.value) return
        val uris = _pendingImages.value
        val text = _input.value.trim()
        if (uris.isEmpty() && text.isEmpty()) return
        if (uris.isEmpty()) {
            sendText()
            return
        }
        val resolved = if (text.isEmpty()) AnalysisMode.FIVE_STEP else InputShapeRouter.route(text)
        lastSend = LastSend(uris, text, resolved)
        // F32：图文发送的预落库失败可能需要恢复待发送区，置位一次性恢复标记
        pendingRestore = true
        _input.value = ""
        _pendingImages.value = emptyList()
        repo.analyzeImagesAsync(uris, text, resolved)
    }

    fun confirmTranscription(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _streaming.value) return
        // v1.9.2 等待文案三档：确认转述后进入主模型分析 → 「军师分析中…」
        _confirming.value = true
        // H5 修复：携带转述卡来源会话 id（渲染门控保证卡只在归属会话可见，
        // repo 落库以该 sid 为准，快速切会话不再把 A 会话的转述写进 B）
        repo.confirmTranscriptionAsync(t, _currentSessionId.value)
    }

    fun retry() {
        val last = lastSend ?: return
        // v1.3.1 失败重试：persistUser=false——用户消息首次发送已落库，重试不再重复发一遍。
        // F30 修复：预落库类失败（READ_FAILED/TOO_LARGE/COMPRESS_FAILED）发生在落库之前
        //（repo analyzeImagesFlow 未写任何消息即报错），重试必须补落库（persistUser=true），
        // 否则用户图片与配文永远不进库，AI 回复成为没有对应用户消息的孤儿记录
        val errorCode = _lastError.value?.code
        val needPersistUser = errorCode != null && errorCode in RESTORE_PENDING_CODES
        if (last.uris.isNotEmpty()) {
            repo.analyzeImagesAsync(last.uris, last.text, last.mode, persistUser = needPersistUser)
        } else {
            repo.sendTextAsync(last.text, last.mode, persistUser = needPersistUser)
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
