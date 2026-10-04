package com.wenyan.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenyan.app.data.repository.ProviderUrlNormalizer
import com.wenyan.app.data.security.AesGcmCipher
import com.wenyan.app.llm.LlmErrorCode
import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 测试连接结果三态（design-pages 页面3） */
data class TestResult(
    val ok: Boolean,
    val warn: Boolean,
    val message: String,
)

/** F21：Keystore 不可用兜底文案（KeyUnavailableException.message 缺失时的兜底） */
private const val KEY_UNAVAILABLE_HINT = "密钥不可用，请重新输入 API Key"

/**
 * 提供商编辑状态（AC-09/11）：名称/Host/Key（密文显隐）+ 模型管理 + 测试连接。
 * providerId <= 0 表示新建。
 */
class ProviderEditViewModel(
    private val repo: SettingsRepository,
    private val providerId: Long,
) : ViewModel() {

    val isNew: Boolean = providerId <= 0

    var name by mutableStateOf("")
    var baseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")
    var showKey by mutableStateOf(false)

    /**
     * v1.7.5 编辑模式解密回显的原始 key（供保存时判断是否修改过）：
     * 未修改 → 保存传 null 不重加密；清空 → 传 null 不覆盖（保持原行为）。
     */
    private var originalApiKey: String? = null

    var models by mutableStateOf<List<ModelInfo>>(emptyList())
    var newModelName by mutableStateOf("")

    var testing by mutableStateOf(false)
    var testResult by mutableStateOf<TestResult?>(null)
    var saving by mutableStateOf(false)
    var showDeleteDialog by mutableStateOf(false)
    var showPrivacyDialog by mutableStateOf(false)
    var privacyAck by mutableStateOf(false)
    var pendingAction by mutableStateOf<PendingAction?>(null)

    /**
     * Holder 会话键：新建用稳定键（同页组合重建后复活旧结果/在途任务认领不断）；
     * 编辑用 per-VM-instance UUID（防同 id 连推时旧 release 误删新表项/取消新 job）。
     * 根因：AppRoot Crossfade 只渲染 current，连续 push 同 id 时旧页 onDispose 晚于新页建表，
     * 稳定同键下旧 release 会误删新表项；新建页栈内同时只会有一个，稳定键无此风险。
     * providerId 只进快照不做键（新建 providerId 恒 <=0）。
     */
    val sessionKey: String = if (providerId <= 0) "pe-new" else "pe-" + UUID.randomUUID()

    /** 镜像 Holder.effectiveId，仅作 delete 兜底（TTL 过期仍可清孤儿行） */
    private var lastEffectiveId: Long? = null

    /** 隐私确认后待执行的动作（AC-18：首次保存 Key 前必须确认） */
    sealed interface PendingAction {
        /** M23 修复：携带保存完成回调——原 data object 丢失 onDone，隐私确认后保存成功但不导航 */
        data class Save(val onDone: () -> Unit = {}) : PendingAction
        data object Test : PendingAction

        /** 隐私确认后先存 provider 再续加模型（保存原始添加意图，避免确认后丢失） */
        data class SaveAndAddModel(
            val modelName: String,
        ) : PendingAction
    }

    init {
        viewModelScope.launch {
            repo.privacyAck.collect { privacyAck = it }
        }
        if (!isNew) {
            viewModelScope.launch {
                // M26 修复：仅取初值回填一次——原 collect 每次发射无条件覆盖 name/baseUrl，
                // 进页立刻打字会被迟到的库值冲掉；providers 表任何变更也会覆盖未保存编辑。
                val providers = repo.providers.first()
                providers.firstOrNull { it.id == providerId }?.let { p ->
                    name = p.name
                    baseUrl = p.baseUrl
                }
            }
            viewModelScope.launch {
                // v1.7.5 掩码回显：解密已保存 key 回填输入框（UI 默认 isSecret 掩码，点眼睛才见明文）；
                // 解密失败/无 key → 保持空白；originalApiKey 供保存时判断是否修改
                val decrypted = repo.getProviderApiKey(providerId)
                if (!decrypted.isNullOrBlank()) {
                    originalApiKey = decrypted
                    apiKey = decrypted
                }
            }
            viewModelScope.launch {
                repo.models.collect { list ->
                    models = list.filter { it.providerId == providerId }
                }
            }
        }
        // F51 修复：模型列表收集对新建路径同样生效——原先收集器整体在 if (!isNew) 内，
        // 新建页 vm.models 永远为空：经 Holder persist 已把模型写进库，但「模型管理」列表不显示
        // （观感如同添加失败）。过滤键用当前生效 provider id：新建落库前尚空 → 过滤为空列表；
        // 首存后 Holder 记下 id，下一次 repo.models 发射（含 addModel 触发的）即自动显示该提供商的模型
        if (isNew) {
            viewModelScope.launch {
                repo.models.collect { list ->
                    models = list.filter { it.providerId == currentEffectiveProviderId() }
                }
            }
        }
        // Holder 镜像：testing/testResult/saving/effectiveId 单源归 Holder per-key StateFlow，
        // Screen 结果行（ProviderEditScreen.kt:182-198）读 VM 镜像态，签名不动
        viewModelScope.launch {
            ProviderEditSessions.acquire(sessionKey)
            try {
                val s = ProviderEditSessions.session(sessionKey)
                launch { s.testing.collect { testing = it } }
                launch { s.saving.collect { saving = it } }
                launch { s.effectiveId.collect { lastEffectiveId = it } }
                s.outcome.collect { o ->
                    testResult = when (o) {
                        null -> null
                        ProviderTestOutcome.Success ->
                            TestResult(ok = true, warn = false, message = "连接正常，模型可用")
                        is ProviderTestOutcome.Failed -> errorToResult(o.error)
                        is ProviderTestOutcome.KeyUnavailable ->
                            TestResult(ok = false, warn = true, message = o.message ?: KEY_UNAVAILABLE_HINT)
                    }
                }
            } finally {
                ProviderEditSessions.uncollect(sessionKey)
            }
        }
    }

    /** 当前生效的 provider id：Holder 单源优先，VM 镜像兜底（TTL 过期后仍可清孤儿行） */
    private fun currentEffectiveProviderId(): Long =
        ProviderEditSessions.effectiveIdOf(sessionKey) ?: lastEffectiveId
            ?: if (isNew) -1L else providerId

    /** v1.7.5 保存用 key：未修改（== 原解密值）→ null 不重加密；清空 → null 不覆盖；新值 → 传明文 */
    private fun apiKeyToPersist(): String? =
        apiKey.takeIf { it.isNotBlank() && it != originalApiKey }

    fun toggleKeyVisibility() {
        showKey = !showKey
    }

    fun testConnection() {
        // AC-18：填写了 API Key 但未确认隐私声明 → 先弹确认
        if (apiKey.isNotBlank() && !privacyAck) {
            pendingAction = PendingAction.Test
            showPrivacyDialog = true
            return
        }
        doTestConnection()
    }

    /** 快照冻结（Main 线程，normalize 通过后立即执行；Holder 永不读 VM 活字段） */
    private fun snapshot(): ProviderEditSnapshot = ProviderEditSnapshot(
        name = name,
        baseUrl = baseUrl,
        keyToPersist = apiKeyToPersist(),
        apiKeyBlank = apiKey.isBlank(),
        providerIdArg = providerId,
        isNew = isNew,
    )

    private fun doTestConnection() {
        if (!normalizeOrReject()) return
        // guard 已归 Holder（短 withLock 查置即释，网络锁外）；交快照即可返回，结果经镜像收集
        ProviderEditSessions.runTest(sessionKey, snapshot(), repo)
    }

    /** AC-18：确认隐私声明后执行待办动作并持久化 ack */
    fun acceptPrivacy() {
        showPrivacyDialog = false
        viewModelScope.launch {
            repo.setPrivacyAck(true)
            privacyAck = true
            val action = pendingAction
            when (action) {
                is PendingAction.Save -> doSave(action.onDone)   // M23 修复：用携带的回调
                PendingAction.Test -> doTestConnection()
                is PendingAction.SaveAndAddModel -> doSaveAndAddModel(action.modelName)
                null -> Unit
            }
            pendingAction = null
        }
    }

    fun dismissPrivacy() {
        showPrivacyDialog = false
        pendingAction = null
    }

    /**
     * Base URL 预检（v1.7.x）：规范化并回写输入框；空串 / 含非法字符（逗号/空格）或 query·fragment
     * （F22：`?`/`#` 会让硬拼端点恒 404）→ 显示错误并阻止继续。
     * @return true 表示可继续
     */
    private fun normalizeOrReject(): Boolean {
        val normalized = ProviderUrlNormalizer.normalize(baseUrl)
        if (normalized == null) {
            testResult = TestResult(
                ok = false,
                warn = true,
                message = when {
                    baseUrl.isBlank() -> "Base URL 不能为空，请填写服务地址"
                    else -> "Base URL 包含非法字符（如逗号、空格、?、#），请全选删除后重新输入"
                },
            )
            return false
        }
        if (normalized != baseUrl) baseUrl = normalized
        return true
    }

    /** v1.7.x 测试连接结果分级：code 为 LlmErrorCode 枚举名（此前误按 "401"/"404" 数字匹配导致全落空） */
    private fun errorToResult(err: LlmError): TestResult = when (err.code) {
        LlmErrorCode.UNAUTHORIZED.name -> TestResult(ok = false, warn = false, message = "API Key 无效，请检查")
        LlmErrorCode.FORBIDDEN.name -> TestResult(ok = false, warn = false, message = "服务拒绝访问，请检查账户状态")
        LlmErrorCode.MODEL_NOT_FOUND.name -> TestResult(ok = false, warn = false, message = "模型不存在，请检查模型名（可能已退役）")
        LlmErrorCode.RATE_LIMITED.name -> TestResult(ok = false, warn = true, message = "请求过于频繁或额度已用尽，稍后重试")
        LlmErrorCode.SERVER_ERROR.name -> TestResult(ok = false, warn = true, message = "模型服务异常，请稍后重试")
        LlmErrorCode.CONNECT_TIMEOUT.name, LlmErrorCode.READ_TIMEOUT.name ->
            TestResult(ok = false, warn = true, message = "连接超时，请检查网络或服务地址")
        // 离线弱网发送前预检：连接测试失败归一 NO_NETWORK（warn=true 可重试，文案与枚举同源）
        LlmErrorCode.NO_NETWORK.name ->
            TestResult(ok = false, warn = true, message = LlmErrorCode.NO_NETWORK.userMessage)
        LlmErrorCode.UNSUPPORTED_URL.name ->
            TestResult(ok = false, warn = true, message = "地址不受支持，仅支持 https://；本地服务请填 http://localhost")
        else -> TestResult(ok = false, warn = true, message = err.message.ifBlank { "连接失败" })
    }

    fun addModel() {
        val nameTrim = newModelName.trim()
        if (nameTrim.isEmpty()) return
        // M27 修复：添加模型同样先 URL 预检，防非法 Base URL 随 saveProvider 静默落库
        if (!normalizeOrReject()) return
        // AC-18：填写了 API Key 但未确认隐私声明 → 先弹确认（新建场景），确认后仍续加模型
        if (isNew && apiKey.isNotBlank() && !privacyAck) {
            pendingAction = PendingAction.SaveAndAddModel(nameTrim)
            showPrivacyDialog = true
            return
        }
        doAddModel(nameTrim)
    }

    /** v1.6.3 新增模型默认非视觉（supportsVision=false），需要时在模型行第二行再开"视觉"开关 */
    private fun doAddModel(nameTrim: String) {
        if (!normalizeOrReject()) return
        val snap = snapshot()
        viewModelScope.launch {
            try {
                val id = ProviderEditSessions.ensureEffectiveId(sessionKey, snap, repo)
                repo.addModel(id, nameTrim, supportsVision = false)
                newModelName = ""
            } catch (e: AesGcmCipher.KeyUnavailableException) {
                // F21 修复：加密失败提示到达 UI（Holder persist 落库时加密）
                testResult = TestResult(ok = false, warn = true, message = e.message ?: KEY_UNAVAILABLE_HINT)
            }
        }
    }

    /** 隐私确认后：先保存 provider，再按原意图添加模型（AC-18 意图保留） */
    private fun doSaveAndAddModel(modelName: String) {
        // guard 唯一入口 tryBeginSaving（短 withLock 查置即释）；外层不再预读 saving.value（TOCTOU）
        val snap = snapshot()
        viewModelScope.launch {
            if (!ProviderEditSessions.tryBeginSaving(sessionKey)) return@launch
            try {
                val id = ProviderEditSessions.ensureEffectiveId(sessionKey, snap, repo)
                repo.addModel(id, modelName, supportsVision = false)
                newModelName = ""
            } catch (e: AesGcmCipher.KeyUnavailableException) {
                testResult = TestResult(ok = false, warn = true, message = e.message ?: KEY_UNAVAILABLE_HINT)
            } finally {
                ProviderEditSessions.endSaving(sessionKey)
            }
        }
    }

    fun deleteModel(id: Long) {
        viewModelScope.launch { repo.deleteModel(id) }
    }

    fun setVision(id: Long, supportsVision: Boolean) {
        viewModelScope.launch { repo.setVisionFlag(id, supportsVision) }
    }

    /** v1.6.3 切换模型在主页"选择模型"弹层的可见性（替代原"设为默认"单选） */
    fun toggleSheetVisible(id: Long) {
        viewModelScope.launch { repo.toggleSheetVisible(id) }
    }

    fun requestDelete() {
        showDeleteDialog = true
    }

    fun dismissDelete() {
        showDeleteDialog = false
    }

    fun save(onDone: () -> Unit) {
        // M27 修复：保存入口同样先做 URL 预检（原仅测试连接路径预检，非法 Base URL 静默落库，
        // 之后对话请求拼坏 URL 全部 404 且无提示指向根因）
        if (!normalizeOrReject()) return
        // AC-18：填写了 API Key 但未确认隐私声明 → 先弹确认
        if (apiKey.isNotBlank() && !privacyAck) {
            pendingAction = PendingAction.Save(onDone)   // M23 修复：携带回调
            showPrivacyDialog = true
            return
        }
        doSave(onDone)
    }

    private fun doSave(onDone: () -> Unit = {}) {
        // save 系 guard 已归 Holder（tryBeginSaving/endSaving）；快照主线程冻结
        val snap = snapshot()
        viewModelScope.launch {
            if (!ProviderEditSessions.tryBeginSaving(sessionKey)) return@launch
            try {
                val id = ProviderEditSessions.ensureEffectiveId(sessionKey, snap, repo)
                // L30 修复：编辑页清空 Key = 真删除已存密文——原 apiKeyToPersist 的 null 语义是
                // 「不覆盖」，清空输入框保存后旧 Key 仍在，测试连接继续用旧 Key 绿灯误导用户。
                if (!snap.isNew && snap.apiKeyBlank && originalApiKey != null) {
                    repo.deleteProviderApiKey(id)
                }
                // v1.6.3 保存后立即测试连接并写入红绿灯状态：成功绿灯，失败/未填 Key 红灯
                // （doSave 的删 Key/红绿灯/onDone 留 VM；test/add 走 Holder runTest/ensureEffectiveId）
                if (snap.apiKeyBlank) {
                    repo.markConnectionStatus(id, ok = false)
                } else {
                    val err = repo.testConnection(id)
                    repo.markConnectionStatus(id, ok = err == null)
                }
                onDone()
            } catch (e: AesGcmCipher.KeyUnavailableException) {
                // F21 修复：加密失败停在当前页并提示，不导航回列表（保存未完成）
                testResult = TestResult(ok = false, warn = true, message = e.message ?: KEY_UNAVAILABLE_HINT)
            } finally {
                ProviderEditSessions.endSaving(sessionKey)
            }
        }
    }

    fun deleteProvider(onDone: () -> Unit) {
        viewModelScope.launch {
            if (!isNew) repo.deleteProvider(providerId)
            // H2 修复：新建中途已落库的半成品也一并删除，不留孤儿行；
            // id 来源改为 Holder 单源优先、VM 镜像兜底（TTL 过期仍可清孤儿行）
            else (ProviderEditSessions.effectiveIdOf(sessionKey) ?: lastEffectiveId)?.let { repo.deleteProvider(it) }
            onDone()
        }
    }
}
