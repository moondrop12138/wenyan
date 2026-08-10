package com.wenyan.desktop

import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.domain.ConversationState
import com.wenyan.app.domain.ConversationStateTracker
import com.wenyan.app.domain.MemoryExtractor
import com.wenyan.app.knowledge.DesktopKnowledgeAssetReader
import com.wenyan.app.knowledge.KnowledgeEngine
import com.wenyan.app.llm.AnalysisParser
import com.wenyan.app.llm.ChatHistoryMessage
import com.wenyan.app.llm.ChatRequest
import com.wenyan.app.llm.CoachAnalysis
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

        service.addMessage(sessionId, "USER", "text", text)

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
        val user = when (routeByInputShape(text)) {
            InputShape.CHAT_LOG -> promptBuilder.buildUserText(text)
            InputShape.SHORT -> {
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
                is LlmEvent.Delta ->
                    onEvent(JSONObject().put("type", "chat").put("text", event.text))
                is LlmEvent.Thinking ->
                    onEvent(JSONObject().put("type", "thinking").put("text", event.text))
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
                            sideEffectScope.launch { extractMemoryOnce(session.targetId, text, event.fullText, resolved) }
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

    /** 记忆注入：惰性搬移 note→facts 后，以 facts 拼 note（PromptBuilder 零改动契约） */
    private suspend fun resolveTargetWithMemory(targetId: Long?): TargetEntity? {
        if (targetId == null) return null
        val target = service.getTarget(targetId) ?: return null
        val memory = memoryText(target.id)
        return if (memory == target.note) target else target.copy(note = memory)
    }

    private suspend fun memoryText(targetId: Long): String {
        migrateNoteToFactsOnce(targetId)
        return service.listFacts(targetId).joinToString("；") { it.text }.take(2000)
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
     * 超长（字符/4 > 24000 token）从最早成对丢弃。
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
        // token 预算：超限从最早成对丢弃
        fun tokens(list: List<ChatHistoryMessage>) = list.sumOf { it.content.length } / 4
        while (mapped.size >= 2 && tokens(mapped) > MAX_HISTORY_TOKENS) {
            mapped.removeAt(0)
            if (mapped.isNotEmpty()) mapped.removeAt(0)
        }
        return mapped
    }

    private fun shouldExtractMemory(state: ConversationState, text: String): Boolean =
        !state.hasActiveTopic && text.length >= 10

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

    /** 新话题自动提炼记忆（失败静默）：主模型提炼 facts → mergeFacts 去重 → 逐条入库 */
    private suspend fun extractMemoryOnce(targetId: Long, userText: String, fullText: String, resolved: ResolvedClient) {
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
            val merged = MemoryExtractor.mergeFacts(existingFacts, facts)
            merged.drop(existingFacts.size).forEach { service.addFact(targetId, it) }
        }
    }

    private fun emitError(onEvent: (JSONObject) -> Unit, code: String, message: String) {
        onEvent(JSONObject().put("type", "error").put("code", code).put("message", message))
    }

    companion object {
        /** 历史 token 预算（与手机版一致：24000） */
        private const val MAX_HISTORY_TOKENS = 24000
    }
}

/** CoachAnalysis → 前端卡片 JSON（与 AnalysisParser 的 v2 schema 对齐） */
fun CoachAnalysis.toJson(): JSONObject = JSONObject()
    .put("inputKind", inputKind.name.lowercase())
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
