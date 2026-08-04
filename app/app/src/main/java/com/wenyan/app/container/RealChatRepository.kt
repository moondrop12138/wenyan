package com.wenyan.app.container

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wenyan.app.data.datastore.SettingsRepository as DataStoreSettings
import com.wenyan.app.data.image.ImageCompressor
import com.wenyan.app.data.repository.ConversationRepository
import com.wenyan.app.data.repository.ProfileRepository
import com.wenyan.app.data.repository.ProviderRepository
import com.wenyan.app.domain.ConversationState
import com.wenyan.app.domain.ConversationStateTracker
import com.wenyan.app.knowledge.CrisisDetector
import com.wenyan.app.knowledge.KnowledgeEngine
import com.wenyan.app.llm.AnalysisParser
import com.wenyan.app.llm.ChatHistoryMessage
import com.wenyan.app.llm.ChatRequest
import com.wenyan.app.llm.LlmClient
import com.wenyan.app.llm.LlmEvent
import com.wenyan.app.llm.ResponseMode
import com.wenyan.app.prompt.PromptBuilder
import com.wenyan.app.ui.contract.AnalysisMode
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.ChatRepository
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.contract.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 对话 Repository 真实实现（AC-04/05/06/07/08/13/14/15）
 *
 * sendText 编排：危机预检 → 知识路由 → prompt 三层拼装 → LLM 流 → 持久化 → 五步法解析。
 * analyzeImage 双通道：主模型 supportsVision=true 走通道 A 直读，否则走通道 B 视觉转述。
 */
class RealChatRepository(
    private val context: Context,
    private val dataStore: DataStoreSettings,
    private val conversationRepository: ConversationRepository,
    private val profileRepository: ProfileRepository,
    private val providerRepository: ProviderRepository,
    private val knowledgeEngine: KnowledgeEngine,
    private val promptBuilder: PromptBuilder,
    private val imageCompressor: ImageCompressor,
) : ChatRepository {

    private val sessionId = MutableStateFlow<Long?>(null)

    /** v1.3 对话状态机：本地结构化跟踪，驱动同题追问不复读 */
    private val stateTracker = ConversationStateTracker()

    private val currentModelId = dataStore.currentModelId
    private val visionModelId = dataStore.visionModelId

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
        ) { sessions, firstMessages ->
            val firstBySession = firstMessages.associateBy { it.sessionId }
            sessions.mapNotNull { s ->
                val first = firstBySession[s.id]
                // 没有 USER 消息的会话（新建的）也展示，标题用占位
                val title = first?.firstUserText
                    ?.replace(Regex("\\s+"), " ")
                    ?.take(30)
                    ?.takeIf { it.isNotBlank() }
                    ?: "新会话"
                SessionSummaryUi(
                    id = s.id,
                    title = title,
                    createdAt = s.createdAt,
                )
            }
        }

    override val currentModelName: Flow<String> =
        combine(currentModelId, providerRepository.observeAllModels()) { id, models ->
            models.firstOrNull { it.id == id }?.name ?: "未配置"
        }

    override fun sendText(text: String, mode: AnalysisMode): Flow<StreamEvent> = flow {
        // AC-13：危机关键词本地预检，命中即转介，不调 LLM
        val crisis = CrisisDetector.detect(text)
        if (crisis.isNotEmpty()) {
            emit(StreamEvent.Analysis(UiMappers.toAnalysisCard(parseSafety(crisis.first()))))
            emit(StreamEvent.Done)
            return@flow
        }

        val sid = ensureSession()
        conversationRepository.addMessage(sid, "USER", "text", text)

        // v1.3 混合渲染：粘贴聊天记录走五步法 JSON 卡片；
        // 简短输入（提问/转述/打招呼）走 freetext 自由文本，直出自然中文，skill 体感。
        val responseMode = when (mode) {
            AnalysisMode.FIVE_STEP -> ResponseMode.STRUCTURED
            AnalysisMode.REPLY, AnalysisMode.RELAYED, AnalysisMode.GREETING -> ResponseMode.FREETEXT
        }

        // v1.3 对话状态机：简短输入时驱动同题判定与状态前缀注入
        val trackerEnabled = responseMode == ResponseMode.FREETEXT
        val previousState = if (trackerEnabled) {
            ConversationState.fromJson(conversationRepository.getSessionState(sid))
        } else {
            ConversationState.EMPTY
        }
        // 新话题信号：本地判定与进行中话题不延续（如粘贴完整聊天记录换题）
        val wasNewTopic = trackerEnabled && previousState.hasActiveTopic &&
            !stateTracker.isSameTopic(previousState, text)
        val state = if (trackerEnabled) {
            stateTracker.onUserInput(previousState, text).also {
                conversationRepository.updateSessionState(sid, it.toJson())
            }
        } else {
            previousState
        }
        val statePrefix = if (trackerEnabled) stateTracker.buildStatePrefix(state) else ""

        val (knowledge, refDocs) = knowledgeEngine.buildInjection(text)
        val profile = profileRepository.getProfile()
        val target = profileRepository.getTarget()
        val system = promptBuilder.buildSystem(profile, target, knowledge, responseMode)
        val history = buildHistory(sid, text)
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
                promptBuilder.buildUserReply(text, recentContext, responseMode, statePrefix)
            }
        }

        val client = resolveClient() ?: run {
            emit(StreamEvent.Error(noConfigError()))
            return@flow
        }

        client.client.stream(
            ChatRequest(client.model, system, user, history = history, responseMode = responseMode),
        ).collect { event ->
            when (event) {
                is LlmEvent.Delta -> emit(StreamEvent.Delta(event.text))
                is LlmEvent.Thinking -> emit(StreamEvent.Thinking(event.text))
                is LlmEvent.Done -> {
                    if (responseMode == ResponseMode.FREETEXT) {
                        // freetext：原文直存直渲，不走 JSON 解析
                        conversationRepository.addMessage(sid, "ASSISTANT", "freetext", event.fullText)
                        // 状态落地：记录本轮结论摘要与话术痕迹，供下轮查重
                        if (trackerEnabled) {
                            val newState = stateTracker.onModelReply(
                                state = state,
                                topicSummary = if (wasNewTopic || !state.hasActiveTopic) {
                                    summarizeTopic(text, event.fullText)
                                } else {
                                    state.topicSummary
                                },
                                conclusion = summarizeConclusion(event.fullText),
                                reply = extractReplySection(event.fullText),
                            )
                            conversationRepository.updateSessionState(sid, newState.toJson())
                        }
                        emit(StreamEvent.FreeTextDone)
                    } else {
                        val analysis = runCatching { AnalysisParser.parse(event.fullText) }.getOrNull()
                        if (analysis != null) {
                            conversationRepository.addMessage(sid, "ASSISTANT", "analysis", event.fullText)
                            val card = UiMappers.toAnalysisCard(analysis)
                            emit(StreamEvent.Analysis(card.copy(citations = refDocs.ifEmpty { card.citations })))
                        }
                        emit(StreamEvent.Done)
                    }
                }
                is LlmEvent.Failed -> emit(StreamEvent.Error(UiMappers.toLlmError(event.error)))
            }
        }
    }

    override fun analyzeImage(uri: Uri): Flow<StreamEvent> = flow {
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

        val sid = ensureSession()
        conversationRepository.addMessage(sid, "USER", "image", dataUrl)

        // 主模型是否支持视觉 → 通道 A 直读
        val mainModel = resolveModel()
        if (mainModel?.supportsVision == true) {
            runVisionDirect(sid, dataUrl).collect { emit(it) }
        } else {
            // 通道 B：先调视觉模型转述
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
                    userText = "请提取这张聊天截图中的全部文字。",
                    imageDataUrl = dataUrl,
                )
            ).collect { event ->
                when (event) {
                    is LlmEvent.Delta -> transcription.append(event.text)
                    is LlmEvent.Thinking -> emit(StreamEvent.Thinking(event.text))
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

    override fun confirmTranscription(transcription: String): Flow<StreamEvent> = flow {
        val sid = ensureSession()
        conversationRepository.addMessage(sid, "USER", "transcription", transcription)

        val (knowledge, _) = knowledgeEngine.buildInjection(transcription)
        val profile = profileRepository.getProfile()
        val target = profileRepository.getTarget()
        val system = promptBuilder.buildSystem(profile, target, knowledge)
        val user = promptBuilder.buildUserTranscription(transcription)
        val history = buildHistory(sid, transcription)

        val client = resolveClient() ?: run {
            emit(StreamEvent.Error(noConfigError()))
            return@flow
        }
        client.client.stream(ChatRequest(client.model, system, user, history = history)).collect { event ->
            when (event) {
                is LlmEvent.Delta -> emit(StreamEvent.Delta(event.text))
                is LlmEvent.Thinking -> emit(StreamEvent.Thinking(event.text))
                is LlmEvent.Done -> {
                    val analysis = runCatching { AnalysisParser.parse(event.fullText) }.getOrNull()
                    if (analysis != null) {
                        conversationRepository.addMessage(sid, "ASSISTANT", "analysis", event.fullText)
                        emit(StreamEvent.Analysis(UiMappers.toAnalysisCard(analysis)))
                    }
                    emit(StreamEvent.Done)
                }
                is LlmEvent.Failed -> emit(StreamEvent.Error(UiMappers.toLlmError(event.error)))
            }
        }
    }

    override suspend fun deleteMessage(messageId: Long) =
        conversationRepository.deleteMessage(messageId)

    override suspend fun switchSession(sessionId: Long) {
        this.sessionId.value = sessionId
    }

    override suspend fun startNewSession() {
        this.sessionId.value = null
    }

    override suspend fun deleteSession(sessionId: Long) {
        conversationRepository.deleteSession(sessionId)
        if (this.sessionId.value == sessionId) {
            this.sessionId.value = null
        }
    }

    override fun cancel() {
        // 流取消由 collect 侧 job 取消触发；MVP 由 ViewModel.stop() 处理
    }

    // ===== 私有辅助 =====

    /**
     * 构建同会话全量历史消息（注入 LLM 请求的 messages 层）。
     *
     * - text → 原文；image → 占位文本；transcription → 带前缀全文；
     *   freetext（assistant 自由文本）→ 原文截断；
     *   analysis（assistant 结构化卡片 JSON）→ 提取 reply 字段作为 assistant 历史，解析失败跳过
     * - 剔除末尾与当前消息重复的 USER 条目（当前消息已由 userText 单独传入）
     * - 超长兜底：粗估 (system+user+history)/4 > [HISTORY_TOKEN_LIMIT] 时从最早轮次成对丢弃，
     *   并在头部插入一条仅模型可见的省略提示
     */
    private suspend fun buildHistory(sid: Long, currentUserContent: String): List<ChatHistoryMessage> {
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

        // 去掉末尾与当前消息重复的 USER 条目（发送前已写库）
        val trimmed = if (mapped.isNotEmpty()
            && mapped.last().role == "user"
            && (mapped.last().content == currentUserContent
                || mapped.last().content == "[截图转述] $currentUserContent"
                || mapped.last().content == IMAGE_PLACEHOLDER)
        ) {
            mapped.dropLast(1)
        } else {
            mapped
        }

        // 超长兜底：从最早轮次成对丢弃
        val result = trimmed.toMutableList()
        var estimated = result.sumOf { it.content.length } / 4
        var truncated = false
        while (estimated > HISTORY_TOKEN_LIMIT && result.size > 2) {
            result.removeAt(0)
            // 尽量成对丢弃（user + assistant），保持轮次完整
            if (result.size > 2) result.removeAt(0)
            truncated = true
            estimated = result.sumOf { it.content.length } / 4
        }
        if (truncated) {
            Log.w("RealChatRepository", "history truncated to ${result.size} messages (~$estimated tokens)")
            result.add(0, ChatHistoryMessage("user", "[注：更早的对话已因长度限制省略]"))
        }
        return result
    }

    private suspend fun ensureSession(): Long {
        sessionId.value?.let { return it }
        val id = conversationRepository.createSession(null, emptyList())
        sessionId.value = id
        return id
    }

    private suspend fun resolveClient(): ResolvedClient? {
        val model = resolveModel() ?: return null
        val provider = providerRepository.getProvider(model.providerId) ?: return null
        val apiKey = providerRepository.decryptApiKey(provider.id) ?: return null
        return ResolvedClient(model.name, LlmClient(provider.baseUrl, apiKey))
    }

    private suspend fun resolveModel() = currentModelId.first()?.let { providerRepository.getModel(it) }

    private suspend fun resolveVisionClient(): ResolvedClient? {
        val id = visionModelId.first() ?: return null
        val model = providerRepository.getModel(id) ?: return null
        val provider = providerRepository.getProvider(model.providerId) ?: return null
        val apiKey = providerRepository.decryptApiKey(provider.id) ?: return null
        return ResolvedClient(model.name, LlmClient(provider.baseUrl, apiKey))
    }

    private suspend fun runVisionDirect(sid: Long, dataUrl: String): Flow<StreamEvent> = flow {
        val profile = profileRepository.getProfile()
        val target = profileRepository.getTarget()
        val system = promptBuilder.buildSystem(profile, target, "")
        val client = resolveClient() ?: run {
            emit(StreamEvent.Error(noConfigError()))
            return@flow
        }
        client.client.stream(
            ChatRequest(
                model = client.model,
                system = system,
                userText = "以下是用户聊天截图，请按五步法分析。",
                imageDataUrl = dataUrl,
                history = buildHistory(sid, IMAGE_PLACEHOLDER),
            )
        ).collect { event ->
            when (event) {
                is LlmEvent.Delta -> emit(StreamEvent.Delta(event.text))
                is LlmEvent.Thinking -> emit(StreamEvent.Thinking(event.text))
                is LlmEvent.Done -> {
                    val analysis = runCatching { AnalysisParser.parse(event.fullText) }.getOrNull()
                    if (analysis != null) {
                        conversationRepository.addMessage(sid, "ASSISTANT", "analysis", event.fullText)
                        emit(StreamEvent.Analysis(UiMappers.toAnalysisCard(analysis)))
                    }
                    emit(StreamEvent.Done)
                }
                is LlmEvent.Failed -> emit(StreamEvent.Error(UiMappers.toLlmError(event.error)))
            }
        }
    }

    private fun parseSafety(hit: String) = AnalysisParser.parse(
        """{"steps":[],"reply":"","citations":[],"safety_override":true,"safety_message":"检测到可能涉及安全风险的表述（$hit）。请优先确保自己的人身安全：离开危险环境，联系可信的人或当地紧急服务。我们无法在危机中提供恋爱建议。","token_estimate":0}"""
    )

    private fun noConfigError(): LlmError =
        LlmError("NO_CONFIG", "请先在设置中配置 API Key 与主模型", false)

    // ===== v1.3 freetext 状态摘要辅助 =====

    /**
     * 新话题时提炼话题摘要：取用户输入的前 24 字（足够模型对齐"在聊什么"）。
     * 模型输出仅作补充——首句关键词追加在后面，总长控制在 40 字内。
     */
    private fun summarizeTopic(userInput: String, modelOutput: String): String {
        val base = userInput.replace(Regex("\\s+"), " ").take(24)
        val firstSentence = modelOutput
            .replace(Regex("[#*>`\\-]"), "")
            .split(Regex("[。！？\n]"))
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(16)
            .orEmpty()
        return if (firstSentence.isBlank()) base else "$base｜$firstSentence".take(40)
    }

    /**
     * 提炼本轮结论摘要：取模型输出的首个完整句（至多 40 字），作为"已给结论"记入状态。
     */
    private fun summarizeConclusion(modelOutput: String): String =
        modelOutput
            .replace(Regex("[#*>`\\-]"), "")
            .split(Regex("[。！？\n]"))
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(40)
            .orEmpty()

    /**
     * 从 freetext 输出中探测"可发送话术"段落（reply-on-demand 约定：
     * 模型给话术时会单独成段，常以「可以发」「直接回」等引导）。
     * 未探测到 → 空串，状态记为本轮未给话术。
     */
    private fun extractReplySection(modelOutput: String): String {
        val marker = REPLY_SECTION_PATTERN.find(modelOutput) ?: return ""
        val after = modelOutput.substring(marker.range.last + 1).trimStart('：', ':', ' ', '\n')
        // 取引导词之后的引号内文本或首行，作为话术原文
        val quoted = Regex("[\"「『](.{4,120}?)[\"」』]").find(after)?.groupValues?.get(1)
        return (quoted ?: after.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()).take(120)
    }

    private data class ResolvedClient(val model: String, val client: LlmClient)

    private companion object {
        /** 历史消息 token 上限（粗估，字符数/4），超出从最早轮次成对丢弃 */
        const val HISTORY_TOKEN_LIMIT = 24_000
        /** 历史中的图片消息占位文本 */
        const val IMAGE_PLACEHOLDER = "[用户发送了一张聊天截图]"

        /** freetext 输出中"可发送话术"段落的引导词（reply-on-demand 约定） */
        val REPLY_SECTION_PATTERN = Regex("(可以发|可以直接发|可以回|直接回|这样回|发这句|回这句|发这段话)")
    }
}
