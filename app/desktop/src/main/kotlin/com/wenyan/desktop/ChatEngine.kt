package com.wenyan.desktop

import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.SessionEntity
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.domain.ChatOrchestrator
import com.wenyan.app.domain.ConversationState
import com.wenyan.app.domain.ConversationStateTracker
import com.wenyan.app.domain.HistoryCompactor
import com.wenyan.app.domain.MemoryExtractor
import com.wenyan.app.knowledge.DesktopKnowledgeAssetReader
import com.wenyan.app.knowledge.KnowledgeEngine
import com.wenyan.app.knowledge.KnowledgeRouting
import com.wenyan.app.knowledge.LlmRouteClassifier
import com.wenyan.app.knowledge.RoutingCatalog
import com.wenyan.app.knowledge.currentRouteClassifier
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
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * 同会话聊天互斥（发送/重跑共用）：前端 S.streaming 是第一道门，此处是第二道——
     * 快速点两次只跑一次，第二次直接回 RETRY_RUNNING 错误帧（前端 toast 提示并保留旧卡）；
     * 发送与重跑跨入口并发同样只跑一次（直接调接口也绕不过）。
     */
    private val sessionLocks = ConcurrentHashMap<Long, Mutex>()

    /** 同会话互斥执行：锁被占 → RETRY_RUNNING 错误帧并跳过本次。 */
    private suspend fun withSessionLock(
        sessionId: Long,
        onEvent: (JSONObject) -> Unit,
        body: suspend () -> Unit,
    ) {
        val lock = sessionLocks.getOrPut(sessionId) { Mutex() }
        if (!lock.tryLock()) {
            emitError(onEvent, "RETRY_RUNNING", "正在处理上一条消息，请稍候")
            return
        }
        try {
            body()
        } finally {
            lock.unlock()
        }
    }

    /**
     * LLM 知识路由目录（懒解析 classpath routing-catalog.json，首次发送才触达资源；
     * null = 目录缺失/坏数据，分类器不可用，路由始终走离线兜底。对称安卓 RealChatRepository.routeCatalog）
     */
    private val routeCatalog: RoutingCatalog? by lazy {
        RoutingCatalog.parse(DesktopKnowledgeAssetReader().readRoutingCatalogJson())
    }

    /**
     * 平台侧 LLM 路由装配（对称安卓 RealChatRepository.routeClassifier）：
     * knowledgeRouting=llm（默认）且请求模型三元组可得 → 分类器；offline（用户显式关闭）/ 未配置 → null（纯离线兜底）。
     * offline 时 [currentRouteClassifier] 短路，不读模型配置（零解密、零路由请求）；
     * 桌面主模型随聊天请求传入（前端逐请求带 modelId），与手机端 DataStore 槽位解析等价。
     */
    private suspend fun routeClassifier(modelId: Long): LlmRouteClassifier? = currentRouteClassifier(
        catalog = routeCatalog,
        isLlmRouting = { service.getKnowledgeRouting() == KnowledgeRouting.LLM },
        resolveMainModelConfig = { service.resolveRouteLlmConfig(modelId) },
    )

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
     * 同会话互斥（与重跑共用锁）：并发第二次直接回 RETRY_RUNNING。
     */
    suspend fun sendMessage(
        sessionId: Long,
        modelId: Long,
        text: String,
        imageDataUrls: List<String> = emptyList(),
        onEvent: (JSONObject) -> Unit,
    ) {
        withSessionLock(sessionId, onEvent) {
            sendMessageInner(sessionId, modelId, text, imageDataUrls, onEvent)
        }
    }

    /** sendMessage 主体（互斥包裹见 [sendMessage]）。 */
    private suspend fun sendMessageInner(
        sessionId: Long,
        modelId: Long,
        text: String,
        imageDataUrls: List<String>,
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

        // 主分析链路（与 confirmTranscription / retryMessage 共用；落库与通道判定已在上方完成）
        runTextPipeline(
            session = session,
            resolved = resolved,
            text = text,
            modelId = modelId,
            imageDataUrls = imageDataUrls,
            onEvent = onEvent,
        )
    }

    /**
     * 重跑指定 AI 卡片所在轮次（前端 AI 气泡重跑图标按钮入口）：
     * 完全重答语义——成功后原位替换：新回答解析成功后**更新旧行本身**（见 handleDone，
     * id/顺序不变——无新旧双卡瞬态、取消无半态、二次重跑不漂移）；失败/取消不写库，
     * 前端保留旧卡。card/done 帧带该行 messageId，前端 done 后原位换入新卡并绑定 id。
     *
     * 轮次定位（与 Android ChatViewModel.regenerate 同规则）：[messageId] 为该 AI 卡片
     * 的消息 id 时取「上一条 ASSISTANT 之后到本条之前」的窗口；[messageId] 缺省（流式
     * 刚完成尚未绑定 id 的旧调用）时取会话最后一条 ASSISTANT 作为替换目标，保证旧调用不断；
     * [messageId] 非空但库中找不到 → 报错提示（绝不静默回退到别的轮次）。
     * 不重复落 USER 消息；无可重跑素材 → NO_RETRY 错误帧。
     *
     * 素材优先级（与 Android ChatViewModel.regenerate 同规则）：
     * 1) 窗口内已有 USER transcription（截图转述已确认过）→ 直接按转述文本重答，
     *    不需要视觉模型、不需要重新确认（重答对象是分析回答，不是转述本身）；
     * 2) 窗口内有 USER image（通道 A 直读轮）→ 主模型 supportsVision 则带图直读重跑，
     *    不支持则报 NO_VISION_RETRY 可理解错误（绝不静默降级误命中更早文本轮）；
     * 3) 否则取窗口内最后一条 USER text。
     * 已落库状态不重复推进（pushUserState=false）。同会话互斥：与发送共用锁，只跑一次。
     */
    suspend fun retryMessage(
        sessionId: Long,
        modelId: Long,
        messageId: Long? = null,
        onEvent: (JSONObject) -> Unit,
    ) {
        withSessionLock(sessionId, onEvent) {
            retryMessageInner(sessionId, modelId, messageId, onEvent)
        }
    }

    private suspend fun retryMessageInner(
        sessionId: Long,
        modelId: Long,
        messageId: Long? = null,
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
        val messages = service.listMessages(sessionId)
        // 替换目标：messageId 命中取该条；缺省（未绑定 id 的旧调用）取最后一条 ASSISTANT；
        // 非空但找不到 → 明确报错，绝不回退到别的轮次重跑
        val targetIdx = if (messageId != null) {
            val idx = messages.indexOfFirst { it.id == messageId }
            if (idx < 0) {
                emitError(onEvent, "NO_RETRY", "这条回答已不存在，无法重新生成")
                return
            }
            idx
        } else {
            val idx = messages.indexOfLast { it.role == "ASSISTANT" }
            if (idx < 0) {
                emitError(onEvent, "NO_RETRY", "没有可重发的消息")
                return
            }
            idx
        }
        val replaceId = messages[targetIdx].id
        // 轮次窗口：上一条 ASSISTANT 之后到本条之前的 USER 素材
        val turnStart = messages.take(targetIdx).indexOfLast { it.role == "ASSISTANT" }
        val window = messages.subList(turnStart + 1, targetIdx)
        // 转述轮优先：直接按已确认的转述文本重答（无需视觉模型/无需重新确认）
        val transcription = window.lastOrNull {
            it.role == "USER" && it.type == "transcription" && it.content.isNotBlank()
        }
        if (transcription != null) {
            runTextPipeline(
                session = session,
                resolved = resolved,
                text = transcription.content,
                modelId = modelId,
                replaceMessageId = replaceId,
                sourceOverride = MemoryFactEntity.SOURCE_TRANSCRIPTION,
                transcriptionMode = true,
                pushUserState = false,
                onEvent = onEvent,
            )
            return
        }
        // 图片轮：主模型支持视觉则带图直读重跑；不支持则报可理解错误（与 Android 对齐）
        val windowImages = window
            .filter { it.role == "USER" && it.type == "image" }
            .map { it.content }
            .filter { it.startsWith("data:image") }
        if (windowImages.isNotEmpty()) {
            val mainModel = service.getModel(modelId)
            if (mainModel?.supportsVision != true) {
                emitError(
                    onEvent,
                    "NO_VISION_RETRY",
                    "这一轮是图片消息，当前模型不支持看图。请先到设置配置视觉模型，或换一个支持图片的模型再重来。",
                )
                return
            }
            runTextPipeline(
                session = session,
                resolved = resolved,
                text = window.lastOrNull { it.role == "USER" && it.type == "text" }?.content.orEmpty(),
                modelId = modelId,
                imageDataUrls = windowImages.take(MAX_IMAGES),
                replaceMessageId = replaceId,
                pushUserState = false,
                onEvent = onEvent,
            )
            return
        }
        val lastText = window.lastOrNull { it.role == "USER" && it.type == "text" && it.content.isNotBlank() }
            ?: run {
                emitError(onEvent, "NO_RETRY", "没有可重发的消息")
                return
            }
        runTextPipeline(
            session = session,
            resolved = resolved,
            text = lastText.content,
            modelId = modelId,
            replaceMessageId = replaceId,
            pushUserState = false,
            onEvent = onEvent,
        )
    }

    /**
     * 主分析链路（sendMessage / confirmTranscription / retryMessage 三入口共用——
     * 此类多份维护此前已造成过 L2 漂移，见 handleDone 的 F114 注释）：
     * 状态机推进 → 知识路由注入 → 三层拼装 → 主模型流式 → Done/Failed 事件分发。
     * [pushUserState]=false 时沿用已落库状态（重跑不重复推进用户侧状态）。
     * [replaceMessageId] 非空即重跑替换：handleDone 新 ASSISTANT 落库成功后删旧行、
     * history 映射过滤被替换旧回答，card/done 帧带新落库 messageId；null=普通发送。
     */
    private suspend fun runTextPipeline(
        session: SessionEntity,
        resolved: ResolvedClient,
        text: String,
        modelId: Long,
        imageDataUrls: List<String> = emptyList(),
        replaceMessageId: Long? = null,
        sourceOverride: String? = null,
        transcriptionMode: Boolean = false,
        pushUserState: Boolean = true,
        onEvent: (JSONObject) -> Unit,
    ) {
        val sessionId = session.id
        // 状态机推进（同题判定 + 状态前缀；重跑沿用已落库状态）
        val previousState = ConversationState.fromJson(session.stateJson)
        val wasNewTopic = pushUserState && previousState.hasActiveTopic && !stateTracker.isSameTopic(previousState, text)
        val state = if (pushUserState) {
            stateTracker.onUserInput(previousState, text).also {
                service.updateSessionState(sessionId, it.toJson())
            }
        } else {
            previousState
        }
        val statePrefix = stateTracker.buildStatePrefix(state)

        // 知识路由 + 三层拼装
        val (knowledge, refDocs) = knowledgeEngine.buildInjection(text, routeClassifier(modelId))
        val profile = service.getLatestProfile()
        val target = resolveTargetWithMemory(session.targetId)
        val system = promptBuilder.buildSystem(profile, target, knowledge)
        // 重跑替换：history 映射过滤被替换旧回答（旧行删除前在库，须排除，否则上下文里出现双回答）
        val history = buildHistory(sessionId, text, excludeMessageId = replaceMessageId)
        // v1.8.2-fix（审查 P1-1）：纯图（无配文，前端以 [图片] 占位发送）走固定分析指令，
        // 对齐手机版 runVisionDirect——否则 "[图片]" 会当普通短句进 buildUserReply 模板。
        // 重跑带图轮次同样视为有图（imageDataUrls 非空即走固定指令，不看 caption）。
        val caption = text.trim()
        val isPureImage = imageDataUrls.isNotEmpty() &&
            (caption.isEmpty() || caption == IMAGE_PLACEHOLDER)
        val user = when {
            transcriptionMode -> promptBuilder.buildUserTranscription(text)
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
                is LlmEvent.Restart -> {
                    // H1: 重试重发。桌面主链路不本地累积增量（Delta 不入 UI），无需清空状态。
                }
                is LlmEvent.Done -> handleDone(
                    sessionId = sessionId,
                    resolved = resolved,
                    state = state,
                    wasNewTopic = wasNewTopic,
                    targetId = session.targetId,
                    replaceMessageId = replaceMessageId,
                    pushUserState = pushUserState,
                    source = sourceOverride ?: if (routeByInputShape(text) == InputShape.CHAT_LOG) {
                        MemoryFactEntity.SOURCE_PASTE
                    } else {
                        MemoryFactEntity.SOURCE_CHAT
                    },
                    userText = text,
                    fullText = event.fullText,
                    refDocs = refDocs,
                    onEvent = onEvent,
                )
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
                // H1: 重试前清空已累积转述，避免与重试后新流重复拼接
                is LlmEvent.Restart -> transcription.clear()
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

        // M5 修复（双端）：转述通道第二步同样执行危机预检——原仅 sendMessage 入口有
        // CrisisDetector 硬短路，截图里的危机表述（遗书/割腕等）经视觉模型转述后
        // 直送主模型分析。落库前检测并走安全卡片（不落库、不调 LLM），与手机端一致。
        val crisisHits = com.wenyan.app.knowledge.CrisisDetector.detect(transcription)
        if (crisisHits.isNotEmpty()) {
            onEvent(JSONObject().put("type", "card").put("card", parseSafety(crisisHits.first()).toJson()))
            onEvent(JSONObject().put("type", "done"))
            return
        }

        service.addMessage(sessionId, "USER", "transcription", transcription)

        // 状态机推进与主分析走共用链路（F121 提炼：与 sendMessage 原各持约 55 行逐字重复）
        runTextPipeline(
            session = session,
            resolved = resolved,
            text = transcription,
            modelId = modelId,
            sourceOverride = MemoryFactEntity.SOURCE_TRANSCRIPTION,
            transcriptionMode = true,
            onEvent = onEvent,
        )
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

    /**
     * LlmEvent.Done 处理（F114 提炼：sendMessage 与 confirmTranscription 原各持约 55 行逐字
     * 重复的 Done 块——此类双份维护已造成过 L2 漂移）：
     * 解析落库 → refs 回填 → 状态回填 → card 事件 → 标题降级 → 记忆提炼 → done 帧。
     * [source] 由调用方决定：文本链路按 routeByInputShape 判定，转述链路固定 SOURCE_TRANSCRIPTION。
     *
     * 完全重答替换（[replaceMessageId] 非空）：解析成功后**原位更新旧行**（内容+类型，
     * id/顺序不变——无新旧双卡瞬态、取消无半态、二次重跑不漂移）；失败/取消/解析失败
     * 不写库，前端保留旧卡。card/done 帧带该行 messageId。记忆提炼加 pushUserState
     * 守门（与 Android persistUser 守门对齐：仅首轮 persistUser=true 提炼，重跑不重复写 facts）。
     */
    private suspend fun handleDone(
        sessionId: Long,
        resolved: ResolvedClient,
        state: ConversationState,
        wasNewTopic: Boolean,
        targetId: Long?,
        replaceMessageId: Long? = null,
        pushUserState: Boolean = true,
        source: String,
        userText: String,
        fullText: String,
        refDocs: List<String>,
        onEvent: (JSONObject) -> Unit,
    ) {
        val analysis = runCatching { AnalysisParser.parseAny(fullText) }.getOrNull()
        var msgId: Long? = null
        if (analysis != null) {
            // v1.9.5 完全重答替换：原位更新旧行（极端：旧行已被删 updated==0 → 退回尾部新增）
            msgId = if (replaceMessageId != null) {
                val updated = service.updateMessageContent(replaceMessageId, fullText, "analysis")
                if (updated > 0) replaceMessageId else service.addMessage(sessionId, "ASSISTANT", "analysis", fullText)
            } else {
                service.addMessage(sessionId, "ASSISTANT", "analysis", fullText)
            }
            val refs = refDocs.ifEmpty { analysis.citations }
            if (refs.isNotEmpty()) service.updateSessionRefDocs(sessionId, JSONArray(refs).toString())
            // 状态回填：结论=advice.core（空则 empathy 首句），话术=reply
            val newState = stateTracker.onModelReply(
                state = state,
                topicSummary = if (wasNewTopic || !state.hasActiveTopic) {
                    summarizeTopic(userText, analysis)
                } else {
                    state.topicSummary
                },
                conclusion = summarizeConclusion(analysis),
                reply = analysis.reply,
            )
            service.updateSessionState(sessionId, newState.toJson())
            val cardFrame = JSONObject().put("type", "card")
                .put("card", analysis.toJson().put("citations", JSONArray(refs)))
            // 落库失败（id<=0）不带 id，前端回退整页刷新，不绑定 0
            if (msgId != null && msgId > 0) cardFrame.put("messageId", msgId)
            onEvent(cardFrame)
            // O5: 主回复顺带产出标题则直接落库，否则走独立标题生成降级
            if (analysis.sessionTitle.isNotBlank()) {
                if (service.getSession(sessionId)?.title?.isNotBlank() != true) {
                    service.updateSessionTitle(sessionId, analysis.sessionTitle.trim().take(20))
                }
            } else {
                sideEffectScope.launch { generateTitleOnce(sessionId, userText, fullText, resolved) }
            }
            // 记忆提炼 pushUserState 守门（对齐 Android persistUser 守门：仅首轮提炼，
            // 重跑 pushUserState=false 不写，避免重跑重复写 facts）
            if (pushUserState && targetId != null && shouldExtractMemory(state, userText)) {
                // O5: 主回复顺带产出新事实则直接落库，否则走独立记忆提炼降级
                if (analysis.newFacts.isNotEmpty()) {
                    val facts = analysis.newFacts.map {
                        MemoryExtractor.ExtractedFact(it.text, it.kind, it.expiresIn)
                    }
                    sideEffectScope.launch { persistFacts(targetId, facts, source, userText) }
                } else {
                    sideEffectScope.launch { extractMemoryOnce(targetId, userText, fullText, resolved, source) }
                }
            }
        } else {
            // H3/M2: 解析失败不丢内容——原始回复以 freetext 落库（与手机版一致），并报错提示；
            // v1.9.5 重跑替换：不落任何新行（失败行会与旧卡并存），报错说明已保留原回答
            if (replaceMessageId == null) {
                service.addMessage(sessionId, "ASSISTANT", "freetext", fullText)
                emitError(onEvent, LlmErrorCode.PARSE_ERROR.name, LlmErrorCode.PARSE_ERROR.userMessage)
            } else {
                emitError(onEvent, LlmErrorCode.PARSE_ERROR.name, "重新生成失败：模型返回的内容无法解析，已保留原回答")
            }
            return
        }
        val doneFrame = JSONObject().put("type", "done")
        // 重跑替换：done 带该行真实落库 id（与 card 帧同源），前端原位换卡并绑定
        if (msgId != null && msgId > 0) {
            doneFrame.put("messageId", msgId)
        }
        onEvent(doneFrame)
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
            .filter { fact -> val expires = fact.expiresAt; expires == null || expires > now }
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
            // L2 修复：以清洗空白后的数量 drop（库里有空白事实即错位跳过新事实，双端同修）
            val toAdd = MemoryExtractor.mergeFacts(existing, segments).drop(existing.count { it.isNotBlank() })
            toAdd.forEach { service.addFact(targetId, it) }
            service.clearTargetNote(targetId)
        }
    }

    /**
     * 历史构造（移植 buildHistory）：取会话全部消息映射 role，剔除末尾重复 USER，
     * 超长（字符/4 > 24000 token）预算选择式压缩（v1.9.1，与手机版一致）：
     * 先对早期消息裁剪保头（每条 ≤200 字 + 截断标记，末尾 6 轮工作集完整），仍超再从最早成对丢弃。
     * [excludeMessageId] 非空时过滤该条（重跑替换：旧回答删除前在库，须排除，否则上下文双回答）。
     */
    private suspend fun buildHistory(
        sessionId: Long,
        currentText: String,
        excludeMessageId: Long? = null,
    ): List<ChatHistoryMessage> {
        val messages = service.listMessages(sessionId)
            .filter { excludeMessageId == null || it.id != excludeMessageId }
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
        // 剔除末尾与本轮重复的 USER（本轮已落库，user 模板会再带一次）。
        // L15 修复：transcription 类型映射后是「[截图转述] 前缀 + 全文」，原去重只匹配
        // content == currentText → 转述确认链路去重失效，同一次请求注入两次。
        if (mapped.isNotEmpty() && mapped.last().role == "user" &&
            (mapped.last().content == currentText || mapped.last().content == "[截图转述] $currentText")
        ) {
            mapped.removeAt(mapped.size - 1)
        }
        // v1.9.1 预算选择式压缩（共享 HistoryCompactor：先裁剪早期消息保头，仍超再从最早成对丢弃）
        val (compacted, truncated) = HistoryCompactor.compact(mapped)
        // M2: 与手机版对齐——发生整条丢弃截断时，头部插入仅模型可见的省略提示
        if (truncated) {
            val result = mutableListOf(ChatHistoryMessage("user", "[注：更早的对话已省略，关键信息已保留摘要]"))
            result.addAll(compacted)
            return result
        }
        return compacted
    }

    private fun shouldExtractMemory(state: ConversationState, text: String): Boolean =
        ChatOrchestrator.shouldExtractMemory(
            state = state,
            userInput = text,
            memoryAutoEnabled = service.isMemoryAutoEnabled(),
            hasTarget = true,
        )

    private fun summarizeTopic(text: String, analysis: CoachAnalysis): String =
        ChatOrchestrator.summarizeTopic(text, analysis)

    private fun summarizeConclusion(analysis: CoachAnalysis): String =
        ChatOrchestrator.summarizeConclusion(analysis)

    private fun parseSafety(hit: String): CoachAnalysis = AnalysisParser.parseAny(
        // L1 修复：hit 用 JSONObject 构造并转义（原 $hit 直接插值，词表含引号即碎；与手机端同修）
        JSONObject()
            .put("input_kind", "user_question")
            .put("empathy", "")
            .put("reply", "")
            .put("reply_timing", "")
            .put(
                "facts", JSONObject()
                    .put("known", JSONArray())
                    .put("assumed", JSONArray())
                    .put("unknown", JSONArray())
            )
            .put(
                "advice", JSONObject()
                    .put("tag", "")
                    .put("core", "")
                    .put("reasons", JSONArray())
                    .put("styles", JSONArray())
            )
            .put("actions", JSONArray())
            .put("citations", JSONArray())
            .put("safety_override", true)
            .put(
                "safety_message",
                "检测到可能涉及安全风险的表述（${hit}）。请优先确保自己的人身安全：" +
                    "离开危险环境，联系可信的人或当地紧急服务。我们无法在危机中提供恋爱建议。",
            )
            .put("token_estimate", 0)
            .toString()
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
            persistFacts(targetId, MemoryExtractor.parseFacts(json), source, userText)
        }
    }

    /** O5: 主回复已顺带产出新事实，直接落库（不走独立 LLM 提炼）；也供 extractMemoryOnce 复用 */
    private suspend fun persistFacts(targetId: Long, facts: List<MemoryExtractor.ExtractedFact>, source: String, userText: String) {
        if (facts.isEmpty()) return
        val existingFacts = service.listFacts(targetId).map { it.text }
        val merged = MemoryExtractor.mergeFacts(existingFacts, facts.map { it.text })
        // L2 修复（persistFacts 漏修补齐，同 migrateNoteToFactsOnce/安卓 RealChatRepository）：
        // mergeFacts 会先 trim+filter 清洗空白再合并，按原始 size drop 会把新事实错位跳过
        val toAdd = merged.drop(existingFacts.count { it.isNotBlank() })
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

    private fun emitError(onEvent: (JSONObject) -> Unit, code: String, message: String) {
        onEvent(JSONObject().put("type", "error").put("code", code).put("message", message))
    }

    companion object {
        /** 纯图发送时前端占位文本（与手机版 IMAGE_PLACEHOLDER 一致；不落库为 text 消息） */
        private const val IMAGE_PLACEHOLDER = "[图片]"

        /** 重跑图片轮次单次带图上限（与 ApiRoutes 图片上传上限 MAX_IMAGES_PER_REQUEST 对齐） */
        private const val MAX_IMAGES = 10
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
