package com.wenyan.desktop

import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.domain.ConversationState
import com.wenyan.app.domain.ConversationStateTracker
import com.wenyan.app.domain.HistoryCompactor
import com.wenyan.app.domain.MemoryExtractor
import com.wenyan.app.knowledge.DesktopKnowledgeAssetReader
import com.wenyan.app.knowledge.KnowledgeEngine
import com.wenyan.app.llm.AnalysisParser
import com.wenyan.app.llm.ChatHistoryMessage
import com.wenyan.app.llm.ChatRequest
import com.wenyan.app.llm.CoachAnalysis
import com.wenyan.app.llm.InputKind
import com.wenyan.app.llm.LlmClient
import com.wenyan.app.llm.LlmErrorCode
import com.wenyan.app.llm.LlmEvent
import com.wenyan.app.prompt.PromptBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * 桌面版聊天引擎：移植 RealChatRepository.sendTextFlow 的完整链路（去掉 Android UI 接缝）。
 *
 * 链路：危机预检 → USER 落库 → 状态机推进 → 知识路由注入 → PromptBuilder 三层拼装
 *      → 输入形态路由选 user 模板 → LlmClient SSE 流式 → 事件回调
 *      → Done 后 AnalysisParser 解析落库 + 状态回填 + 异步拟题 + 异步记忆提炼。
 *
 * 事件经 [onEvent] 回调以 JSON 吐出（chat/thinking/card/done/error），由路由层封装为 SSE 帧。
 */
class ChatEngine(
    private val service: WenyanService,
    private val knowledgeEngine: KnowledgeEngine = KnowledgeEngine(DesktopKnowledgeAssetReader()),
) {
    private val promptBuilder = PromptBuilder()
    private val stateTracker = ConversationStateTracker()
    private val sideEffectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val migrateMutex = Mutex()

    // ===== 输入形态路由（移植 ChatViewModel.routeByInputShape，四分优先级不变） =====

    enum class InputShape { CHAT_LOG, SHORT }

    fun routeByInputShape(text: String): InputShape {
        val trimmed = text.trim()
        val isMultiLine = trimmed.contains('\n')
        val hasQuotes = trimmed.any { it == '"' || it == '“' || it == '”' || it == '\'' || it == '‘' || it == '’' }
        val looksLikeChatLog = trimmed.contains("：") && trimmed.contains("\n")
        return if (looksLikeChatLog || isMultiLine || hasQuotes || trimmed.length > 40) {
            InputShape.CHAT_LOG
        } else {
            InputShape.SHORT
        }
    }

    // ===== 主链路 =====

    /**
     * 发送一条文本消息，流式事件经 [onEvent] 回调。
     * [imageDataUrls] 非空时走通道 A（主模型直读图片，需前端选定 supportsVision 模型）。
     */
    suspend fun sendMessage(
        sessionId: Long,
        modelId: Long,
        text: String,
        imageDataUrls: List<String> = emptyList(),
        onEvent: (JSONObject) -> Unit,
    ) {
        // AC-13：危机关键词本地预检，命中即转介（不落库、不调 LLM）
        val crisis = com.wenyan.app.knowledge.CrisisDetector.detect(text)
        if (crisis.isNotEmpty()) {
            onEvent(JSONObject().put("type", "card").put("card", parseSafety(crisis.first()).toJson()))
            onEvent(JSONObject().put("type", "done"))
            return
        }

        val session = service.getSession(sessionId) ?: run {
            emitError(onEvent, "NO_SESSION", "会话不存在")
            return
        }
        val resolved = resolveClient(modelId) ?: run {
            emitError(onEvent, "NO_CONFIG", "请先在设置中配置 API Key 与模型")
            return
        }

        // v1.8.2-fix（审查 P1-1）：统一落库对齐手机端 analyzeImagesFlow——
        // 无论通道 A/B 都先落库：每张图一条 image 消息 + 配文一条 text 消息；
        // 纯图发送（text 为 [图片] 占位）不落占位文本气泡（此前通道 A 只落 text 且图片丢失）。
        val caption = text.trim()
        if (imageDataUrls.isNotEmpty()) {
            imageDataUrls.forEach { service.addMessage(sessionId, "USER", "image", it) }
            if (caption.isNotEmpty() && caption != IMAGE_PLACEHOLDER) {
                service.addMessage(sessionId, "USER", "text", caption)
            }
        } else {
            service.addMessage(sessionId, "USER", "text", text)
        }

        // 通道判定（对齐手机端 analyzeImagesFlow）：
        // 通道 A：主模型 supportsVision → 直读（下方主链路原样处理，imageDataUrls 已落库）；
        // 通道 B：主模型不支持视觉 → 视觉槽位模型先转述，发 transcription 帧后本轮结束
        //        （确认后由前端调 confirmTranscription 走主模型纯文本分析）。
        if (imageDataUrls.isNotEmpty()) {
            val mainModel = service.getModel(modelId)
            if (mainModel?.supportsVision != true) {
                runVisionTranscribe(sessionId, imageDataUrls, onEvent)
                return
            }
        }

        // 状态机推进（同题判定 + 状态前缀）
        val previousState = ConversationState.fromJson(session.stateJson)
        val wasNewTopic = previousState.hasActiveTopic && !stateTracker.isSameTopic(previousState, text)
        val state = stateTracker.onUserInput(previousState, text).also {
            service.updateSessionState(sessionId, it.toJson())
        }
        val statePrefix = stateTracker.buildStatePrefix(state)

        // 知识路由 + 三层拼装
        val (knowledge, refDocs) = knowledgeEngine.buildInjection(text)
        val profile = service.getLatestProfile()
        val target = resolveTargetWithMemory(session.targetId)
        val system = promptBuilder.buildSystem(profile, target, knowledge)
        val history = buildHistory(sessionId, text)
        // v1.8.2-fix（审查 P1-1）：纯图（无配文，前端以 [图片] 占位发送）走固定分析指令，
        // 对齐手机版 runVisionDirect——否则 "[图片]" 会当普通短句进 buildUserReply 模板。
        val isPureImage = imageDataUrls.isNotEmpty() &&
            (caption.isEmpty() || caption == IMAGE_PLACEHOLDER)
        val user = when {
            isPureImage -> "以下是用户聊天截图，请按四段结构分析。"
            routeByInputShape(text) == InputShape.CHAT_LOG -> promptBuilder.buildUserText(text)
            else -> {
                val recentContext = history.takeLast(6)
                    .joinToString("\n") { h -> (if (h.role == "user") "用户" else "军师") + "：" + h.content.take(200) }
                    .takeIf { it.isNotBlank() }
                promptBuilder.buildUserReply(text, recentContext, statePrefix)
            }
        }

        resolved.client.stream(
            ChatRequest(resolved.modelName, system, user, imageDataUrls = imageDataUrls, history = history),
        ).collect { event ->
            when (event) {
                is LlmEvent.Delta -> {
                    // v1.8.1 桌面版不再把原始 token 流式展示给用户；
                    // 最终形态是四段卡片，Delta 仅拼入 accumulator 等待 Done 后解析。
                }
                is LlmEvent.Thinking -> {
                    // reasoning_content 已彻底舍弃展示，不传给前端。
                }
                is LlmEvent.Done -> {
                    val analysis = runCatching { AnalysisParser.parseAny(event.fullText) }.getOrNull()
                    if (analysis != null) {
                        service.addMessage(sessionId, "ASSISTANT", "analysis", event.fullText)
                        val refs = refDocs.ifEmpty { analysis.citations }
                        if (refs.isNotEmpty()) service.updateSessionRefDocs(sessionId, JSONArray(refs).toString())
                        // 状态回填：结论=advice.core（空则 empathy 首句），话术=reply
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
                        service.updateSessionState(sessionId, newState.toJson())
                        onEvent(
                            JSONObject().put("type", "card")
                                .put("card", analysis.toJson().put("citations", JSONArray(refs)))
                        )
                        // 异步副作用（失败静默，不阻塞流）
                        sideEffectScope.launch { generateTitleOnce(sessionId, text, event.fullText, resolved) }
                        if (session.targetId != null && shouldExtractMemory(state, text)) {
                            // v1.9.1：CHAT_LOG（粘贴聊天记录）=paste，其余=口述输入
                            val source = if (routeByInputShape(text) == InputShape.CHAT_LOG) {
                                MemoryFactEntity.SOURCE_PASTE
                            } else {
                                MemoryFactEntity.SOURCE_CHAT
                            }
                            sideEffectScope.launch { extractMemoryOnce(session.targetId, text, event.fullText, resolved, source) }
                        }
                    } else {
                        // 解析失败不落库（与手机版一致：Room 无脏数据）
                        emitError(onEvent, LlmErrorCode.PARSE_ERROR.name, LlmErrorCode.PARSE_ERROR.userMessage)
                        return@collect
                    }
                    onEvent(JSONObject().put("type", "done"))
                }
                is LlmEvent.Failed -> {
                    emitError(onEvent, event.error.name, event.error.userMessage + if (event.detail.isNotBlank()) "（${event.detail.take(120)}）" else "")
                }
            }
        }
    }

    /**
     * 通道 B 第一步（移植 RealChatRepository.analyzeImagesFlow 通道 B）：
     * 用视觉槽位模型（可跨 provider，自带 baseUrl/apiKey）把截图转述成文字，
     * 完成后发 transcription 帧，本轮 SSE 到此结束（不发 done）；确认走 confirmTranscription。
     * 未配置槽位 → NO_VISION；转述为空 → EMPTY；失败 → error 帧。
     */
    private suspend fun runVisionTranscribe(
        sessionId: Long,
        imageDataUrls: List<String>,
        onEvent: (JSONObject) -> Unit,
    ) {
        val vision = resolveVisionClient() ?: run {
            emitError(onEvent, "NO_VISION", "当前模型不支持图片，且未配置视觉模型。请到 设置 → 视觉模型 选择一个支持图片的模型。")
            return
        }
        val transcription = StringBuilder()
        vision.client.stream(
            ChatRequest(
                model = vision.modelName,
                system = "你是截图文字提取器。只输出截图中的文字，尽量保留说话人、顺序与间隔，不添加任何解释。",
                userText = "请提取这几张聊天截图中的全部文字，按截图顺序输出。",
                imageDataUrls = imageDataUrls,
            )
        ).collect { event ->
            when (event) {
                is LlmEvent.Delta -> transcription.append(event.text)
                is LlmEvent.Thinking -> { /* reasoning 不外传（与主链路一致） */ }
                is LlmEvent.Failed -> emitError(onEvent, event.error.name, event.error.userMessage + if (event.detail.isNotBlank()) "（${event.detail.take(120)}）" else "")
                is LlmEvent.Done -> {
                    if (transcription.isBlank()) {
                        emitError(onEvent, "EMPTY", "模型未提取到文字，请重试或重新选图")
                    } else {
                        onEvent(JSONObject().put("type", "transcription").put("text", transcription.toString()))
                    }
                }
            }
        }
    }

    /**
     * 通道 B 第二步（移植 RealChatRepository.confirmTranscription）：
     * 用户确认（可编辑）转述文本后：落库 type="transcription" → 知识注入 +
     * buildUserTranscription → 主模型纯文本分析 → card/done。
     * 记忆提炼素材取 transcription（对齐手机版语义）。
     */
    suspend fun confirmTranscription(
        sessionId: Long,
        modelId: Long,
        transcription: String,
        onEvent: (JSONObject) -> Unit,
    ) {
        val session = service.getSession(sessionId) ?: run {
            emitError(onEvent, "NO_SESSION", "会话不存在")
            return
        }
        val resolved = resolveClient(modelId) ?: run {
            emitError(onEvent, "NO_CONFIG", "请先在设置中配置 API Key 与模型")
            return
        }

        service.addMessage(sessionId, "USER", "transcription", transcription)

        // 状态机推进（转述即用户素材，同题判定与状态前缀与文本链路一致）
        val previousState = ConversationState.fromJson(session.stateJson)
        val wasNewTopic = previousState.hasActiveTopic && !stateTracker.isSameTopic(previousState, transcription)
        val state = stateTracker.onUserInput(previousState, transcription).also {
            service.updateSessionState(sessionId, it.toJson())
        }
        val statePrefix = stateTracker.buildStatePrefix(state)

        val (knowledge, refDocs) = knowledgeEngine.buildInjection(transcription)
        val profile = service.getLatestProfile()
        val target = resolveTargetWithMemory(session.targetId)
        val system = promptBuilder.buildSystem(profile, target, knowledge)
        val history = buildHistory(sessionId, transcription)
        val user = promptBuilder.buildUserTranscription(transcription)

        resolved.client.stream(
            ChatRequest(resolved.modelName, system, user, history = history),
        ).collect { event ->
            when (event) {
                is LlmEvent.Delta -> { /* 拼入 accumulator，Done 后解析（不流式展示） */ }
                is LlmEvent.Thinking -> { /* reasoning 不外传 */ }
                is LlmEvent.Done -> {
                    val analysis = runCatching { AnalysisParser.parseAny(event.fullText) }.getOrNull()
                    if (analysis != null) {
                        service.addMessage(sessionId, "ASSISTANT", "analysis", event.fullText)
                        val refs = refDocs.ifEmpty { analysis.citations }
                        if (refs.isNotEmpty()) service.updateSessionRefDocs(sessionId, JSONArray(refs).toString())
                        val newState = stateTracker.onModelReply(
                            state = state,
                            topicSummary = if (wasNewTopic || !state.hasActiveTopic) {
                                summarizeTopic(transcription, analysis)
                            } else {
                                state.topicSummary
                            },
                            conclusion = summarizeConclusion(analysis),
                            reply = analysis.reply,
                        )
                        service.updateSessionState(sessionId, newState.toJson())
                        onEvent(
                            JSONObject().put("type", "card")
                                .put("card", analysis.toJson().put("citations", JSONArray(refs)))
                        )
                        sideEffectScope.launch { generateTitleOnce(sessionId, transcription, event.fullText, resolved) }
                        if (session.targetId != null && shouldExtractMemory(state, transcription)) {
                            // v1.9.1：转述/截图通道来源=transcription
                            sideEffectScope.launch { extractMemoryOnce(session.targetId, transcription, event.fullText, resolved, MemoryFactEntity.SOURCE_TRANSCRIPTION) }
                        }
                    } else {
                        emitError(onEvent, LlmErrorCode.PARSE_ERROR.name, LlmErrorCode.PARSE_ERROR.userMessage)
                        return@collect
                    }
                    onEvent(JSONObject().put("type", "done"))
                }
                is LlmEvent.Failed -> {
                    emitError(onEvent, event.error.name, event.error.userMessage + if (event.detail.isNotBlank()) "（${event.detail.take(120)}）" else "")
                }
            }
        }
    }

    // ===== 测连接（移植 RealSettingsRepository.testAllModels 语义） =====

    /**
     * 对 provider 下每个已配置模型发最小流式 chat（system="你好", userText="ping"）。
     * 任一成功 → 绿灯（ok=true）；全失败 → 红灯 + 最后错误。同时回写 connectionStatus。
     */
    suspend fun testConnection(providerId: Long): JSONObject {
        val provider = service.getProvider(providerId)
            ?: return JSONObject().put("ok", false).put("error", "provider not found")
        val apiKey = service.decryptApiKey(providerId)
        if (apiKey.isNullOrBlank()) {
            service.updateConnectionStatus(providerId, "fail")
            return JSONObject().put("ok", false).put("error", "未配置 API Key")
                .put("errorCode", "NO_API_KEY")
        }
        val models = service.listModels(providerId)
        if (models.isEmpty()) {
            service.updateConnectionStatus(providerId, "fail")
            return JSONObject().put("ok", false).put("error", "未配置模型")
                .put("errorCode", "NO_MODEL")
        }

        var lastError: LlmEvent.Failed? = null
        var successModel: String? = null
        for (model in models) {
            val client = LlmClient(provider.baseUrl, apiKey)
            var failed: LlmEvent.Failed? = null
            client.stream(ChatRequest(model.name, system = "你好", userText = "ping")).collect { event ->
                when (event) {
                    is LlmEvent.Done -> { /* 成功 */ }
                    is LlmEvent.Failed -> failed = event
                    else -> { /* Delta/Thinking 忽略 */ }
                }
            }
            if (failed == null) {
                successModel = model.name
                break
            }
            lastError = failed
        }

        val ok = successModel != null
        service.updateConnectionStatus(providerId, if (ok) "ok" else "fail")
        val result = JSONObject().put("ok", ok)
        if (ok) {
            result.put("model", successModel)
        } else {
            result.put("errorCode", lastError?.error?.name ?: "UNKNOWN")
            result.put("error", lastError?.error?.userMessage ?: "请求失败，请稍后重试")
        }
        return result
    }

    // ===== 内部辅助 =====

    private class ResolvedClient(val modelName: String, val client: LlmClient)

    private suspend fun resolveClient(modelId: Long): ResolvedClient? {
        val model = service.getModel(modelId) ?: return null
        val provider = service.getProvider(model.providerId) ?: return null
        val apiKey = service.decryptApiKey(provider.id) ?: return null
        return ResolvedClient(model.name, LlmClient(provider.baseUrl, apiKey))
    }

    /** 视觉槽位解析（移植 RealChatRepository.resolveVisionClient）：可跨 provider，用槽位模型自己的 baseUrl/apiKey */
    private suspend fun resolveVisionClient(): ResolvedClient? {
        val id = service.getVisionModelId() ?: return null
        return resolveClient(id)
    }

    /** 记忆注入：惰性搬移 note→facts 后，以 facts 拼 note（PromptBuilder 零改动契约） */
    private suspend fun resolveTargetWithMemory(targetId: Long?): TargetEntity? {
        if (targetId == null) return null
        val target = service.getTarget(targetId) ?: return null
        val memory = memoryText(target.id)
        return if (memory == target.note) target else target.copy(note = memory)
    }

    private suspend fun memoryText(targetId: Long): String {
        migrateNoteToFactsOnce(targetId)
        // v1.9.0：hypothesis（模型推断）条目带「（推测，待验证）」标注注入，与事实区分
        // v1.9.1：expiresAt 已到期条目过滤不注入；transcription 来源带「（来自截图转述）」标注
        val now = System.currentTimeMillis()
        return service.listFacts(targetId)
            .filter { fact -> fact.expiresAt == null || fact.expiresAt > now }
            .joinToString("；") { fact ->
                val annotation = when {
                    fact.kind == com.wenyan.app.data.db.MemoryFactEntity.KIND_HYPOTHESIS -> "（推测，待验证）"
                    fact.source == MemoryFactEntity.SOURCE_TRANSCRIPTION -> "（来自截图转述）"
                    else -> ""
                }
                if (annotation.isEmpty()) fact.text else "${fact.text}$annotation"
            }
            .take(2000)
    }

    private suspend fun migrateNoteToFactsOnce(targetId: Long) {
        migrateMutex.withLock {
            val target = service.getTarget(targetId) ?: return
            if (target.note.isBlank()) return
            val existing = service.listFacts(targetId).map { it.text }
            val segments = MemoryExtractor.splitNoteToFacts(target.note).take(MemoryExtractor.DEFAULT_FACT_LIMIT)
            val toAdd = MemoryExtractor.mergeFacts(existing, segments).drop(existing.size)
            toAdd.forEach { service.addFact(targetId, it) }
            service.clearTargetNote(targetId)
        }
    }

    /**
     * 历史构造（移植 buildHistory）：取会话全部消息映射 role，剔除末尾重复 USER，
     * 超长（字符/4 > 24000 token）预算选择式压缩（v1.9.1，与手机版一致）：
     * 先对早期消息裁剪保头（每条 ≤200 字 + 截断标记，末尾 6 轮工作集完整），仍超再从最早成对丢弃。
     */
    private suspend fun buildHistory(sessionId: Long, currentText: String): List<ChatHistoryMessage> {
        val messages = service.listMessages(sessionId)
        val mapped = messages.mapNotNull { m ->
            when {
                m.type == "text" -> ChatHistoryMessage(m.role.lowercase(), m.content)
                m.type == "image" -> ChatHistoryMessage(m.role.lowercase(), "[图片]")
                m.type == "transcription" -> ChatHistoryMessage(m.role.lowercase(), "[截图转述] ${m.content}")
                m.type == "analysis" -> {
                    val reply = runCatching { AnalysisParser.parseAny(m.content).reply }.getOrDefault("")
                    if (reply.isBlank()) null else ChatHistoryMessage(m.role.lowercase(), reply)
                }
                else -> null
            }
        }.toMutableList()
        // 剔除末尾与本轮重复的 USER（本轮已落库，user 模板会再带一次）
        if (mapped.isNotEmpty() && mapped.last().role == "user" && mapped.last().content == currentText) {
            mapped.removeAt(mapped.size - 1)
        }
        fun tokens(list: List<ChatHistoryMessage>) = HistoryCompactor.estimatedTokens(list)
        // v1.9.1 预算选择式压缩（共享 HistoryCompactor：先裁剪早期消息保头，仍超再从最早成对丢弃）
        val (compacted, _) = HistoryCompactor.compact(mapped)
        return compacted
    }

    private fun shouldExtractMemory(state: ConversationState, text: String): Boolean =
        service.isMemoryAutoEnabled() && !state.hasActiveTopic && text.length >= 10

    private fun summarizeTopic(text: String, analysis: CoachAnalysis): String =
        analysis.facts.known.firstOrNull()?.take(30) ?: text.take(30)

    private fun summarizeConclusion(analysis: CoachAnalysis): String =
        analysis.advice.core.ifBlank { analysis.empathy.lineSequence().firstOrNull().orEmpty() }.take(80)

    private fun parseSafety(hit: String): CoachAnalysis = AnalysisParser.parseAny(
        """{"input_kind":"user_question","empathy":"","reply":"","reply_timing":"","facts":{"known":[],"assumed":[],"unknown":[]},"advice":{"tag":"","core":"","reasons":[],"styles":[]},"actions":[],"citations":[],"safety_override":true,"safety_message":"检测到可能涉及安全风险的表述（$hit）。请优先确保自己的人身安全：离开危险环境，联系可信的人或当地紧急服务。我们无法在危机中提供恋爱建议。","token_estimate":0}"""
    )

    /** 首轮回复完成后异步拟题（幂等：已有标题跳过；失败静默） */
    private suspend fun generateTitleOnce(sessionId: Long, userText: String, fullText: String, resolved: ResolvedClient) {
        runCatching {
            val session = service.getSession(sessionId) ?: return
            if (session.title.isNotBlank()) return
            val reply = runCatching { AnalysisParser.parseAny(fullText).reply }.getOrDefault("")
            val prompt = "请为以下对话起一个 10 字以内的会话标题，只输出标题本身，不要标点：\n用户：${userText.take(100)}\n军师：${reply.take(100)}"
            var title = ""
            resolved.client.stream(ChatRequest(resolved.modelName, system = "你是标题生成器。", userText = prompt))
                .collect { event ->
                    if (event is LlmEvent.Done) title = event.fullText.trim().take(20)
                }
            if (title.isNotBlank()) service.updateSessionTitle(sessionId, title)
        }
    }

    /** 新话题自动提炼记忆（失败静默）：主模型提炼 facts → mergeFacts 去重 → 逐条入库（v1.9.0 带 kind 分层 + 撤销日志；v1.9.1 带 expiresAt/source） */
    private suspend fun extractMemoryOnce(targetId: Long, userText: String, fullText: String, resolved: ResolvedClient, source: String) {
        runCatching {
            val reply = runCatching { AnalysisParser.parseAny(fullText).reply }.getOrDefault(fullText.take(500))
            val existingFacts = service.listFacts(targetId).map { it.text }
            val prompt = MemoryExtractor.buildPrompt(userText, reply, existingFacts.joinToString("；").take(2000))
            var json = ""
            resolved.client.stream(
                ChatRequest(resolved.modelName, system = "你是记忆提炼器。只输出 JSON，不加解释。", userText = prompt),
            ).collect { event ->
                if (event is LlmEvent.Done) json = event.fullText
            }
            val facts = MemoryExtractor.parseFacts(json)
            if (facts.isEmpty()) return
            val merged = MemoryExtractor.mergeFacts(existingFacts, facts.map { it.text })
            val toAdd = merged.drop(existingFacts.size)
            if (toAdd.isEmpty()) return
            val addedIds = mutableListOf<Long>()
            toAdd.forEach { text ->
                val fact = facts.firstOrNull { it.text == text }
                val id = service.addFact(
                    targetId = targetId,
                    text = text,
                    kind = fact?.kind ?: MemoryExtractor.KIND_FACT,
                    expiresAt = MemoryExtractor.computeExpiryMillis(fact?.expiresIn),
                    source = source,
                )
                if (id > 0L) addedIds.add(id)
            }
            if (addedIds.isNotEmpty()) {
                service.recordMemoryWrite(targetId, addedIds, userText.take(20))
            }
        }
    }

    private fun emitError(onEvent: (JSONObject) -> Unit, code: String, message: String) {
        onEvent(JSONObject().put("type", "error").put("code", code).put("message", message))
    }

    companion object {
        /** 纯图发送时前端占位文本（与手机版 IMAGE_PLACEHOLDER 一致；不落库为 text 消息） */
        private const val IMAGE_PLACEHOLDER = "[图片]"
    }
}

/** CoachAnalysis → 前端卡片 JSON（与 AnalysisParser 的 v2 schema 对齐） */
fun CoachAnalysis.toJson(): JSONObject = JSONObject()
    .put("inputKind", inputKind.name.lowercase())
    // v1.8.2-fix（审查 P2-7）：输出澄清标记，前端据此显示「先确认一下」并隐藏复制话术
    .put("isClarification", inputKind == InputKind.UNCERTAIN)
    .put("empathy", empathy)
    .put("reply", reply)
    .put("replyTiming", replyTiming)
    .put("facts", JSONObject()
        .put("known", JSONArray(facts.known))
        .put("assumed", JSONArray(facts.assumed))
        .put("unknown", JSONArray(facts.unknown)))
    .put("advice", JSONObject()
        .put("tag", advice.tag)
        .put("core", advice.core)
        .put("reasons", JSONArray(advice.reasons))
        .put("styles", JSONArray(advice.styles.map {
            JSONObject().put("key", it.key).put("label", it.label).put("text", it.text)
        })))
    .put("actions", JSONArray(actions.map {
        JSONObject().put("label", it.label).put("text", it.text)
    }))
    .put("citations", JSONArray(citations))
    .put("memoryCitations", JSONArray(memoryCitations))
    .put("safetyOverride", safetyOverride)
    .put("safetyMessage", safetyMessage)
