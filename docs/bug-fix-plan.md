# wenyan 项目 Bug 修复方案（对应 docs/bug-report.md）

> 对应报告：`docs/bug-report.md`（65 项：高危 5 / 中危 27 / 低危 33）。
> 本方案给出每一项的具体改法；高危与核心中危附可直接落地的代码，低危给一行式修法。
> 实施顺序按报告 §5 优先级：高危 → 核心共病中危 → 数据安全类 → 桌面安全三项 → 低危顺手清理。

---

## 一、高危（5 项，必须先修）

### H1 🖥️ CSRF Host 校验前缀匹配绕过（ApiRoutes.kt:110-119）

**问题**：`host.startsWith("127.0.0.1")` 对 `127.0.0.1.evil.com:18923` 为 true，DNS 重绑定下攻击页可拿到 CSRF token 后任意写。

**修法**：剥离端口后与白名单**全等**比较。注意 IPv6 `[::1]:port` 的括号形式：

```kotlin
// ApiRoutes.kt 顶部
private val ALLOWED_HOSTS = setOf("127.0.0.1", "localhost", "::1")

/** "127.0.0.1:18923" → "127.0.0.1"；"[::1]:18923" → "::1" */
private fun hostName(hostHeader: String): String {
    val h = hostHeader.trim().lowercase()
    return if (h.startsWith("[")) h.substringBefore(']').removePrefix("[")
    else h.substringBefore(':')
}

// 拦截器内替换 hostOk 判定：
val host = call.request.headers["Host"] ?: ""
val hostOk = host.isBlank() || hostName(host) in ALLOWED_HOSTS
```

> 同时完成 **M1**：把 Host 校验从「仅写请求」提升到**全部 /api 请求**（含 `/api/bootstrap`、`/api/export`、`/api/search`）——合法前端本身就跑在 127.0.0.1:port，Host 校验恒过；DNS 重绑定下攻击页 Host 为攻击域名，读接口一并封死。token 校验仍仅对写请求。

---

### H2 📱 新建提供商重复插入脏数据（ProviderEditViewModel.kt:121,204,217,261）

**问题**：`isNew` 是构造期常量永不翻转；测试连接/添加模型×2/保存四条路径各自 `saveProvider()` 插新行 → 一次流程最多插 3 条相同提供商。

**修法**：引入 `persistedId`，首存后记住 id，后续全部走 update。四条路径收敛到一个函数：

```kotlin
// ProviderEditViewModel
/** H2: 首次落库后记住返回 id；null = 尚未落库（新建） */
private var persistedId: Long? = null

private val effectiveId: Long get() = persistedId ?: providerId   // 预设/编辑页直接用原 id

/** 新建路径：首存返回 id 并记忆；之后一律 update */
private suspend fun ensurePersisted(): Long {
    persistedId?.let { id ->
        repo.updateProvider(id, name, baseUrl, apiKeyToPersist())
        return id
    }
    val id = repo.saveProvider(name.ifBlank { "未命名服务" }, baseUrl, apiKey, isPreset = false)
    persistedId = id
    return id
}
```

四条路径统一改写：

```kotlin
// doTestConnection()（原 121-125 行）
val id = if (isNew) ensurePersisted() else { repo.updateProvider(providerId, name, baseUrl, apiKeyToPersist()); providerId }

// addModel() 两处（原 204-206、217-219 行）
val id = if (isNew) ensurePersisted() else providerId
repo.addModel(id, ...)

// save()（原 261-265 行）
val id = if (isNew) ensurePersisted() else { repo.updateProvider(providerId, name, baseUrl, apiKeyToPersist()); providerId }
```

删除路径同步兜底：`if (!isNew) repo.deleteProvider(providerId) else persistedId?.let { repo.deleteProvider(it) }`。
**回归验证**：「填 Key → 测试 → 加模型 → 保存」全程 DB 中该提供商只有 1 行；「测试后直接返回」不留孤儿（可在 `onCleared()` 中对 `persistedId != null && !savedConfirmed` 的半成品做清理，或接受单行孤儿但不再翻倍）。

---

### H3 📱 档案编辑表单被流式重置清空（MemoryEditViewModel.kt:78-84）

**问题**：`targets` 流（combine 三流）在事实增删时重发射，`collectLatest` 每次都 `loadFields()` 用库值覆盖未保存的 name/mbti/score/relationStatus/timeline。

**修法**：只做一次字段加载，后续发射只更新只读展示字段：

```kotlin
private var fieldsLoaded = false

viewModelScope.launch {
    repo.targets.collectLatest { list ->
        target = list.firstOrNull { it.id == targetId }
        if (!fieldsLoaded && target != null) {
            loadFields()          // 仅首次用库值填充表单
            fieldsLoaded = true
        }
        loading = false
    }
}
```

保存成功后如需刷新，可显式调用 `loadFields()`（此时用户意图就是「以库为准」）。

---

### H4 📱 发送后快速切会话，消息写入错误会话（RealChatRepository.kt:300-320,551-561）

**问题**：`analyzeImagesFlow` 先压缩最多 10 张图（秒级窗口），之后才 `ensureSession()` 读共享 `MutableStateFlow<Long?>`；期间切会话 → 消息落错会话。

**修法**：发送入口**先快照 sessionId**，全程作为参数传递；落库前校验未变更，变更即中止：

```kotlin
override fun analyzeImagesFlow(...): Flow<StreamEvent> = flow {
    val sid = ensureSession()                       // ① 发送开始即锁定会话（压缩之前）
    // ……图片压缩（耗时操作）……
    if (sessionId.value != sid) {                   // ② 期间切会话 → 中止，不写库
        emit(StreamEvent.Error(LlmError("SESSION_CHANGED", "会话已切换，本次发送已取消", retryable = false)))
        return@flow
    }
    persistUserAndAnalyze(sid, ...)                 // ③ 后续 addMessage / 提炼记忆全部用 sid 参数
}
```

配套要求：`sendTextFlow` 同样入口快照；`resolveTargetWithMemory`、记忆提炼、`addMessage` 的调用点全部改为显式 `sid` 参数，**链路内禁止再读 `sessionId.value`**。`ensureSession()` 保持原语义（仅入口调用一次）。

---

### H5 📱 转述卡跨会话渲染与写入（ChatScreen.kt:425-435 + RealChatRepository.kt:371-373）

**问题**：transcription 是全局流式状态，跨会话渲染；确认时 `confirmTranscription` 里 `ensureSession()` 取**当前**会话落库 → A 会话的转述写进 B 会话。

**修法**（两步，与 M18 共用基建）：

1. `StreamingState` 增加 `sessionId: Long?` 字段；`transcribeImagesFlow` 开始时把当前 sid 写入状态，产出 Transcription 事件时携带 sid：

```kotlin
data class StreamingState(
    ...,
    val sessionId: Long? = null,   // H5/M18: 流式状态归属会话
)
```

2. UI 侧（ChatScreen.kt:425-435）只在 `state.sessionId == currentSessionId` 时渲染确认卡；
3. 确认接口带来源会话：

```kotlin
override fun confirmTranscription(transcription: String, sid: Long): Flow<StreamEvent> = flow {
    // 原: val sid = ensureSession()  ← 删除，改用参数，杜绝落错会话
    conversationRepository.addMessage(sid, "USER", "transcription", transcription)
    ...
}
```

调用方从确认卡持有的 state 里取 `sessionId` 传入。

---

## 二、中危（27 项）

### 2.1 安全 / 桌面服务

**M1（GET 无 Host 校验）**：随 H1 一并修——拦截器改为「全部 /api 请求校验 Host；写请求额外校验 token」。

**M2（SSE 生产者裸作用域 + runBlocking 占死 IO 线程）**：生产者改挂在 **call 所属作用域**（Ktor 调用结束时自动取消），写入失败即取消内部流：

```kotlin
// 原: CoroutineScope(Dispatchers.IO).launch { ... runBlocking { channel.writeStringUtf8(...) } }
// 改（在 route handler 内）：
val producerJob = launch {                       // 继承 call 作用域，客户端断开 → 随调用取消
    innerStream.collect { chunk ->
        try {
            channel.writeStringUtf8(chunk); channel.flush()
        } catch (e: Exception) {                 // 写失败 = 对端已断
            cancel()                             // 取消 collect → 内部 LLM 流随之取消，停止烧 token
        }
    }
}
```

内部 LLM flow 需要保证取消可传播（`collect` 被 cancel 即停止上游请求/流读取）。

**M3（导入无事务）**：

```kotlin
// WenyanService.importAllJson
db.withTransaction {          // androidx.room.withTransaction
    clearAllTablesForRestore()   // 原清空逻辑
    // 原逐表 insert 逻辑
}
```

任一记录抛异常 → 整体回滚，原数据不动。

**M4（上传先全量读内存再查上限）**：边读边累计，超限立即中断：

```kotlin
val bytes = part.provider().inputStream().use { input ->
    val buf = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(chunk); if (n < 0) break
        total += n
        if (total > MAX_UPLOAD_TOTAL_BYTES) {
            buf.reset(); break                       // 或直接 respond 413 并 return
        }
        buf.write(chunk, 0, n)
    }
    buf.toByteArray()
}
if (total > MAX_UPLOAD_TOTAL_BYTES) return@post call.respond(HttpStatusCode.PayloadTooLarge, ...)
```

### 2.2 共病（shared 层，改一处双端生效）

**M5（confirmTranscription 缺危机预检，双端）**：在 Android `RealChatRepository.kt:371` 与桌面 `ChatEngine.kt:271-301` 的转述确认入口，落库前加与 sendMessage 相同的预检：

```kotlin
val hits = CrisisDetector.detect(transcription)
if (hits.isNotEmpty()) {
    emit(StreamEvent.SafetyCard(hits))     // 与 sendMessage 命中危机时同一张安全卡
    return@flow                             // 不落库、不调主模型
}
```

**M6（自动记忆提炼首轮后停摆）**：`ConversationStateTracker.isSameTopic` 补「换题开场」判定，并让长输入无共享词时判新题：

```kotlin
private val NEW_TOPIC_PATTERN = Regex("^(换个?话题|聊点?别的|说点?(别的|其他)|不聊这个了|对了[，,])")

fun isSameTopic(state: ConversationState, userInput: String): Boolean {
    if (!state.hasActiveTopic) return false
    val t = userInput.trim()
    if (t.isEmpty()) return true
    if (NEW_TOPIC_PATTERN.containsMatchIn(t)) return false            // ① 显式换题开场
    if (t.contains('\n') && (t.contains("：") || t.contains(":"))) return false
    if (FOLLOW_UP_PATTERN.containsMatchIn(t)) return true
    // ② 长输入（≥30 字，非粘贴记录）且与话题摘要无共享实义词 → 判新题
    if (t.length >= 30 && !sharesKeyword(state.topicSummary, t)) return false
    return true
}
```

更彻底的方案：让记忆提炼的结构化输出回写 `topic_changed` 字段并更新 `topicSummary`（报告已建议），两者可并行——先上启发式止血，模型回写做二期。补测试：`ConversationStateTrackerTest` 覆盖「换题开场 / 长输入新题 / 短追问同题」。

**M7（org.json 平台分歧，显式 null → "null"）**：在 `json/` 平台包装层统一加 `isNull` 预检，Android 端所有 `optString(key, fallback)` 调用点改走包装函数：

```kotlin
// json/Json.android.kt（commonMain 放 expect，两端 actual 同实现）
fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, null)
```

调用点（事实解析、expires_in 等契约字段）全部替换；`"null"` 垃圾事实不再入库。

**M8（反义判定纯 contains）**：`MemoryConflictDetector` 命中前剥除否定前缀（`不|没|没有|别|无|非`），`sharedCore` 过滤停用词（她说/他说/我/你…）且公共窗口长度阈值提到 ≥3 字。

**M9（SseParser 强类型取值抛异常冲出监听器）**：`SseParser.kt:46-73` 的 `getJSONObject/getString` 全部改 `optJSONObject/optString/isNull` 防御取值；任何缺失/类型不符按 `parseError` 处理（维持「非法 chunk = 不可重试 PARSE_ERROR」契约）。用 `LlmClientTest` 骨架补用例：`"error":null`、error 为字符串、content 为数组。

**M10（流干净关闭无 finish_reason → 永久「思考中」）**：`LlmClient.kt` listener 覆写 `onClosed`：

```kotlin
override fun onClosed(eventSource: EventSource) {
    // 服务端 200 后干净关流且从未发 finish_reason → 收尾，避免 flow 永不 close
    if (!settled) settle(SingleResult.Retryable(LlmErrorCode.EMPTY_CONTENT, "stream closed without finish_reason"))
}
```

**M11（KnowledgeEngine.docCache 并发竞争）**：`docCache` 访问全部包进 `Mutex`（commonMain 可用）：

```kotlin
private val cacheMutex = Mutex()
suspend fun cachedDoc(id: String): Doc? = cacheMutex.withLock {
    docCache.get(id) ?: loadAndPut(id)      // get 也须在锁内（access-order 写结构）
}
```

**M12（extractKeywords 全是 4-gram）**：按长度配额交错选取，例如 4/3/2-gram 各取 4 个（12 = 4+4+4），或按「短词优先、同长度内保序」轮转选取；保证 ≥15 字输入时 2/3 字词可入选。

**M13（HybridRouter/RouteReranker 单字否定误判）**：删除单字「不」「没」判定，只保留多字否定短语（`不是|没有|并不|根本不|从不|再也不会`…）；`RouteReranker.kt:24,59-60` 同步改。补离线评测对照（「不错」「要不要」不再被过滤/罚分）。

**M14（CrisisDetector 高频词硬匹配误报）**：把「被打」「报复」「受不了」从 `phrases` 移入 `compoundKeywords` 白名单：

```kotlin
// phrases 中删除: "被打", "报复", "受不了"
// compoundKeywords 增加：
"被打" to listOf("被打哭", "被打伤", "被打得", "被打进医院", "天天被打"),
"报复" to listOf("报复我", "报复他", "报复你", "要报复"),
"受不了" to listOf("受不了了想死", "受不了想结束", "真的受不了想"),
```

在 `CrisisDetectorBoundaryTest` 补边界用例：「我不想被打扰」「报复性熬夜」「他太吵了我受不了」不命中；「被打哭」「他要报复我」命中。

### 2.3 Android 数据层

**M15（cancel() 状态复位不完整）**：

```kotlin
override fun cancel() {
    streamJob?.cancel(); streamJob = null
    _streamingState.update {
        it.copy(streaming = false, transcribing = false, error = null)   // M15: 三项一并复位
    }
}
```

错误卡「取消」按钮随之生效（错误码已清，状态机无残留）。

**M16（恢复备份 clearAll 连带清设置）**：`RealSettingsRepository.kt:216-218` 把 `dataStore.clearAll()` 替换为只清失效 id 槽位：

```kotlin
if (ok) {
    // M16: 仅清指向旧库行的 id 槽位；onboarding/privacy/theme 等本地设置保留
    dataStore.setCurrentModelId(null)
    dataStore.setVisionModelId(null)
    dataStore.setActiveTargetId(null)
    AppLogger.i("backup_restore_ok")
}
```

（DataStore 侧若无 setter 支持 null，加 `remove(key)` 三个方法。）恢复后激活档案为空是**预期**——备份里有 targets，用户在记忆页重新点选即可；自动记忆在未选档案前不静默失效报错。

**M17（发送链路无异常兜底）**：`RealChatRepository.kt:99` appScope 挂 handler：

```kotlin
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default +
    CoroutineExceptionHandler { _, e ->
        Log.e("RealChatRepository", "uncaught in appScope", e)
        AppLogger.e("app_scope_uncaught", "msg" to (e.message ?: e.javaClass.simpleName))
        _streamingState.update { it.copy(streaming = false, transcribing = false, error = LlmError("INTERNAL", "内部错误，请重试", retryable = true)) }
    })
```

### 2.4 聊天 UI

**M18**：与 H5 共用 `StreamingState.sessionId` 基建。切会后：新会话不再假「思考中」（状态不匹配即视为空闲）；`_streaming` 守卫改为按 sessionId 判断；stop 只取消 `state.sessionId == 当前会话` 的 job。

**M19（ImagePreviewOverlay/MessageBubble 捕获首帧 viewport=Zero）**：`drawn` 改为 `onGloballyPositioned { if (it.size != IntSize.Zero) drawn = it.size }` 更新（或 `pointerInput(viewport)` 以非零 viewport 为 key 重建），clamp 上限随之修正。

**M20（会话切换不复位 LazyListState）**：`ChatScreen` 的 LazyColumn 加 `key(currentSessionId)`（切换即重建列表状态归零）；或 `LaunchedEffect(currentSessionId) { listState.scrollToItem(0) }`，`isAtBottom` 随之恢复自动跟随。

**M21（初始路由用未加载的 onboardingCompleted）**：启动路由决策推迟到 DataStore 首个发射：首帧渲染 splash 占位，`onboardingCompleted` 首值到达后再 `replaceAll`；或用 `runBlocking { dataStore.onboardingCompleted.first() }` 仅限 Application 启动路径（主线程一次小读可接受）。

**M22（memoryReceipt/notice 重放/丢失）**：repo 侧 `MutableStateFlow<String?>` 改 `Channel<Broadcast>(UNLIMITED)` 或 `MutableSharedFlow(replay = 0, extraBufferCapacity = 8)`；UI 侧 `collect`（非 `collectAsState`）消费即走，旋转不重放、同文案不丢。

### 2.5 设置 / 组件

**M23（隐私确认后 Save 丢回调）**：

```kotlin
sealed interface PendingAction {
    data object Test : PendingAction
    data class Save(val onDone: () -> Unit = {}) : PendingAction   // M23: 携带回调
}
```

确认隐私分支里 `when (val a = pendingAction) { is PendingAction.Save -> doSave(a.onDone); ... }`。

**M24（Slider steps 语义差一）**：`SliderField.kt`：

```kotlin
steps = (valueRange.distanceTo / step - 1).toInt().coerceAtLeast(0)   // 0-100/5 → 19 → 落点 21 个 = 5 的倍数
```

（`distanceTo = end - start`；总落点 = steps + 2。）

**M25（关键事件列表实时重排串写）**：编辑态按**原始顺序**渲染（排序仅用于展示完成态），或 LazyColumn + `key(item.id)` 稳定 key；`onValueChange` 捕获的 index 不再漂移。

**M26（providers 流无条件回写覆盖输入）**：init 中 `val first = repo.providers.first()` 取初值回填一次，后续流发射不再写 name/baseUrl（或 `fieldsBackfilled` 标志位，同 H3 模式）。

**M27（保存/加模型绕过 URL 预检）**：`save(onDone)` 与 `addModel()` 入口第一行加 `if (!normalizeOrReject()) return`，非法 Base URL 不落库。

---

## 三、低危（33 项，一行式修法）

### 3.1 共病

| # | 修法 |
|---|------|
| L1 | 危机关键词 JSON 注入点改 `JSONObject.quote(hit)` 或用 org.json 构造（双端两份副本同步改；顺手下沉 shared） |
| L2 | `mergeFacts` 先 `existing.filter { it.isNotBlank() }` 再 `drop`；或改为「按索引对位覆盖 + 追加新增」 |
| L3 | `fromStringList` 包 try/catch 返回 `emptyList()`，与兄弟方法对齐 |
| L4 | `TimelineParser` 排序 key 改 `parseYear*10000 + parseMonth*100 + parseDay`，非格式串排尾（`Int.MAX_VALUE`） |
| L5 | `take(40)` 换用本文件已有 `takeCodePoints` |
| L6 | `stripFence` 改正则 `(?is)```(json|JSON)?\s*(\{.*\})\s*``` ` 提取首个花括号平衡块，围栏前置文字容忍 |
| L7 | QueryVariantRouter 变体合并进原文档语料统计（N/avgdl/df 全局口径），或对变体得分取 max 后归一到文档级 |
| L8 | Bm25Scorer：查询清洗后剩 1 字时降级用单字 token 与文档单字索引匹配（文档侧同样索引单字），无命中再 0 分 |
| L9 | 首块预算 `(maxChars - heading - TRUNCATED_MARK.length - 4)`；同时收紧 KnowledgeChunkerTest 容差 |
| L10 | `classifyFailure` 先判「流中途失败」（已有 delta 输出）再判 response 状态码，归 READ_TIMEOUT |
| L11 | 400 先读 body error 字段细分：`context_length_exceeded`/`maximum context length` → 上下文过长，否则通用「请求参数错误」 |
| L12 | SseParser 支持多行 data：按 `\n` 拆分逐行解析后合并，或对含换行的 data 帧先拼接再 parse |
| L13 | extractJson 改「扫描全部 `{` 起点逐一配平，取第一个能完整配平且能解析的子串」 |
| L14 | `take()` 家族统一替换 shared 层 `takeCodePoints`（SessionTitle、ChatOrchestrator 摘要） |

### 3.2 桌面

| # | 修法 |
|---|------|
| L15 | 转述消息落库类型改 `transcription` 后，`buildHistory` 去重条件同时匹配 transcription 类型；或 user 模板不再嵌全文、只引用「上一条转述」 |
| L16 | 解码用 `ImageReader`/`ImageIO` 降采样参数（subsampling）先小后大；透明图按 alpha 通道选 `TYPE_INT_ARGB` |
| L17 | `reg` 进程：`readText(timeout)` + `waitFor(3, SECONDS)`，超时 `destroyForcibly()` 并抛错（不回退 COMPUTERNAME，密钥漂移比失败更危险） |
| L18 | 路由层包 `runCatching`，解析失败 `respond(BadRequest)`；id 不存在 `respond(NotFound)` |
| L19 | findFreePort 改 `ServerSocket(0)` 探测后**保持打开**传入 Ktor，或捕获 BindException 重试探测（最多 N 次） |
| L20 | PUT targets 的 score 用 `has("score")` 判存在性：存在且 null → 写 null 清空；缺省才保留旧值 |

### 3.3 Android

| # | 修法 |
|---|------|
| L21 | 搜索改 `queryFlow.debounce(300).collectLatest { doSearch(it) }`（或 `searchJob?.cancel()` 后重启） |
| L22 | `undoLastMemoryWrite` 的读取移进 `dataStore.edit { }` 事务内 |
| L23 | PresetSeed 包 `db.withTransaction`；幂等判据改「存在预设标记的 provider（按 preset key 查）」 |
| L24 | Keystore 首次生成包 `synchronized(lock)` 双检：`getOrNull(alias) ?: synchronized { getOrNull(alias) ?: generate() }` |
| L25 | APK 下载先写 `.tmp`，校验通过后 `renameTo` 最终名；异常路径 `tmp.delete()` |
| L26 | MetricsFileStore.save 改「写 `.tmp` → rename」原子替换；UsageMetrics 调用点加同一把锁；`load()` 移出主线程（appScope.launch） |
| L27 | `runCatching` 后补 `onFailure { if (it is CancellationException) throw it }`（四处同改） |
| L28 | profile 插入时回传备份中的 createdAt |
| L29 | 缓冲改 `addFirst`（或 joinToString 前 `reversed()`），KDoc/文件头与实现一致 |
| L30 | 清空 Key：`apiKey.isBlank()` 时显式传空串/删除标记，后端识别为删除；红绿灯按「Key 是否可解密」而非 `isBlank()` |
| L31 | 统一两处关系状态选项集（以 MemoryEditScreen 为准，OnboardingSteps 补齐），或建立共享常量 |
| L32 | `rememberReducedMotion` 改监听 `Settings.Secure.ANIMATOR_DURATION_SCALE`/`TRANSITION_ANIMATION_SCALE` 的 ContentObserver，可重组刷新 |
| L33 | LiquidGlass 实装 `pointerInput` 按压缩放 + `graphicsLayer` 速度参数，或删参数并在 ModelSheet 移除调用 |
| L34 | launcher 注册移出重组路径：`rememberLauncherForActivityResult` 的 contract 参数固定 `PickMultipleVisualMedia(10)`，名额用回调里 `take(remainingSlots)` 截断并提示 |

---

## 四、实施批次建议（PR 划分）

| 批次 | 内容 | 风险 |
|------|------|------|
| PR1 安全 | H1 + M1 + M2 + M4（桌面 API 层） | 低，纯服务端校验/资源管理 |
| PR2 高危数据 | H2 + H3 + M15 + M16 + M23 + M26 + M27（设置域） | 中，涉及表单状态机，需手测全流程 |
| PR3 会话归属 | H4 + H5 + M18 + M20（StreamingState.sessionId 基建一次铺开） | 中高，核心聊天链路，需回归双会话切换场景 |
| PR4 共病核心 | M5 + M6 + M10 + M14 + M9 + M7（shared 层，带测试） | 中，均有测试骨架可扩展 |
| PR5 数据安全 | M3 + M17 + L23 + L24 + L26 + L22 + L25 + L27 | 低，模式统一（事务/锁/原子写/CE 透传） |
| PR6 桌面杂项 | M2 余项 + M3 已含 + L15-L20 | 低 |
| PR7 知识/解析 | M8 + M11 + M12 + M13 + L1-L14 | 低，离线评测可验证 |
| PR8 UI 打磨 | M19 + M21 + M22 + M24 + M25 + M31/L31 + L29/L32/L33/L34 + L21 | 低 |

## 五、回归测试基线

- **已有骨架扩展**：`LlmClientTest`（M9/M10：invalid chunk fatal、onClosed 收尾）、`CrisisDetectorBoundaryTest`（M14：误报边界 + 白名单命中）、`KnowledgeChunkerTest`（L9 收紧容差）。
- **需新增**：`ConversationStateTrackerTest`（M6）、`MemoryConflictDetectorTest`（M8）、`SseParserTest`（M9/L12）、`ApiRoutesHostTest`（H1/M1：`127.0.0.1.evil.com` 403、`[::1]:p` 通过）、ProviderEditViewModel 流程测试（H2：全流程仅 1 行 provider）。
- **手测脚本**（PR2/PR3）：填 Key→测试→加模型→保存（查 DB 行数）；A 会话发送中切 B 会话（查消息归属）；A 会话截图转述后切 B 再确认（查落库会话）；恢复备份后主题/隐私确认不丢、激活档案可重选。

## 六、结构性建议（对应报告 §4）

- 「平行缺陷」四组（备份恢复 / Key 加密 / 危机预检 / mergeFacts）在修复时**顺手把逻辑下沉 shared**：备份恢复的「清槽位」、mergeFacts、危机预检入口统一为一个 `ChatSafetyGate`，双端只留平台 IO 差异。
- shared 层新增代码一律走 `opt*/isNull` 防御取值与 `takeCodePoints`，杜绝 M7/L5/L14 这类平台分歧再发生。


---

## 修复执行记录（全量完成，未封包）

按本计划完成全部 65 项修复的代码落盘。执行要点与偏差说明：

### 编译/测试验证
- `:shared:compileKotlinJvm` / `:shared:compileDebugKotlinAndroid` / `:desktop:compileKotlin` / `:app:compileDebugKotlin` 全部通过
- `:app:testDebugUnitTest`：**457 个测试全部通过**
- `:app:compileDebugUnitTestKotlin` / `:app:compileDebugAndroidTestKotlin` 通过

### 计划外补充修复
- **M5 桌面侧补齐**：ChatEngine.confirmTranscription 原缺危机预检，已对齐手机端（落库前 CrisisDetector 硬短路 → 安全卡片）
- **L18 实施方式调整**：未逐路由包裹 runCatching，改引 `ktor-server-status-pages`（2.3.12）全局把 NumberFormatException / JSONException 映射为 400

### 随修更新的既有测试
- `MemoryExtractorTest`：L6 stripFence 重写后语义不变，原测试通过（实现中曾引入首行围栏清空 bug，已在验证阶段发现并修正）
- `CrisisDetectorBoundaryTest`：M14 后裸「受不了」不再触发（误报治理），断言改为复合词触发 + 裸词不触发
- `HybridRouterTrainTest`：M12/M13 后 hybrid 在留出集小幅反超 contains（F1 ≈0.194 vs ≈0.183），「contains 恒最优」断言改为护栏式（任一路由退化 >0.05 即回归）
- `ErrorMapperFullTest`：L11 新增 BAD_REQUEST 后错误码 14→15
- 测试假件（TestFakes / 各 FakeSettingsRepository）：同步 L30 新增的 `deleteProviderApiKey`、M23 的 Save data class 化等契约变更

### 未做事项（按用户要求）
- 未打包（无 assemble/installDist/dist 等任务执行）、未提交 git
