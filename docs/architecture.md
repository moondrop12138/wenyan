# 架构文档 - 温言 v1.9.6

> 本文档对应 **v1.9.6（2026-10）**，描述双端（Android + Windows 桌面）共享 KMP 架构现状。
> 原 v1.0「狗头军师（安卓）」设计稿已被本文整体取代（旧稿的规则路由、五步法、单端假设均已演进）。
> **易变数据（预设厂商/模型名单、端口、版本号等）一律以代码为准**，本文只描述结构与机制；版本演进以 `git log` 为准。

---

## 1. 概述

温言是一款**纯本地运行**的恋爱决策支持应用，无自有后端、数据不出本机（BYOK，用户自带任意 OpenAI 兼容服务商的 Key）。一核双端：

- **`:shared`**（Kotlin Multiplatform，commonMain）：llm / domain / prompt / knowledge / data 纯逻辑，两端共用
- **`:app`**（Android）：Jetpack Compose UI + Android 平台接缝
- **`:desktop`**（Windows）：Ktor CIO 本地服务 + 纯静态 HTML/CSS/JS 前端，jpackage 打包为 exe

两端行为一致、单点维护：四段式回答、跨会话记忆档案、多模型 BYOK、截图双通道、知识路由、逐段渐显全部走同一份共享逻辑。

## 2. 模块结构

Gradle 工程根在 `app/` 子目录（git 仓库根为上级目录），4 个模块：

```
app/
├── shared/                   # KMP 共享模块（commonMain 纯逻辑 + Room 实体/DAO）
│   └── src/commonMain/kotlin/com/wenyan/app/
│       ├── llm/              # LLM 客户端与解析
│       ├── domain/           # 会话编排/记忆/历史压缩纯逻辑
│       ├── knowledge/        # 知识引擎与路由
│       ├── prompt/           # System prompt 模板与三层拼装
│       └── data/             # Room 实体/DAO、图片压缩契约、加密基类
├── app/                      # Android 应用（Compose UI + 平台接缝）
│   └── src/main/java/com/wenyan/app/
│       ├── ui/               # Chat / Settings / Onboarding / navigation / components(glass)
│       ├── container/        # RealAppContainer + Real*Repository（链路核心）
│       ├── data/             # datastore / db / image / metrics / security / update
│       └── assets/knowledge/ # 41 份知识文档 + 路由资产（构建门禁生成/校验）
├── desktop/                  # Windows 桌面（JVM 模块）
│   └── src/main/kotlin/com/wenyan/desktop/   # Main/WenyanService/ChatEngine/ApiRoutes/StaticPage
│   └── src/main/resources/static/            # index.html / app.js / styles.css / aqua-fluid.js
│   └── packaging/            # package.ps1：jdeps→jlink→jpackage→exe + 绿色版 zip
└── benchmark/                # BaselineProfile
```

`:shared` 各包代表类与职责：

| 包 | 代表类 | 职责 |
|---|---|---|
| llm | `LlmClient` / `SseParser` | OkHttp SSE（callbackFlow 封装），输出 `LlmEvent`（Delta/Thinking/Done/Failed/Restart）；单帧解析含 reasoningDelta 修复 |
| llm | `AnalysisParser` / `CoachAnalysis` | **四段式 v2 解析**（接住你/事实/建议+三风格/行动，含 sessionTitle/newFacts 顺带产出）；v1 五步法仅作老数据回落 |
| llm | `ChatRequestBuilder` / `ErrorMapper` / `RetryPolicy` / `UsageMetrics` | 请求构造、错误码映射（如 NO_NETWORK）、指数退避、用量统计 |
| domain | `ChatOrchestrator` / `ConversationStateTracker` / `HistoryCompactor` | 记忆节流与标题素材、话题状态机、v1.9.1 选择式历史压缩 |
| domain | `MemoryExtractor` / `MemoryConflictDetector` / `TimelineParser` | 自动记忆提炼、冲突检测、事件时间线解析 |
| knowledge | `KnowledgeEngine` | 路由调度 + 注入拼装 + 64 条 LRU 文档缓存 |
| knowledge | `LlmRouteClassifier` / `RoutingCatalog` / `HybridVariantRouter` / `KnowledgeIndex` / `Bm25Scorer` | 三级路由链（见 §5） |
| knowledge | `KnowledgeChunker` / `CrisisDetector` / `RouteEvaluator` / `RouteDiagnostics` | 分块截断、**本地危机预检**、评测与路由诊断 |
| prompt | `CorePrompt` / `PromptBuilder` | System 核心模板原文；三层拼装【system-核心】/【system-档案】/【system-知识】 |
| data | db（7 Entity + 7 DAO + Converters）/ `ImageSpec` / `AesGcmCipher` | Room KMP 数据契约；图片压缩纯计算（1568px/85%/20MB）；AES-GCM 平台无关基类 |

## 3. 分层与平台接缝

- 依赖只向下：**UI → ViewModel → Repository（接口）→ 数据源**；入口只装配零业务逻辑；单文件 ≤ 300 行
- **expect/actual 仅一处**：`json/Json.kt`（androidMain 用 android.jar 内置 org.json，jvmMain 用外部 org.json）
- 平台差异的主力手法是「**同名同包编译期替换**」——两端各提供一个同名类，共享层只见类型不见实现：

| 同名类 | Android 实现 | 桌面实现 |
|---|---|---|
| `KeystoreAesGcmCipher` | AndroidKeyStore 硬件密钥 | 机器指纹（Windows MachineGuid + 用户名 → PBKDF2）派生密钥，不落盘 |
| `AppDatabase` | Room context builder + Room 驱动 | BundledSQLiteDriver；迁移 SQL 与 Android 版逐字一致（Android 版为唯一事实源） |
| `ImageCompressor` | Bitmap 缩放 | ImageIO |
| `KnowledgeAssetReader`（接口） | assets 读取 | classpath `/knowledge/` 读取（构建时由 processResources 拷入 jar） |

- 另有**接口注入**：`KnowledgeAssetReader` 接口、`AppLogger.sink`（Android→Log+崩溃落盘，桌面→stdout）、指标存储（`MetricsFileStore` / `DesktopMetricsStore`）
- **`:app` 分层**：`ui/contract/`（`AppContainer`、`ChatRepository`、`SettingsRepository` 等接口，含 `StreamingState` 单流状态中枢与 `StreamEvent` sealed）→ `container/RealAppContainer`（装配 DB/DataStore/Cipher/KnowledgeEngine/共享 OkHttpClient）+ `RealChatRepository`（聊天链路核心）→ `data/` → 每屏一个 ViewModel
- **`:desktop` 分层**：无 Repository 层，全包仅 5 类——`Main`（入口）、`WenyanService`（直封装共享 DAO + 加解密 + 设置存储）、`ChatEngine`（移植 RealChatRepository 链路）、`ApiRoutes`（REST + SSE）、`StaticPage`

## 4. 聊天请求数据流

### 4.1 Android 链路

```
ChatScreen → ChatViewModel.sendText/retry
  → RealChatRepository.sendTextFlow
      ① 离线预检（无网短路，输入保留）
      ② routeClassifier() 装配（knowledgeRouting 设置，默认 llm）
      ③ KnowledgeEngine.buildInjection(text, classifier)   ← §5 知识路由
      ④ PromptBuilder 三层拼装（输入形态 routeByInputShape 四分）
      ⑤ resolveClient()：DataStore current_model_id → Room provider → Keystore 解密 → LlmClient
      ⑥ LlmClient.stream（OkHttp SSE：连接 15s / 读 60s、指数退避、公网必须 https 终检）
      ⑦ SseParser 逐帧 → 落库 → AnalysisParser.parseAny 四段式 v2 解析
  → StreamEvent（Delta/Thinking/Analysis/Transcription/Error/Done/Restart）→ CoachCard 渲染
```

### 4.2 截图双通道（`analyzeImages`，双端一致）

- 主模型 `supportsVision=true` → **通道 A**：base64 `image_url` 直读，四段式分析
- 否则 → **通道 B 视觉转述**：视觉模型（用户在模型库自选）流式输出「聊天记录文字转述」（`StreamEvent.Transcription`，`CrisisDetector` 预检）→ 用户确认卡（可编辑修正）→ `transcriptionMode=true` 重跑主模型纯文本分析
- 选图后统一压缩：最长边 ≤1568px、质量 85%（`ImageSpec` 纯计算契约，两端实现各自落码）

### 4.3 桌面链路

- 前端 `fetch` + ReadableStream 消费 SSE（POST 语义，EventSource 不支持）→ `POST /api/chat/stream`
- `ChatEngine.sendMessage` 复用共享链路，`launchSseBridge` 以 ByteChannel 手写 `data: {json}\n\n` 帧，帧类型 `chat/thinking/card/transcription/done/error`
- 客户端断开级联取消内部 EventSource；同会话互斥 `withSessionLock`（并发第二次回 `RETRY_RUNNING`）

## 5. 知识路由（核心）

41 份关系科学与实用沟通文档（20 knowledge + 21 practical）打包进资产，按需注入 system prompt。`KnowledgeEngine.buildInjection` 三级降级：

1. **LLM 分类器**（默认，`route_source=llm`）：`LlmRouteClassifier` 非流式 chat（复用主模型三元组），system = 路由指令 + catalog 行（file|title|summary），`max_tokens=200`，**总超时 10s**（协程 withTimeout + OkHttp callTimeout 双保险），容错解析首个 JSON → 输出 **0–5 个文件名**；任一不在 `routing-catalog.json` 白名单即整体判失败；空数组 = 合法弃权
2. **离线混合兜底**（分类器失败 → `fallback`；未注入分类器 → `offline`）：`HybridVariantRouter` 用 `KnowledgeIndex`（routes-v2.json 关键词/BM25）取 topK=3，命中不足 2 篇时用问法变体库（`route_query_variants.json`，820 条）BM25 补漏；无变体库再退 `KnowledgeIndex` 单独
3. `routed.take(maxDocs=5)` → 每份 `KnowledgeChunker.truncate`（二级标题分块，≤6000 字符 ≈ 4K token，关键词命中 chunk 优先保留）→ 包装【知识文档 #N】注入，refDocs 回写 session 供界面回显

**设置开关** `knowledgeRouting`（默认 `llm`；显式 `offline` 时零解密、零路由请求）；`route_source` 进入路由诊断（Android 用量诊断弹窗可见）。

**资产三件**（`app/src/main/assets/knowledge/`）：`routes-v2.json`（关键词路由表）、`routing-catalog.json`（41 条 file/title/summary = LLM 路由目录 + 白名单）、`route_query_variants.json`（变体库）。

**双重门禁**：

- 构建门禁 `knowledgeCheck`（preBuild → `scripts/gen_routes.py`）：校验恰好 41 份 md、生成 routes-v2.json、校验 catalog（41 条、文件名一致、summary ≤30 字），违规构建失败
- 评测门禁 `LeakGateTest`：评测集 query 与变体库任意条目最长公共子串 ≥8 字符即失败（防评测集泄漏退化为背题）；v1.9.4 起评测集经去泄漏改写，路由策略转正须过三臂盲评决策门

## 6. 数据层

**Room KMP v8，7 表**（Entity/DAO 在 `:shared`，schema 导出至 `app/schemas/` 与 `desktop/schemas/`，累计 8 段迁移）：

| 表 | 关键字段 |
|---|---|
| `profile` | mbti / score / strengths / weaknesses（本人档案） |
| `target` | codeName / mbti / score / relationStatus / timeline(JSON) / note（咨询对象档案） |
| `session` | title / scenarioTag / refDocs(JSON) / stateJson / targetId |
| `message` | sessionId / role(USER\|ASSISTANT) / type(text\|image\|transcription) / content |
| `provider` | name / baseUrl / **apiKeyEncrypted** / isPreset / connectionStatus / sortOrder |
| `model` | providerId / name / supportsVision / isDefault / showInSheet / sortOrder |
| `memory_fact` | targetId / text / kind(fact\|hypothesis) / expiresAt / source(manual\|transcription…) |

**轻设置**：Android 走 DataStore `settings`（模型选择、主题、流光/玻璃外观、onboarding、active_target、记忆开关、current_session_id、knowledge_routing 等）；桌面走 `%APPDATA%\Wenyan\wenyan-settings.properties`（visionModelId / memoryAutoEnabled / knowledgeRouting / memoryWriteLog）。

**加密**：同一 `AesGcmCipher` 基类，密钥永不出平台安全区——Android 密钥在 AndroidKeyStore（硬件-backed）；桌面由 Windows MachineGuid + 用户名经 PBKDF2 派生 AES-256，**密钥不落盘**。两端密文**不互通**、各自独立建档；记忆导出备份带 `app` 头标识（wenyan-android / wenyan-desktop），可互读合并。

## 7. 桌面端形态

- **`Main.kt`**：Ktor **CIO**，仅绑 `127.0.0.1`；端口自 **18923** 起探测（探测→尝试 bind→失败重探循环，防 TOCTOU，各限 50 次）；启动成功 `Desktop.browse` 自动开浏览器；每次启动生成随机 CSRF token；`DESKTOP_VERSION` 常量
- **安全**：全局拦截器 Host 白名单（127.0.0.1 / localhost / ::1 全等比对，防 DNS 重绑定）+ 写请求校验 `X-Wenyan-Token`
- **`ApiRoutes.kt`** 端点分组：
  - 引导/状态：`GET /api/bootstrap`、`/health`、`/metrics`、`/search`
  - 模型：providers/models CRUD、`POST /api/providers/{id}/test`
  - 记忆：targets/facts CRUD、`GET /api/facts/{id}/permanent`、`/api/memory/export|import`、`/api/memory/undo-last-write`
  - 会话：sessions CRUD、`GET /api/sessions/{id}/messages`、`POST /api/sessions/{id}/target`
  - **SSE**：`POST /api/chat/stream`、`/api/chat/retry`、`/api/chat/confirm-transcription`
  - 图片：`POST /api/images/upload`（50MB 上限）
  - 数据/杂项：`GET/PUT /api/settings`、`/api/export`、`/api/import`、`/api/data/clear`、`/api/update`、`/api/onboarding`
- **static 前端**（原生 JS 无框架）：`index.html`（SVG 液态折射滤镜/玻璃增强层）、`app.js`（hash 路由 + fetch SSE；分区：主题玻璃 / 数据加载 / 会话 / 聊天渲染 / v1.9.6 逐段渐显（对齐 RevealController）/ 通道 B 转述确认 / 设置 / 模型管理 / 记忆档案 / 引导页）、`styles.css`、`aqua-fluid.js`（WebGL 流体 shader，与 Android `FluidBackground` 同源参数）
- **打包**（`packaging/package.ps1`）：`:desktop:installDist` → `jdeps --print-module-deps`（补 java.desktop / jdk.crypto.ec / cryptoki / mscapi）→ `jlink` 裁剪 JRE → `jpackage --type app-image`（主类 `com.wenyan.desktop.MainKt`）→ WiX 3.x 打 exe 安装包；产物：exe 安装包 + 绿色版目录 + zip（均内嵌裁剪 JRE）

## 8. Android 平台接缝

- 入口：`WenyanApp`（Application：RealAppContainer + AppLogger sink）、`MainActivity`（edge-to-edge + 主题装配）、`AppViewModel`（主题/流光/玻璃全局态）；导航 `AppRoot` 五 Route：Onboarding / Chat / Settings / ProviderEdit / MemoryEdit
- 屏幕：`ChatScreen`（+ InputBar / SessionDrawer / MessageBubble / InputShapeRouter 等）、`SettingsScreen`（模型服务 / 记忆 / 外观 / 隐私与安全四分组）、`OnboardingScreen`（四步问卷）
- 玻璃/流光组件族（`ui/components/glass/`）：`LiquidGlass`（双填充材质）、`FluidBackground`（AGSL 流体 shader，30fps 节流；Android 13 以下自动降级简化光斑）、`GlassBlurCapabilityProbe`（能力探测，v1.9.4 起降级为纯观测——结论进状态行/logcat，不再影响渲染）、`HueWindow`（`GtjWindowTheme` 包裹 17 处独立窗口弹层，色相≠0 时与主界面同色相）、`FluidAppearance` / `GlowBackground`
- `ui/chat/RevealController`：v1.9.6 回答逐段渐显的计划器（每段 ~320ms 淡入上浮、错峰 140ms、总时长 0.9–2.6s 钳制；历史/交互/减动效直出），纯逻辑可单测；桌面 `app.js` 有同款实现
- **版本号三处同步**：`app/build.gradle.kts`（versionCode/versionName）、`desktop/.../Main.kt`（DESKTOP_VERSION）、`desktop/packaging/package.ps1`（$VERSION）；`app.js` 的 APP_VERSION 仅作兜底，启动时以 `/api/health` 覆盖

## 9. 测试与门禁

- **单元测试**：63 个测试类 / 624 个测试方法，全部住 `app/src/test/`（`:shared` 无测试源集，依赖 :shared 可直测）。分布重心：knowledge 19 类（含 `LeakGateTest`、`RouteEvaluator`、路由评测）、llm 8、domain 7、glass 6、settings 6、prompt 2、container 4
- **androidTest**：6 类 12 方法（`MigrationTest` 走 room-testing 逐段迁移、SessionDrawer、GlassSurfaceClick、记忆流程）
- **:desktop**：`MemoryMergeImportTest`、`SharedSourceSmokeTest`
- **构建/评测门禁**：`knowledgeCheck`（preBuild，知识库完整性 + 路由资产一致性）、`LeakGateTest`（评测集去泄漏，见 §5）

## 10. 关键约束

1. **纯本地 BYOK**：无自有后端，数据不出本机；桌面服务仅绑 127.0.0.1 并以 CSRF token + Host 全等白名单兜底
2. **密钥不落盘**：Android 走 AndroidKeyStore，桌面走机器指纹派生；两端密文不互通
3. **超时/重试**：LLM 连接 15s / 读 60s、指数退避重试；LLM 路由独立 10s 超时，最坏静默降级离线路由
4. **危机优先**：`CrisisDetector` 本地预检先于一切话术链路，命中即安全转介
5. **预设厂商/模型名单以 `PresetSeed.kt` 为准**（本文不快照——v1.0 旧稿内嵌名单的教训是模型迭代即过时）
6. **行为一致性**：平台差异只允许出现在 §3 接缝表与 UI 层；业务逻辑改动落 `:shared`，两端同时生效
