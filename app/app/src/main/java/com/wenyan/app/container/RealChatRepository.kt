package com.wenyan.app.container

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wenyan.app.data.datastore.SettingsRepository as DataStoreSettings
import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.data.image.ImageCompressor
import com.wenyan.app.data.image.ImageSpec
import com.wenyan.app.data.repository.ConversationRepository
import com.wenyan.app.data.repository.ProfileRepository
import com.wenyan.app.data.repository.ProviderRepository
import com.wenyan.app.domain.ChatOrchestrator
import com.wenyan.app.domain.ConversationState
import com.wenyan.app.domain.ConversationStateTracker
import com.wenyan.app.domain.HistoryCompactor
import com.wenyan.app.domain.MemoryConflictDetector
import com.wenyan.app.domain.MemoryExtractor
import com.wenyan.app.knowledge.CrisisDetector
import com.wenyan.app.knowledge.KnowledgeEngine
import com.wenyan.app.llm.AnalysisParser
import com.wenyan.app.llm.ChatHistoryMessage
import com.wenyan.app.llm.ChatRequest
import com.wenyan.app.llm.CoachAnalysis
import com.wenyan.app.llm.LlmClient
import com.wenyan.app.llm.LlmEvent
import com.wenyan.app.prompt.PromptBuilder
import com.wenyan.app.ui.contract.AnalysisMode
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.ChatRepository
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.contract.StreamEvent
import com.wenyan.app.ui.contract.StreamingState
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * 对话 Repository 真实实现（AC-04/05/06/07/08/13/14/15）
 *
 * sendText 编排：危机预检 → 知识路由 → prompt 三层拼装 → LLM 流 → 持久化 → 五步法解析。
 * analyzeImages 双通道（v1.6.1 多图）：主模型 supportsVision=true 走通道 A 直读，否则走通道 B 视觉转述。
 * v1.3.1：async 发送族在应用级 appScope 收集（Activity 销毁/息屏不中断），
 * 流式增量经 streamingState 推送；persistUser=false 供失败重试（用户消息不重复落库）。
 * v1.9.4：当前会话 id 持久化到 DataStore 并写穿，冷启动自动恢复上次打开的对话
 * （修复澎湃OS 等夜间杀进程后聊天页空态、历史抽屉无「当前会话」高亮的问题）。
 */
class RealChatRepository(
    private val context: Context,
    private val dataStore: DataStoreSettings,
    /** F11：容器注入的进程内共享 OkHttpClient（LlmClient 默认参数每次 build 新实例，
     *  独立 ConnectionPool/Dispatcher——跨请求重新 TCP+TLS 握手 + 线程池 churn） */
    private val httpClient: OkHttpClient,
    private val conversationRepository: ConversationRepository,
    private val profileRepository: ProfileRepository,
    private val providerRepository: ProviderRepository,
    private val knowledgeEngine: KnowledgeEngine,
    private val promptBuilder: PromptBuilder,
    private val imageCompressor: ImageCompressor,
) : ChatRepository {

    private val sessionId = MutableStateFlow<Long?>(null)

    /**
     * v1.9.4 隔夜冷启动恢复竞态标记：恢复是异步的，若用户在恢复完成前已主动
     * 切换/新建会话（包括 startNewSession 回到空态这种「当前值为 null」的选择），
     * 恢复流程不得用持久化的旧 id 覆盖较新的选择。
     */
    @Volatile
    private var sessionChosenByUser = false

    /** v1.3 对话状态机：本地结构化跟踪，驱动同题追问不复读 */
    private val stateTracker = ConversationStateTracker()

    /** v1.2.1 会话标题生成 scope：独立于发送流，发射即返回不阻塞 UI；失败静默 */
    private val titleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** v1.7.2 自动记忆提炼 scope：独立于发送流，不阻塞主流程；失败静默（仿 titleScope） */
    private val memoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * v1.7.4 提炼并发互斥（per-target）：同档案提炼串行（读快照 → LLM → 插入非原子，
     * 并发会基于同一快照重复插入）；不同档案互不阻塞。锁对象常驻不清理（每档案一个
     * Mutex，内存可忽略；避免并发下 remove 竞态）。
     */
    private val extractMutexes = ConcurrentHashMap<Long, Mutex>()

    /**
     * v1.3.1 应用级发送 scope：不随 Activity/ViewModel 销毁，息屏/退后台回答继续完成并落库。
     * 进程被杀则无法续跑（系统回收，除非前台服务——MVP 不做）。
     */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            // M17 修复：发送链路无协程异常兜底——原 appScope 无 CoroutineExceptionHandler，
            // sendTextFlow/analyzeImagesFlow 中任何未捕获异常（DB 写失败、解析异常等）
            // 沿 appScope.launch 直接崩溃进程。兜底：记日志。
            // v1.9.4：原先还在这里无条件复位流式状态，但该兜底拿到不到归属 key，会把
            // 「别的会话正在跑的流」也一起复位（同一类跨会话串状态问题）。异常流的收尾
            // 改由 task 结束回调按归属复位，见 StreamStateHost.onFinished。
            CoroutineExceptionHandler { _, e ->
                Log.e("RealChatRepository", "uncaught in appScope", e)
            }
    )

    /**
     * v1.3.1 流式状态中枢：async 发送族在 appScope 收集后推送，ViewModel 订阅映射。
     * v1.9.4：状态与任务注册表下沉到 [StreamStateHost]（归属 key 与状态同源，可被 JVM 单测覆盖）。
     */
    private val streamHost = StreamStateHost(appScope)
    override val streamingState: StateFlow<StreamingState> = streamHost.state

    /** M22 修复：一次性回执/提示改 SharedFlow（replay=0）——原 StateFlow 字段
     *  在 Activity 旋转后重放导致 toast 重复弹，且相同文案被 StateFlow 去重导致第二次丢失 */
    private val _memoryReceiptEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val memoryReceiptEvents: Flow<String> = _memoryReceiptEvents.asSharedFlow()

    private val _noticeEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val noticeEvents: Flow<String> = _noticeEvents.asSharedFlow()

    private val currentModelId = dataStore.currentModelId
    private val visionModelId = dataStore.visionModelId

    init {
        // v1.9.4 隔夜冷启动恢复：进程被杀（澎湃OS 夜间清理等）后 sessionId 归零，
        // 聊天页空态、历史抽屉无「当前会话」高亮。构造后异步读持久化的上次会话 id
        // 并恢复；失败静默，绝不阻塞 UI（详情见 restoreLastSessionId）。
        appScope.launch { restoreLastSessionId() }
    }

    /**
     * v1.9.4 冷启动恢复上次会话：读 current_session_id → 校验该会话仍存在
     * （不存在则清键并保持空态，如隐私清空后键残留）→ 赋值 sessionId。
     * 竞态护栏：赋值前后都复核用户是否已手动切换/新建（见 sessionChosenByUser），
     * 不覆盖较新选择。全程 runCatching 静默。
     */
    private suspend fun restoreLastSessionId() {
        runCatching {
            val saved = dataStore.getCurrentSessionId() ?: return@runCatching
            if (sessionId.value != null || sessionChosenByUser) return@runCatching
            // 校验会话仍存在（Room 现有查询）；不存在说明键已残留，清键回到空态
            //（若期间用户已手动选择，其写穿已更新键，跳过清理不覆盖）
            if (conversationRepository.getSession(saved) == null) {
                if (!sessionChosenByUser) dataStore.setCurrentSessionId(null)
                Log.i("RealChatRepository", "stale session id $saved cleared, keep empty state")
                return@runCatching
            }
            // DB 校验期间用户可能已做出选择，二次复核后再赋值
            if (sessionId.value != null || sessionChosenByUser) return@runCatching
            sessionId.value = saved
            Log.i("RealChatRepository", "restored last session $saved")
        }.onFailure { Log.w("RealChatRepository", "restore last session failed", it) }
    }

    /**
     * v1.9.4 写穿：把当前会话 id 异步持久化到 DataStore（null = 清键）。
     * runCatching 静默，绝不影响主流程；写前复核 in-memory 当前值未被再次更新，
     * 防快速连续切换/删除时后到的写覆盖较新选择（乱序写收敛为最新值）。
     */
    private fun persistSessionIdAsync(id: Long?) {
        appScope.launch {
            runCatching {
                if (sessionId.value == id) {
                    dataStore.setCurrentSessionId(id)
                }
            }.onFailure { Log.w("RealChatRepository", "persist session id failed", it) }
        }
    }

    override val messages: Flow<List<ChatMessageUi>> =
        sessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else conversationRepository.observeMessages(id).map { list ->
                list.map { UiMappers.toChatMessage(it) }
            }
        }

    override val currentSessionId: Flow<Long?> = sessionId

    override val sessions: Flow<List<SessionSummaryUi>> =
        combine(
            conversationRepository.observeAllSessions(),
            conversationRepository.observeFirstUserMessages(),
            // v1.7.2 第三路：全档案 → 会话归属档案名（抽屉 Tag）
            profileRepository.observeTargets(),
        ) { sessions, firstMessages, targets ->
            val firstBySession = firstMessages.associateBy { it.sessionId }
            val targetNameById = targets.associate { it.id to it.codeName }
            sessions.mapNotNull { s ->
                val first = firstBySession[s.id]
                // v1.2.1 三级回退：DB 标题（主模型拟定）> 首条 USER 前 30 字 > "新会话"
                val title = SessionTitle.resolveSessionTitle(s.title, first?.firstUserText)
                SessionSummaryUi(
                    id = s.id,
                    title = title,
                    createdAt = s.createdAt,
                    targetName = s.targetId?.let { targetNameById[it] },
                    targetId = s.targetId,
                )
            }
        }

    override val currentModelName: Flow<String> =
        combine(currentModelId, providerRepository.observeAllModels()) { id, models ->
            models.firstOrNull { it.id == id }?.name ?: "未配置"
        }

    // F45 精简：删除 sendText/analyzeImages/confirmTranscription 三个非异步流式薄包装——
    // v1.3.1 async 族引入后全仓库零调用（消费方统一走 *Async + streamingState 中枢），
    // 契约与实现一并移除；私有 *Flow 实现保留（被 async 入口复用）

    /**
     * v1.3.1 persistUser=false 供失败重试：用户消息首次已落库，重试不重复落库、不重复更新状态机。
     * v1.9.4 [owner] = 本流的归属句柄（async 入口传入；直接 collect 的同步入口传 null，不参与
     * 流式状态归属）：新会话首次落库后由它把状态归属与任务注册 key 一起迁移（见 [StreamStateHost.Handle.retag]）。
     */
    private fun sendTextFlow(
        text: String,
        mode: AnalysisMode,
        persistUser: Boolean,
        owner: StreamStateHost.Handle?,
    ): Flow<StreamEvent> = flow {
        // AC-13：危机关键词本地预检，命中即转介，不调 LLM
        val crisis = CrisisDetector.detect(text)
        if (crisis.isNotEmpty()) {
            emit(StreamEvent.Analysis(UiMappers.toCoachCard(parseSafety(crisis.first()))))
            emit(StreamEvent.Done)
            return@flow
        }

        val sid = ensureSession()
        owner?.retag(sid)   // H5/M18：新建会话首次落库后把状态归属与任务注册 key 一起迁到真实 sid
        if (persistUser) {
            conversationRepository.addMessage(sid, "USER", "text", text)
        }

        // v1.6 全部输入统一四段结构 JSON（无 freetext 分支）

        // v1.6 对话状态机全模式常开：同题判定与状态前缀对所有输入生效
        val previousState = ConversationState.fromJson(conversationRepository.getSessionState(sid))
        // 新话题信号：本地判定与进行中话题不延续（如粘贴完整聊天记录换题）
        val wasNewTopic = previousState.hasActiveTopic &&
            !stateTracker.isSameTopic(previousState, text)
        // v1.3.1 重试（persistUser=false）不重复推进状态机，state 保持当前值，prompt 注入一致
        val state = if (persistUser) {
            stateTracker.onUserInput(previousState, text).also {
                conversationRepository.updateSessionState(sid, it.toJson())
            }
        } else {
            previousState
        }
        val statePrefix = stateTracker.buildStatePrefix(state)

        val (knowledge, refDocs) = knowledgeEngine.buildInjection(text)
        val profile = profileRepository.getProfile()
        // v1.7.2 会话归属档案优先（老会话 targetId=null → 空档案 = 现状行为）
        // v1.7.3 注入链路改读事实表：resolveTargetWithMemory 内部惰性搬移 note→facts 后
        //   target.copy(note = facts.joinToString("；").take(2000))，PromptBuilder 零改动。
        val target = resolveTargetWithMemory(sid)
        val system = promptBuilder.buildSystem(profile, target, knowledge)
        val (history, historyTruncated) = buildHistory(sid, text)
        val user = when (mode) {
            AnalysisMode.FIVE_STEP -> promptBuilder.buildUserText(text)
            // REPLY/RELAYED/GREETING 共用简短输入模板（§3.3）：
            // 模型在 prompt 内做最终语境判断，拿不准时反问兜底。
            // messages 层已带全量历史，这里再把最近几轮拼成简短上下文，
            // 帮助模型在单条 user 消息里也能抓住对话走向。
            AnalysisMode.REPLY, AnalysisMode.RELAYED, AnalysisMode.GREETING -> {
                val recentContext = history.takeLast(6)
                    .joinToString("\n") { h -> (if (h.role == "user") "用户" else "军师") + "：" + h.content.take(200) }
                    .takeIf { it.isNotBlank() }
                promptBuilder.buildUserReply(text, recentContext, statePrefix)
            }
        }

        val client = resolveClient() ?: run {
            emit(StreamEvent.Error(noConfigError()))
            return@flow
        }

        client.client.stream(
            ChatRequest(client.model, system, user, history = history),
        ).collect { event ->
            when (event) {
                is LlmEvent.Delta -> emit(StreamEvent.Delta(event.text))
                is LlmEvent.Thinking -> emit(StreamEvent.Thinking(event.text))
                is LlmEvent.Restart -> emit(StreamEvent.Restart)
                is LlmEvent.Done -> {
                    // v1.6 统一结构化落库：解析失败兜底（H3）落库 freetext 展示原文，流结束置 Done
                    val analysis = runCatching { AnalysisParser.parseAny(event.fullText) }.getOrNull()
                    if (analysis != null) {
                        conversationRepository.addMessage(sid, "ASSISTANT", "analysis", event.fullText)
                        // O5: 主回复顺带产出标题则直接落库，否则走独立标题生成降级
                        if (analysis.sessionTitle.isNotBlank()) {
                            if (conversationRepository.getSession(sid)?.title?.isNotBlank() != true) {
                                conversationRepository.updateSessionTitle(sid, SessionTitle.sanitizeTitle(analysis.sessionTitle))
                            }
                        } else {
                            titleScope.launch { generateTitleOnce(sid, text, event.fullText, isStructured = true) }
                        }
                        // v1.7.2：新话题自动提炼记忆（仅 persistUser=true 首轮；开关关/无档案/同题追问跳过）
                        // v1.9.1：素材来源按输入通道——FIVE_STEP=粘贴聊天记录，其余=口述输入
                        if (persistUser && shouldExtractMemory(sid, state, text)) {
                            val source = if (mode == AnalysisMode.FIVE_STEP) {
                                MemoryFactEntity.SOURCE_PASTE
                            } else {
                                MemoryFactEntity.SOURCE_CHAT
                            }
                            // O5: 主回复顺带产出新事实则直接落库，否则走独立记忆提炼降级
                            if (analysis.newFacts.isNotEmpty()) {
                                val facts = analysis.newFacts.map {
                                    MemoryExtractor.ExtractedFact(it.text, it.kind, it.expiresIn)
                                }
                                memoryScope.launch { persistFactsFromReply(sid, facts, source, text) }
                            } else {
                                memoryScope.launch { extractMemoryOnce(sid, text, event.fullText, source) }
                            }
                        }
                        // v1.6 状态回填走卡片字段：结论摘要=advice.core（空则 empathy 首句），话术=reply
                        val newState = stateTracker.onModelReply(
                            state = state,
                            topicSummary = if (wasNewTopic || !state.hasActiveTopic) {
                                summarizeTopic(text, analysis)
                            } else {
                                state.topicSummary
                            },
                            conclusion = summarizeConclusion(analysis),
                            reply = analysis.reply,
                        )
                        conversationRepository.updateSessionState(sid, newState.toJson())
                        val card = UiMappers.toCoachCard(analysis)
                        emit(StreamEvent.Analysis(card.copy(citations = refDocs.ifEmpty { card.citations })))
                        if (historyTruncated) _noticeEvents.tryEmit(HISTORY_TRUNCATED_NOTICE)   // M22
                    } else {
                        // H3: 解析失败不丢内容——原始回复以 freetext 落库展示，并提示
                        conversationRepository.addMessage(sid, "ASSISTANT", "freetext", event.fullText)
                        _noticeEvents.tryEmit(PARSE_FALLBACK_NOTICE)   // M22
                    }
                    emit(StreamEvent.Done)
                }
                is LlmEvent.Failed -> emit(StreamEvent.Error(UiMappers.toLlmError(event.error)))
            }
        }
    }

    /**
     * v1.3.1 persistUser=false 供图片失败重试：image/text 首次已落库，重试不重复落库。
     * v1.9.4 [owner] 同 [sendTextFlow]：async 入口传入归属句柄，同步入口传 null。
     */
    private fun analyzeImagesFlow(
        uris: List<Uri>,
        text: String,
        mode: AnalysisMode,
        persistUser: Boolean,
        owner: StreamStateHost.Handle?,
    ): Flow<StreamEvent> = flow {
        // v1.3.1 图文同发：配文先过危机预检（命中即转介，不落库、不发 LLM）
        val caption = text.trim()
        if (caption.isNotEmpty()) {
            val crisis = CrisisDetector.detect(caption)
            if (crisis.isNotEmpty()) {
                emit(StreamEvent.Analysis(UiMappers.toCoachCard(parseSafety(crisis.first()))))
                emit(StreamEvent.Done)
                return@flow
            }
        }

        // H4 修复：发送开始即锁定会话——压缩最多 10 张图是秒级窗口，原实现压缩之后才
        // ensureSession() 读共享 MutableStateFlow<Long?>，期间切会话/新建会话 →
        // 用户消息、AI 回复、记忆提炼全部落错会话。现在 sid 先快照，后续全部用参数传递。
        val sid = ensureSession()
        owner?.retag(sid)   // H5/M18
        // v1.6.1 多图：全部压缩成功才进入落库（任一失败 → 整体报错，未写任何消息，ViewModel 恢复整批待发送）
        val dataUrls = mutableListOf<String>()
        for (uri in uris) {
            // F18 修复：读取前置大小预检——原实现先把整个文件 readBytes() 读进内存，
            // ImageCompressor 才做 20MB 检查；超过堆上限的超大图在守卫生效前就可能 OOM，
            // 20MB 上限实际只防「解码」不防「读取」。openAssetFileDescriptor 拿不到长度
            // （-1 = 未知/流式提供方）时回退原全量读取路径
            val declaredBytes = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull()
            if (declaredBytes != null && declaredBytes >= 0 && ImageSpec.isTooLarge(declaredBytes)) {
                emit(StreamEvent.Error(LlmError("TOO_LARGE", ImageSpec.IMAGE_TOO_LARGE_MESSAGE, false)))
                return@flow
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: run {
                    emit(StreamEvent.Error(LlmError("READ_FAILED", "图片读取失败，请重试", false)))
                    return@flow
                }
            val dataUrl = try {
                imageCompressor.compressToDataUrl(bytes)
            } catch (e: ImageCompressor.ImageTooLargeException) {
                emit(StreamEvent.Error(LlmError("TOO_LARGE", e.message ?: "图片过大", false)))
                return@flow
            } catch (e: Exception) {
                emit(StreamEvent.Error(LlmError("COMPRESS_FAILED", "图片处理失败，请重试", false)))
                return@flow
            }
            dataUrls.add(dataUrl)
        }

        // v1.3.1 图文同发：先图后文落库（Room Flow 顺序刷新，UI 显示相邻多条用户气泡）；重试跳过
        if (persistUser) {
            dataUrls.forEach { conversationRepository.addMessage(sid, "USER", "image", it) }
            if (caption.isNotEmpty()) {
                conversationRepository.addMessage(sid, "USER", "text", caption)
            }
        }

        // 主模型是否支持视觉 → 通道 A 直读
        val mainModel = resolveModel()
        if (mainModel?.supportsVision == true) {
            runVisionDirect(sid, dataUrls, caption, mode).collect { emit(it) }
        } else {
            // 通道 B：先调视觉模型转述（配文已作为独立消息在历史里，确认转述后模型可见）
            // v1.9.2 等待文案三档：转述期间 UI 显示「视觉模型正在提取截图文字…」
            owner?.markTranscribing(true)
            val vision = resolveVisionClient()
            if (vision == null) {
                emit(StreamEvent.Error(LlmError("NO_VISION", "未配置视觉模型，请在设置中选择", false)))
                return@flow
            }
            val transcription = StringBuilder()
            vision.client.stream(
                ChatRequest(
                    model = vision.model,
                    system = "你是截图文字提取器。只输出截图中的文字，尽量保留说话人、顺序与间隔，不添加任何解释。",
                    userText = "请提取这几张聊天截图中的全部文字，按截图顺序输出。",
                    imageDataUrls = dataUrls,
                )
            ).collect { event ->
                when (event) {
                    is LlmEvent.Delta -> transcription.append(event.text)
                    is LlmEvent.Thinking -> emit(StreamEvent.Thinking(event.text))
                    is LlmEvent.Restart -> {
                        transcription.clear()
                        emit(StreamEvent.Restart)
                    }
                    is LlmEvent.Failed -> emit(StreamEvent.Error(UiMappers.toLlmError(event.error)))
                    is LlmEvent.Done -> {
                        if (transcription.isBlank()) {
                            emit(StreamEvent.Error(LlmError("EMPTY", "模型未提取到文字，请重试或重新选图", true)))
                        } else {
                            emit(StreamEvent.Transcription(transcription.toString()))
                        }
                    }
                }
            }
        }
    }

    /** v1.9.4：实现体带 [StreamStateHost.Handle]（async 入口经 launchStream 传入；同步入口传 null） */
    private fun confirmTranscriptionFlow(
        transcription: String,
        sid: Long?,
        owner: StreamStateHost.Handle?,
    ): Flow<StreamEvent> = flow {
        // M5 修复（双端）：转述通道第二步同样执行危机预检——原实现仅 sendMessage/analyzeImages
        // 入口有 CrisisDetector 硬短路，截图里的危机表述（遗书/割腕等）经视觉模型转述后
        // 直送主模型分析。落库前同样检测并走安全卡片（不落库、不调 LLM）。
        val crisisHits = CrisisDetector.detect(transcription)
        if (crisisHits.isNotEmpty()) {
            emit(StreamEvent.Analysis(UiMappers.toCoachCard(parseSafety(crisisHits.first()))))
            emit(StreamEvent.Done)
            return@flow
        }
        // H5 修复：优先用转述卡来源会话（跨会话确认不再落错会话）；null 回退当前会话（旧语义）
        @Suppress("NAME_SHADOWING") val sid = sid ?: ensureSession()
        owner?.retag(sid)   // H5/M18
        conversationRepository.addMessage(sid, "USER", "transcription", transcription)

        val (knowledge, _) = knowledgeEngine.buildInjection(transcription)
        val profile = profileRepository.getProfile()
        // v1.7.2 会话归属档案优先；v1.7.3 注入改读事实表
        val target = resolveTargetWithMemory(sid)
        val system = promptBuilder.buildSystem(profile, target, knowledge)
        val user = promptBuilder.buildUserTranscription(transcription)
        val (history, historyTruncated) = buildHistory(sid, transcription)

        val client = resolveClient() ?: run {
            emit(StreamEvent.Error(noConfigError()))
            return@flow
        }
        client.client.stream(ChatRequest(client.model, system, user, history = history)).collect { event ->
            when (event) {
                is LlmEvent.Delta -> emit(StreamEvent.Delta(event.text))
                is LlmEvent.Thinking -> emit(StreamEvent.Thinking(event.text))
                is LlmEvent.Restart -> emit(StreamEvent.Restart)
                is LlmEvent.Done -> {
                    val analysis = runCatching { AnalysisParser.parseAny(event.fullText) }.getOrNull()
                    if (analysis != null) {
                        conversationRepository.addMessage(sid, "ASSISTANT", "analysis", event.fullText)
                        // v1.7.2 转述确认同样自动提炼（素材取 transcription）；v1.9.1 来源=截图转述
                        val stateNow = ConversationState.fromJson(conversationRepository.getSessionState(sid))
                        if (shouldExtractMemory(sid, stateNow, transcription)) {
                            memoryScope.launch { extractMemoryOnce(sid, transcription, event.fullText, MemoryFactEntity.SOURCE_TRANSCRIPTION) }
                        }
                        emit(StreamEvent.Analysis(UiMappers.toCoachCard(analysis)))
                        if (historyTruncated) _noticeEvents.tryEmit(HISTORY_TRUNCATED_NOTICE)   // M22
                    } else {
                        // H3: 解析失败兜底——原始回复以 freetext 落库展示
                        conversationRepository.addMessage(sid, "ASSISTANT", "freetext", event.fullText)
                        _noticeEvents.tryEmit(PARSE_FALLBACK_NOTICE)   // M22
                    }
                    emit(StreamEvent.Done)
                }
                is LlmEvent.Failed -> emit(StreamEvent.Error(UiMappers.toLlmError(event.error)))
            }
        }
    }

    override suspend fun deleteMessage(messageId: Long) =
        conversationRepository.deleteMessage(messageId)

    /** O3: 全文检索（命中消息 → 去重 sessionId 列表） */
    override suspend fun searchSessions(query: String): List<Long> =
        conversationRepository.searchMessages(query).map { it.sessionId }.distinct()

    override suspend fun switchSession(sessionId: Long) {
        this.sessionId.value = sessionId
        // v1.9.4 用户主动选择会话：标记竞态 + 写穿持久化（冷启动可恢复）
        sessionChosenByUser = true
        persistSessionIdAsync(sessionId)
    }

    /**
     * F10 修复：清库/备份恢复后的会话态复位（供 RealSettingsRepository 联动调用，
     * 不上 ChatRepository 契约）。wipeAll/importBackup 清空重建 session 表后，内存
     * sessionId 若仍指向已删除行，下一条消息 ensureSession 会直接复用悬空 id →
     * addMessage 违反 message.sessionId 外键（PRAGMA foreign_keys=ON）静默失败，
     * 用户消息丢失且 UI 无任何提示。这里与 startNewSession 同语义回空态：
     * 置空内存 id + 写穿清持久化键，下次发送时 ensureSession 新建会话；
     * 同步复位冷启动恢复竞态标记。persistSessionIdAsync 自带「写前复核当前值」
     * 竞态护栏，期间用户已切新会话则该写自动放弃。
     */
    fun invalidateSessionState() {
        sessionChosenByUser = false
        sessionId.value = null
        persistSessionIdAsync(null)
    }

    override suspend fun startNewSession() {
        this.sessionId.value = null
        // v1.9.4 新建空会话也是较新选择：标记竞态并清键，冷启动回到空态与杀进程时一致
        //（首条消息发出后 ensureSession 会写入新 id）
        sessionChosenByUser = true
        persistSessionIdAsync(null)
    }

    override suspend fun deleteSession(sessionId: Long) {
        // v1.9.4 删除也是用户主动选择：先置竞态标记，防止冷启动恢复流程在 DB 校验窗口内
        // 把刚被删除的会话 id 赋回内存（FK 约束下会导致该条消息静默丢失）
        sessionChosenByUser = true
        // M18：删除会话时取消其进行中的流式任务（回复不再静默落已删除会话）
        // v1.9.4：同时复位归属该会话的流式状态——取消是异步生效的（协程真正结束才走
        // invokeOnCompletion 兜底），不同步复位会留下僵尸 {streaming=true}：
        // 被删会话不再有任何事件来复位它，用户停在「停止生成/思考中」的假态上。
        streamHost.cancelFor(sessionId)
        conversationRepository.deleteSession(sessionId)
        if (this.sessionId.value == sessionId) {
            this.sessionId.value = null
            // v1.9.4 删除的是当前会话 → 清持久化键，冷启动不再恢复已删除会话
            persistSessionIdAsync(null)
        }
    }

    override fun cancel() {
        // M18 修复：只取消当前查看会话的流（其他会话的后台续跑不受影响）——
        // 原实现取消的是唯一 streamJob，stop 掉的可能是旧会话的流。
        // M15 修复：状态机完整复位——原仅 streaming=false，残留 transcribing 与 error：
        // 转述中断后状态机残留；错误卡「取消」按钮点击无效（错误码不清、再 cancel 是 no-op）。
        // v1.9.4：key 解析（状态归属优先，与任务注册 key 同源）与复位收进 StreamStateHost.cancel
        // ——原实现按 sessionId.value 取 job，新会话首条消息的 job 注册在 PENDING key 下，
        // 点停止只复位 UI、LLM 协程照跑。
        streamHost.cancel(sessionId.value)
    }

    // ===== v1.3.1 后台续跑 async 发送族 =====

    override fun sendTextAsync(text: String, mode: AnalysisMode, persistUser: Boolean) =
        launchStream { owner -> sendTextFlow(text, mode, persistUser, owner) }

    override fun analyzeImagesAsync(
        uris: List<Uri>,
        text: String,
        mode: AnalysisMode,
        persistUser: Boolean,
    ) = launchStream { owner -> analyzeImagesFlow(uris, text, mode, persistUser, owner) }

    override fun confirmTranscriptionAsync(transcription: String, sid: Long?) =
        launchStream { owner -> confirmTranscriptionFlow(transcription, sid, owner) }

    /**
     * M18/H5 统一异步流入口：任务按「当前会话 key」注册，同会话已有流在跑则忽略（原全局
     * _streaming 守卫把切会后的新会话发送也锁死）；不同会话可并行后台跑。
     * 流式状态初始化即带归属 sessionId；事件应用时校验归属，旧会话流的迟到事件不再污染新会话。
     * 流内部拿到 [StreamStateHost.Handle]：新会话首次落库后用它把状态归属与任务注册 key 一起迁移。
     */
    private fun launchStream(flowFactory: (StreamStateHost.Handle) -> Flow<StreamEvent>) {
        streamHost.launch(sessionId.value, flowFactory)
    }

    // ===== 私有辅助 =====

    /**
     * 构建同会话全量历史消息（注入 LLM 请求的 messages 层）。
     *
     * - text → 原文；image → 占位文本；transcription → 带前缀全文；
     *   freetext（assistant 自由文本）→ 原文截断；
     *   analysis（assistant 结构化卡片 JSON）→ 提取 reply 字段作为 assistant 历史，解析失败跳过
     * - 剔除末尾与当前消息重复的 USER 条目（当前消息已由 userText 单独传入）
     * - 超长兜底（v1.9.1 预算选择式，替代纯丢弃）：粗估 (system+user+history)/4 > [HISTORY_TOKEN_LIMIT]
     *   时按「优先保留尾部（工作集）→ 早期消息逐条裁剪保头」处理：
     *   先对早期消息做内容裁剪（每条最多保留 [EARLY_MSG_CHAR_BUDGET] 字 + 截断标记），
     *   仍超预算再从最早整条丢弃，并在头部插入仅模型可见的省略提示。
     *   相比旧版整轮丢弃，被裁消息的关键信息（开头）仍保留在上下文里。
     */
    /** O9: 返回 (历史消息, 是否发生压缩截断) */
    private suspend fun buildHistory(sid: Long, currentUserContent: String): Pair<List<ChatHistoryMessage>, Boolean> {
        val entities = conversationRepository.listMessages(sid)
        val mapped = entities.mapNotNull { e ->
            when (e.type) {
                "text" -> ChatHistoryMessage(e.role.lowercase(), e.content)
                "image" -> ChatHistoryMessage(e.role.lowercase(), IMAGE_PLACEHOLDER)
                "transcription" -> ChatHistoryMessage(e.role.lowercase(), "[截图转述] ${e.content}")
                "freetext" -> ChatHistoryMessage(e.role.lowercase(), e.content.take(600))
                "analysis" -> runCatching { AnalysisParser.parse(e.content).reply }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ChatHistoryMessage("assistant", it) }
                else -> null
            }
        }

        // 去掉末尾与当前消息重复的 USER 条目（发送前已写库）。
        // v1.3.1 图文同发：image 占位 + text 配文两条都要去，故循环 drop 连续末尾 USER。
        val trimmed = mapped.toMutableList()
        while (trimmed.isNotEmpty() && trimmed.last().role == "user" &&
            (trimmed.last().content == currentUserContent
                || trimmed.last().content == IMAGE_PLACEHOLDER
                || trimmed.last().content == "[截图转述] $currentUserContent")
        ) {
            trimmed.removeAt(trimmed.lastIndex)
        }

        // v1.9.1 超长兜底：预算选择式压缩（共享 HistoryCompactor：先裁剪早期消息保头，仍超再从最早成对丢弃）
        val (compacted, truncated) = HistoryCompactor.compact(trimmed)
        val result = compacted.toMutableList()
        if (truncated) {
            Log.w("RealChatRepository", "history truncated to ${result.size} messages (~${HistoryCompactor.estimatedTokens(result)} tokens)")
            result.add(0, ChatHistoryMessage("user", "[注：更早的对话已因长度限制省略，关键信息已保留摘要]"))
        }
        return result to truncated
    }

    private suspend fun ensureSession(): Long {
        sessionId.value?.let { return it }
        val id = conversationRepository.createSession(
            scenarioTag = null,
            refDocs = emptyList(),
            // v1.7.2：新会话绑定当前激活档案（切档案只影响新会话）
            targetId = dataStore.getActiveTargetId(),
        )
        sessionId.value = id
        // v1.9.4 新会话拿到 id 后写穿持久化（冷启动可恢复），并视为用户较新选择
        sessionChosenByUser = true
        persistSessionIdAsync(id)
        return id
    }

    /**
     * v1.7.2 解析会话归属档案：会话 targetId → 档案（含 note 记忆正文）；
     * 老会话 targetId=null → 返回 null（空档案 = 现状行为，不报错）。
     */
    private suspend fun resolveTarget(sid: Long): TargetEntity? =
        conversationRepository.getSession(sid)?.targetId?.let { profileRepository.getTarget(it) }

    /**
     * v1.7.3 注入用档案解析：在 resolveTarget 基础上把记忆注入文本换成事实表内容。
     * memoryText 内部惰性搬移（note→facts，幂等）后 facts.joinToString("；").take(2000)；
     * 以 target.copy(note = memoryText) 传给 PromptBuilder（PromptBuilder 零改动）。
     * 失败静默回退原档案（note 可能为空 = 现状行为）。
     */
    private suspend fun resolveTargetWithMemory(sid: Long): TargetEntity? {
        val target = resolveTarget(sid) ?: return null
        val memory = runCatching { profileRepository.memoryText(target.id) }.getOrDefault(target.note)
        return if (memory == target.note) target else target.copy(note = memory)
    }

    private suspend fun resolveClient(): ResolvedClient? {
        val model = resolveModel() ?: return null
        val provider = providerRepository.getProvider(model.providerId) ?: return null
        val apiKey = providerRepository.decryptApiKey(provider.id) ?: return null
        // F11：复用容器共享 OkHttpClient（原每次调用经默认参数新建，连接池零复用）
        return ResolvedClient(model.name, LlmClient(provider.baseUrl, apiKey, client = httpClient))
    }

    private suspend fun resolveModel() = currentModelId.first()?.let { providerRepository.getModel(it) }

    private suspend fun resolveVisionClient(): ResolvedClient? {
        val id = visionModelId.first() ?: return null
        val model = providerRepository.getModel(id) ?: return null
        val provider = providerRepository.getProvider(model.providerId) ?: return null
        val apiKey = providerRepository.decryptApiKey(provider.id) ?: return null
        return ResolvedClient(model.name, LlmClient(provider.baseUrl, apiKey, client = httpClient))
    }

    /**
     * v1.3.1 通道 A（主模型直读图片）：支持图文同发。
     * - 纯图：保持原提示（四段结构 JSON 卡片）；
     * - 图文同发：mode=FIVE_STEP → buildUserText（聊天记录模板），其余 → buildUserReply（简短输入模板）；
     * - v1.6 全部统一 STRUCTURED，完成解析后落库直渲；
     * - v1.6.1 多图：dataUrls 一次请求全量直读（content 数组多 image_url）。
     */
    private suspend fun runVisionDirect(
        sid: Long,
        dataUrls: List<String>,
        text: String,
        mode: AnalysisMode,
    ): Flow<StreamEvent> = flow {
        val profile = profileRepository.getProfile()
        // v1.7.2 会话归属档案优先；v1.7.3 注入改读事实表
        val target = resolveTargetWithMemory(sid)
        val system = promptBuilder.buildSystem(profile, target, "")
        val client = resolveClient() ?: run {
            emit(StreamEvent.Error(noConfigError()))
            return@flow
        }
        val (history, historyTruncated) = buildHistory(sid, text.ifBlank { IMAGE_PLACEHOLDER })
        val userText = when {
            text.isBlank() -> "以下是用户聊天截图，请按四段结构分析。"
            mode == AnalysisMode.FIVE_STEP -> promptBuilder.buildUserText(text)
            else -> {
                val recentContext = history.takeLast(6)
                    .joinToString("\n") { h -> (if (h.role == "user") "用户" else "军师") + "：" + h.content.take(200) }
                    .takeIf { it.isNotBlank() }
                promptBuilder.buildUserReply(text, recentContext)
            }
        }
        client.client.stream(
            ChatRequest(
                model = client.model,
                system = system,
                userText = userText,
                imageDataUrls = dataUrls,
                history = history,
            )
        ).collect { event ->
            when (event) {
                is LlmEvent.Delta -> emit(StreamEvent.Delta(event.text))
                is LlmEvent.Thinking -> emit(StreamEvent.Thinking(event.text))
                is LlmEvent.Restart -> emit(StreamEvent.Restart)
                is LlmEvent.Done -> {
                    val analysis = runCatching { AnalysisParser.parseAny(event.fullText) }.getOrNull()
                    if (analysis != null) {
                        conversationRepository.addMessage(sid, "ASSISTANT", "analysis", event.fullText)
                        // v1.7.2 通道 A 直读同样自动提炼（素材取截图）；v1.9.1 来源=截图转述
                        val stateNow = ConversationState.fromJson(conversationRepository.getSessionState(sid))
                        if (shouldExtractMemory(sid, stateNow, text)) {
                            memoryScope.launch { extractMemoryOnce(sid, text, event.fullText, MemoryFactEntity.SOURCE_TRANSCRIPTION) }
                        }
                        emit(StreamEvent.Analysis(UiMappers.toCoachCard(analysis)))
                        if (historyTruncated) _noticeEvents.tryEmit(HISTORY_TRUNCATED_NOTICE)   // M22
                    } else {
                        // H3: 解析失败兜底——原始回复以 freetext 落库展示
                        conversationRepository.addMessage(sid, "ASSISTANT", "freetext", event.fullText)
                        _noticeEvents.tryEmit(PARSE_FALLBACK_NOTICE)   // M22
                    }
                    emit(StreamEvent.Done)
                }
                is LlmEvent.Failed -> emit(StreamEvent.Error(UiMappers.toLlmError(event.error)))
            }
        }
    }

    private fun parseSafety(hit: String) = AnalysisParser.parseAny(
        // L1 修复：hit 用 JSONObject 构造并转义——原 $hit 直接插值进 JSON 字符串模板，
        // 词表一旦含引号即产生非法 JSON/注入点（脆弱实现，双端各一份副本）。
        org.json.JSONObject()
            .put("input_kind", "user_question")
            .put("empathy", "")
            .put("reply", "")
            .put("reply_timing", "")
            .put(
                "facts", org.json.JSONObject()
                    .put("known", org.json.JSONArray())
                    .put("assumed", org.json.JSONArray())
                    .put("unknown", org.json.JSONArray())
            )
            .put(
                "advice", org.json.JSONObject()
                    .put("tag", "")
                    .put("core", "")
                    .put("reasons", org.json.JSONArray())
                    .put("styles", org.json.JSONArray())
            )
            .put("actions", org.json.JSONArray())
            .put("citations", org.json.JSONArray())
            .put("safety_override", true)
            .put(
                "safety_message",
                "检测到可能涉及安全风险的表述（${hit}）。请优先确保自己的人身安全：" +
                    "离开危险环境，联系可信的人或当地紧急服务。我们无法在危机中提供恋爱建议。",
            )
            .put("token_estimate", 0)
            .toString()
    )

    private fun noConfigError(): LlmError =
        LlmError("NO_CONFIG", "请先在设置中配置 API Key 与主模型", false)

    // ===== v1.2.1 会话标题生成辅助 =====

    /**
     * 首轮回复完成后由主模型拟定会话标题（幂等，失败静默）。
     *
     * - 幂等：DB 已有非空 title 直接跳过（防第二/后续轮重复生成）
     * - 素材：首句用户输入 + 首条回复；STRUCTURED 时 fullText 是 JSON，取 reply 字段
     * - 调用：复用流式接口收集首个 Done（标题短，SSE 开销可忽略），20s 超时兜底
     * - 失败：静默留空 → 抽屉走 resolveSessionTitle 首句回退；下轮 Done 天然重试
     */
    private suspend fun generateTitleOnce(
        sid: Long,
        userText: String,
        replyFullText: String,
        isStructured: Boolean,
    ) {
        if (conversationRepository.getSession(sid)?.title?.isNotBlank() == true) return
        val replyMaterial = if (isStructured) {
            runCatching { AnalysisParser.parse(replyFullText).reply }.getOrDefault("")
        } else {
            replyFullText
        }
        val (userLine, replyLine) = ChatOrchestrator.buildTitleMaterial(userText, replyMaterial)
        if (userLine.isBlank()) return

        val title = runCatching {
            withTimeout(TITLE_TIMEOUT_MS) {
                val client = resolveClient() ?: return@withTimeout null
                client.client.stream(
                    ChatRequest(
                        model = client.model,
                        system = TITLE_SYSTEM_PROMPT,
                        userText = ChatOrchestrator.buildTitlePrompt(userLine, replyLine),
                    ),
                ).filterIsInstance<LlmEvent.Done>().firstOrNull()?.fullText
            }
        }.getOrNull()?.let { SessionTitle.sanitizeTitle(it) }?.takeIf { it.isNotBlank() }

        if (title != null) {
            conversationRepository.updateSessionTitle(sid, title)
            Log.i("RealChatRepository", "session $sid title generated: $title")
        } else {
            Log.w("RealChatRepository", "session $sid title generation failed/skipped, fallback to first message")
        }
    }

    // ===== v1.6 对话状态摘要辅助（结构化来源） =====

    /**
     * 新话题时提炼话题摘要：取用户输入的前 24 字（足够模型对齐"在聊什么"）。
     * 模型输出仅作补充——empathy 首句（空则 advice.core）关键词追加在后面，总长控制在 40 字内。
     */
    private fun summarizeTopic(userInput: String, analysis: CoachAnalysis): String =
        ChatOrchestrator.summarizeTopic(userInput, analysis)

    /**
     * 提炼本轮结论摘要：advice.core（空则 empathy 首句，至多 40 字），作为"已给结论"记入状态。
     */
    private fun summarizeConclusion(analysis: CoachAnalysis): String =
        ChatOrchestrator.summarizeConclusion(analysis)

    // ===== v1.7.2 自动记忆提炼辅助 =====

    /**
     * 自动提炼节流判定（纯判定，不写状态）：
     * - 开关关闭 → 跳过
     * - 会话无归属档案 / 档案不存在 → 跳过（老会话 null 不提炼）
     * - 首话题或新话题（!isSameTopic）→ 提炼；同题追问 → 跳过（避免每轮都调 LLM）
     */
    private suspend fun shouldExtractMemory(sid: Long, state: ConversationState, userInput: String): Boolean {
        if (!dataStore.memoryAutoEnabled.first()) return false
        val targetId = conversationRepository.getSession(sid)?.targetId ?: return false
        val hasTarget = profileRepository.getTarget(targetId) != null
        // M2/O4: 双端统一判定收敛到 ChatOrchestrator
        return ChatOrchestrator.shouldExtractMemory(state, userInput, memoryAutoEnabled = true, hasTarget = hasTarget)
    }

    /**
     * 执行一次记忆提炼（独立 scope + 幂等 + 失败静默 + 20s 超时，仿 generateTitleOnce）：
     * 调主模型（temperature=0.3）→ parseFacts 防御解析 → mergeFacts 去重（≤50 条）→ 仅新增逐条 INSERT。
     * v1.7.4：per-target Mutex 串行化（锁等待计入 20s 超时；同档案并发触发时后者基于最新 facts 提炼，
     * 不会重复插入）。失败静默 Log.w；重复触发因 mergeFacts 去重不会重复插入。
     * v1.9.0：parseFacts 返回 {text,kind}，hypothesis 走分层写入；成功写入后记录操作日志（供撤销）+ 回执。
     * v1.9.1：source 素材来源由调用方按输入通道传入（paste/transcription/chat），
     * expiresIn 由模型标注（today/week）→ computeExpiryMillis 换算到期时间戳落库。
     */
    private suspend fun extractMemoryOnce(sid: Long, userInput: String, replyFullText: String, source: String) {
        runCatching {
            withTimeout(MEMORY_TIMEOUT_MS) {
                val targetId = conversationRepository.getSession(sid)?.targetId ?: return@withTimeout
                val target = profileRepository.getTarget(targetId) ?: return@withTimeout
                val mutex = extractMutexes.getOrPut(targetId) { Mutex() }
                mutex.withLock {
                    // v1.7.3 提炼前确保惰性搬移完成（老 note 进事实表），existing 从事实表读取
                    profileRepository.migrateNoteToFactsOnce(targetId)
                    val existing = profileRepository.getFacts(targetId).map { it.text }
                    val reply = runCatching { AnalysisParser.parse(replyFullText).reply }.getOrDefault(replyFullText)
                    val prompt = MemoryExtractor.buildPrompt(userInput, reply, existing.joinToString("；"))
                    val client = resolveClient() ?: return@withLock
                    val json = client.client.stream(
                        ChatRequest(client.model, MEMORY_SYSTEM_PROMPT, prompt),
                    ).filterIsInstance<LlmEvent.Done>().firstOrNull()?.fullText
                    persistFactsOnce(targetId, MemoryExtractor.parseFacts(json ?: ""), source, userInput)
                }
            }
        }.onFailure { Log.w("RealChatRepository", "memory extraction failed", it) }
    }

    /** O5: 主回复已顺带产出新事实，直接落库（不走独立 LLM 提炼） */
    private suspend fun persistFactsFromReply(
        sid: Long,
        facts: List<MemoryExtractor.ExtractedFact>,
        source: String,
        userInput: String,
    ) {
        runCatching {
            val targetId = conversationRepository.getSession(sid)?.targetId ?: return@runCatching
            if (profileRepository.getTarget(targetId) == null) return@runCatching
            val mutex = extractMutexes.getOrPut(targetId) { Mutex() }
            mutex.withLock {
                profileRepository.migrateNoteToFactsOnce(targetId)
                persistFactsOnce(targetId, facts, source, userInput)
            }
        }.onFailure { Log.w("RealChatRepository", "persist facts from reply failed", it) }
    }

    /** O5: 合并+插入已解析事实（不含 LLM 调用；调用方负责 per-target 互斥） */
    private suspend fun persistFactsOnce(
        targetId: Long,
        facts: List<MemoryExtractor.ExtractedFact>,
        source: String,
        userInput: String,
    ) {
        val existing = profileRepository.getFacts(targetId).map { it.text }
        val merged = MemoryExtractor.mergeFacts(existing, facts.map { it.text })
        // L2 修复：mergeFacts 内部先清洗空白条目，返回列表的「旧事实段」比原始 existing 短——
        // 按原始 size drop 会把新事实错位跳过（库里有空白事实即触发）。以清洗后数量为准。
        val toAdd = merged.drop(existing.count { it.isNotBlank() })
        if (toAdd.isEmpty()) return
        // O2: 冲突检测——新事实与既有事实矛盾时提示（记忆页裁决）
        val conflictCount = toAdd.sumOf { MemoryConflictDetector.findConflicts(it, existing).size }
        if (conflictCount > 0) {
            _noticeEvents.tryEmit("发现 $conflictCount 条与已记住事实可能矛盾，请到记忆页确认")   // M22
        }
        if (existing.size >= MemoryExtractor.DEFAULT_FACT_LIMIT) {
            Log.w("RealChatRepository", "memory facts cap reached for target $targetId")
            return
        }
        val addedIds = mutableListOf<Long>()
        toAdd.take(MemoryExtractor.DEFAULT_FACT_LIMIT - existing.size).forEach { text ->
            val fact = facts.firstOrNull { it.text == text }
            val id = profileRepository.addFact(
                targetId = targetId,
                text = text,
                kind = fact?.kind ?: MemoryExtractor.KIND_FACT,
                expiresAt = MemoryExtractor.computeExpiryMillis(fact?.expiresIn),
                source = source,
            )
            if (id > 0L) addedIds.add(id)
        }
        if (addedIds.isNotEmpty()) {
            dataStore.recordMemoryWrite(targetId, addedIds, userInput.take(20))
            _memoryReceiptEvents.tryEmit("已记住 ${addedIds.size} 条事实，可在设置中查看或撤销")   // M22
        }
        Log.i("RealChatRepository", "memory extracted: +${toAdd.size} facts for target $targetId")
    }

    private data class ResolvedClient(val model: String, val client: LlmClient)

    private companion object {
        /** v1.9.1 历史消息超长兜底统一走共享 HistoryCompactor（domain 包） */
        /** 历史中的图片消息占位文本 */
        const val IMAGE_PLACEHOLDER = "[用户发送了一张聊天截图]"

        /** v1.2.1：标题生成超时（毫秒），超时静默回退首句截断 */
        const val TITLE_TIMEOUT_MS = 20_000L
        /** v1.2.1：标题生成 system prompt，只输出标题本身 */
        const val TITLE_SYSTEM_PROMPT = "你是会话标题生成器。只输出标题本身，不要任何解释、引号、标点或表情。"

        /** v1.7.2：记忆提炼超时（毫秒），超时静默不追加 */
        const val MEMORY_TIMEOUT_MS = 20_000L
        /** v1.7.2：记忆提炼 system prompt，只输出 JSON */
        const val MEMORY_SYSTEM_PROMPT = "你是记忆提炼器。只输出 JSON，不加解释。"

        /** H3: 解析失败兜底提示（原始回复已以 freetext 展示） */
        const val PARSE_FALLBACK_NOTICE = "模型输出格式异常，已展示原文"

        /** O9: 历史压缩透明化提示 */
        const val HISTORY_TRUNCATED_NOTICE = "对话较长，较早内容已摘要化，如需精确信息请补充"
    }
}

/**
 * 流式状态主机（v1.9.4）：[ChatRepository.streamingState] 的唯一写入方。
 *
 * 职责：持有流式状态中枢 + 按会话 key 注册的流任务表，并保证**状态归属 sessionId 与任务注册
 * key 同源同步**——M18 归属校验（「状态归属 == ownerKey」才应用事件）成立的前提。
 *
 * 为什么必须抽出来单独修：新会话首条消息在 ensureSession 之前以 [PENDING_SESSION_KEY] 启动
 * （此时 sessionId.value 还是 null），拿到真实 sid 后若只把状态里的 sessionId 改成真实 sid、
 * 任务仍注册在 PENDING 下，则事件应用时 ownerKey 仍是 -1 → Delta/Analysis/Done/Error/
 * Transcription 全部被丢弃：Done 的 streaming=false 永不落地，打字气泡不消失、发送键卡在
 * 「停止生成」；cancel()/deleteSession() 也按真实 sid 取不到 job（点停止只复位 UI，LLM 协程照跑）。
 * [Handle.retag] 现在一次迁移这两处（见 RealChatRepositoryTest 回归用例）。
 *
 * 另：本类不依赖 Android（只用 kotlinx.coroutines + ui.contract），JVM 单测可直接驱动；
 * RealChatRepository 本体依赖 Context/DataStore/Android Keystore，JVM 单测构造不起来。
 */
internal class StreamStateHost(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(StreamingState())
    val state: StateFlow<StreamingState> = _state.asStateFlow()

    /**
     * M18 修复：流式任务按会话维度注册（key = 会话 id；[PENDING_SESSION_KEY] = 尚未落库的新会话）。
     * 原单一 streamJob + 全局 _streaming 守卫：切会后新会话假「思考中」、发送被锁死、
     * stop 取消的是旧会话流。现在同会话单飞、不同会话可并行后台跑。
     */
    private val jobs = ConcurrentHashMap<Long, Job>()

    /**
     * 启动一条流（同 key 已有活跃流则忽略）。
     * LAZY 启动 + 先注册再 start：保证流内部调 [Handle.retag] 时注册表已就绪，key 一定迁得动
     * （否则 retag 与注册之间存在竞态窗口，迁移会丢）。
     */
    fun launch(viewSessionId: Long?, flowFactory: (Handle) -> Flow<StreamEvent>) {
        val key = viewSessionId ?: PENDING_SESSION_KEY
        if (jobs[key]?.isActive == true) return
        _state.value = StreamingState(streaming = true, sessionId = viewSessionId)
        val handle = Handle(key)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            flowFactory(handle).collect { event -> applyEvent(event, handle.key) }
        }
        handle.job = job
        jobs[key] = job
        // v1.9.4 兜底：流结束（正常/取消/异常）都要复位——原来只摘除注册表项，
        // 事件没走到收尾时状态就永久卡在 {streaming=true}
        job.invokeOnCompletion { onFinished(handle) }
        job.start()
    }

    /** 流式事件 → 状态中枢（带归属校验：非本会话事件丢弃，防跨会话串状态） */
    fun applyEvent(event: StreamEvent, ownerKey: Long) {
        when (event) {
            is StreamEvent.Delta -> _state.update { if (owned(it, ownerKey)) it.copy(text = it.text + event.text) else it }
            is StreamEvent.Thinking -> _state.update { if (owned(it, ownerKey)) it.copy(thinking = it.thinking + event.text) else it }
            is StreamEvent.Analysis -> _state.update { if (owned(it, ownerKey)) it.copy(streaming = false, text = "", thinking = "") else it }
            is StreamEvent.Transcription -> _state.update {
                if (owned(it, ownerKey)) it.copy(streaming = false, transcription = event.text, text = "", thinking = "", transcribing = false) else it
            }
            is StreamEvent.Error -> _state.update {
                if (owned(it, ownerKey)) it.copy(streaming = false, error = event.error, transcribing = false) else it
            }
            StreamEvent.Restart -> _state.update { if (owned(it, ownerKey)) it.copy(text = "", thinking = "") else it }
            StreamEvent.Done -> _state.update { if (owned(it, ownerKey)) it.copy(streaming = false, text = "", thinking = "") else it }
        }
    }

    /**
     * stop 按钮：取消当前**显示中**的流并复位状态机（M15：transcribing/error 一并清）。
     * 状态归属与任务注册 key 由 [Handle.retag] 保持同步，故优先按状态归属取 job
     * （stop 按钮可见 ⇔ 状态归属 == 当前查看会话：ViewModel 只映射 mine 的状态）；
     * 视图会话 key 作兜底，避免状态已被别的会话流占走时点停止完全无反应。
     */
    fun cancel(viewSessionId: Long?) {
        val ownerKey = _state.value.sessionId ?: PENDING_SESSION_KEY
        val viewKey = viewSessionId ?: PENDING_SESSION_KEY
        val key = if (jobs.containsKey(ownerKey)) ownerKey else viewKey
        jobs.remove(key)?.cancel()
        _state.update {
            if ((it.sessionId ?: PENDING_SESSION_KEY) == key) {
                it.copy(streaming = false, transcribing = false, error = null)
            } else {
                it
            }
        }
    }

    /**
     * deleteSession：取消该会话的流并复位其流式状态。
     * 取消是异步生效的（协程真正结束才走 [onFinished] 兜底），这里同步复位：被删会话不会再有
     * 任何事件回来复位自己，否则留下僵尸 {streaming=true}（打字气泡不消失 / 停止键卡住）。
     */
    fun cancelFor(sessionId: Long) {
        jobs.remove(sessionId)?.cancel()
        _state.update { if (it.sessionId == sessionId) StreamingState() else it }
    }

    /**
     * 流结束兜底：摘除注册 + 若状态仍归属本流且仍在 streaming 则复位。
     * 文案保护：Done/Error/Analysis 正常到达时已自行复位（streaming=false），这里不再改动
     * 状态（尤其不碰 error），只兜「事件没走到收尾」的路径（取消 / 抛异常 / 断流未收尾）。
     * 归属校验同样保留：别的会话正在跑的流不被误复位。
     */
    private fun onFinished(handle: Handle) {
        val key = handle.key
        handle.job?.let { jobs.remove(key, it) }
        _state.update {
            if ((it.sessionId ?: PENDING_SESSION_KEY) == key && it.streaming) {
                it.copy(streaming = false, transcribing = false, text = "", thinking = "")
            } else {
                it
            }
        }
    }

    private fun owned(st: StreamingState, key: Long) = (st.sessionId ?: PENDING_SESSION_KEY) == key

    /**
     * 一条流的句柄：当前归属 key（可重打标）+ 它在注册表里的 job。
     * 每个句柄只被它自己那条协程的 retag 写过一次，无需加锁（volatile 读足够）。
     */
    inner class Handle(initialKey: Long) {
        @Volatile
        private var currentKey = initialKey

        /** 注册表中的 job（launch 内 start 之前绑定） */
        var job: Job? = null

        val key: Long get() = currentKey

        /**
         * H5/M18：新建会话首次落库后重打标——状态归属与任务注册 key **一起**迁移
         * （PENDING → 真实 sid），使事件归属校验与 cancel/deleteSession 查表共用同一个 key。
         * 只处理 PENDING → 真实 sid 这一步：已落定的流（如 H5 跨会话确认转述，其 sid 与视图会话
         * 不同）保持原归属，避免把某个会话的流改签到另一个会话。
         */
        fun retag(sid: Long) {
            val old = currentKey
            if (old != PENDING_SESSION_KEY || sid == PENDING_SESSION_KEY) return
            currentKey = sid
            val registered = job
            if (registered != null && jobs.remove(old, registered)) jobs[sid] = registered
            // 状态归属同样只在不属于其他流时才接管（原 retagStreamingOwner 语义）
            _state.update { if (it.sessionId == null) it.copy(sessionId = sid) else it }
        }

        /** 通道 B 转述等待态：仅在本流仍拥有状态时生效（不串到别的会话的流上） */
        fun markTranscribing(value: Boolean) {
            _state.update { if (owned(it, currentKey)) it.copy(transcribing = value) else it }
        }
    }

    companion object {
        /** H5/M18：尚未落库的新会话在任务表/归属比较里的哨兵 key（Room 自增 id 恒 >= 1） */
        const val PENDING_SESSION_KEY = -1L
    }
}
