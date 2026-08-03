package com.goutoujunshi.app.container

import android.content.Context
import android.net.Uri
import com.goutoujunshi.app.data.datastore.SettingsRepository as DataStoreSettings
import com.goutoujunshi.app.data.image.ImageCompressor
import com.goutoujunshi.app.data.repository.ConversationRepository
import com.goutoujunshi.app.data.repository.ProfileRepository
import com.goutoujunshi.app.data.repository.ProviderRepository
import com.goutoujunshi.app.knowledge.CrisisDetector
import com.goutoujunshi.app.knowledge.KnowledgeEngine
import com.goutoujunshi.app.llm.AnalysisParser
import com.goutoujunshi.app.llm.ChatRequest
import com.goutoujunshi.app.llm.LlmClient
import com.goutoujunshi.app.llm.LlmEvent
import com.goutoujunshi.app.prompt.PromptBuilder
import com.goutoujunshi.app.ui.contract.AnalysisMode
import com.goutoujunshi.app.ui.contract.ChatMessageUi
import com.goutoujunshi.app.ui.contract.ChatRepository
import com.goutoujunshi.app.ui.contract.LlmError
import com.goutoujunshi.app.ui.contract.StreamEvent
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

    private val currentModelId = dataStore.currentModelId
    private val visionModelId = dataStore.visionModelId

    override val messages: Flow<List<ChatMessageUi>> =
        sessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else conversationRepository.observeMessages(id).map { list ->
                list.map { UiMappers.toChatMessage(it) }
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

        val (knowledge, refDocs) = knowledgeEngine.buildInjection(text)
        val profile = profileRepository.getProfile()
        val target = profileRepository.getTarget()
        val system = promptBuilder.buildSystem(profile, target, knowledge)
        val user = if (mode == AnalysisMode.REPLY) {
            promptBuilder.buildUserReply(text, null)
        } else {
            promptBuilder.buildUserText(text)
        }

        val client = resolveClient() ?: run {
            emit(StreamEvent.Error(noConfigError()))
            return@flow
        }

        client.client.stream(ChatRequest(client.model, system, user)).collect { event ->
            when (event) {
                is LlmEvent.Delta -> emit(StreamEvent.Delta(event.text))
                is LlmEvent.Done -> {
                    val analysis = runCatching { AnalysisParser.parse(event.fullText) }.getOrNull()
                    if (analysis != null) {
                        conversationRepository.addMessage(sid, "ASSISTANT", "analysis", event.fullText)
                        val card = UiMappers.toAnalysisCard(analysis)
                        emit(StreamEvent.Analysis(card.copy(citations = refDocs.ifEmpty { card.citations })))
                    }
                    emit(StreamEvent.Done)
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

        val client = resolveClient() ?: run {
            emit(StreamEvent.Error(noConfigError()))
            return@flow
        }
        client.client.stream(ChatRequest(client.model, system, user)).collect { event ->
            when (event) {
                is LlmEvent.Delta -> emit(StreamEvent.Delta(event.text))
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

    override fun cancel() {
        // 流取消由 collect 侧 job 取消触发；MVP 由 ViewModel.stop() 处理
    }

    // ===== 私有辅助 =====

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
            )
        ).collect { event ->
            when (event) {
                is LlmEvent.Delta -> emit(StreamEvent.Delta(event.text))
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

    private data class ResolvedClient(val model: String, val client: LlmClient)
}
