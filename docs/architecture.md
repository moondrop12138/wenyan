# 架构文档 - 狗头军师（安卓）v1.0

> **L14 版本注**：本文档预设模型名单为 2026-08-02 快照（5 厂商），当前实际为 7 厂商（模型名单随厂商迭代，以代码 `PresetSeed` 为准）。

> 生成日期：2026-08-02
> 作者：高见远（架构师）｜汇总：大湾区靓仔（项目总监）
> 状态：待用户确认

---

## 1. 技术选型表（已联网核验依赖存在性）

| 层 | 选型 | 版本 | 理由 |
|----|------|------|------|
| UI | Jetpack Compose + Material 3 | BOM 2025.xx | 用户锁定 Kotlin 原生，DeepSeek 级体验 |
| LLM Client | OkHttp + okhttp-sse **自建** | okhttp 4.12.0 / okhttp-sse 4.12.0 | BYOK 多供应商兼容（DeepSeek/OpenAI/GLM/硅基流动）、SSE 流式原生（callbackFlow 封装）、超时/指数退避可配；官方 openai-java SDK 对国产模型扩展字段支持差且体积大 |
| 知识库引擎 | **规则路由**（编译期路由表 JSON） | - | 最贴合 SKILL.md「按需加载」原设计；token 可控（每次 1-3 份文档）；零 embedding 成本、离线可用。全量 396KB ≈ 20 万 token 不可行；本地向量 RAG 对 40 份静态文档属过度设计 |
| 本地存储 | Room + DataStore | room 2.6+ / datastore 1.1 | 档案/会话/消息多表关联用 Room（Flow 响应式）；API Key/BaseURL/模型/主题设置用 DataStore |
| 图片选择 | Photo Picker | activity-compose 1.9 | PickVisualMedia 免权限，低版本自动 fallback |
| 图标 | **Material Symbols**（material-icons-extended） | 随 BOM | 锁定一套；与 M3 视觉一致、R8 裁剪、无第三方依赖风险 |
| 最低版本 | minSdk 26 | - | 覆盖 Android 8.0+ |

## 2. 架构分层

```
UI (Compose) → ViewModel → Repository
                              ├─ LLM Client (OkHttp + SSE, callbackFlow 封装)
                              ├─ KnowledgeEngine (编译期路由表 → assets 文档 → 分块截断)
                              ├─ PromptBuilder (分层 prompt 拼装)
                              └─ Room (档案/会话/消息) + DataStore (Key/设置) + Assets (知识库)
```

- 依赖只向下：UI → ViewModel → Repository → 数据源
- 单文件 ≤ 300 行，入口只装配零业务逻辑
- 全部网络请求走 LLM Client 单例，统一超时（连接 15s / 读 60s）、指数退避重试（3 次）、停止按钮

## 3. 知识库集成方案（规则路由，核心）

- 40 份 markdown 文档（20 knowledge + 20 practical）打包进 APK `assets/knowledge/`
- **编译期脚本**生成路由表 JSON：SKILL.md「按需加载」表 + 文档标题关键词 → 场景映射
- 运行时：根据用户输入/当前场景命中 1-3 份文档 → 按 `##` 分块 + 关键词命中章节截断 → 单文档预算 4K token 拼入 system prompt
- 中文 13KB 文档 ≈ 6-7K token，必须截断；优先级：标题命中 > 章节命中 > 摘要
- 知识加载对用户透明：分析结果回显"本次参考：xxx.md"

## 4. 模型选择器与截图分析双通道（用户拍板方案）

### 4.1 多提供商模型管理（Chatbox 式机制，用户拍板）

**数据模型**（Room 表，Phase 2 细化）：

- `provider`（提供商）：id / name / baseUrl / apiKeyEncrypted（Keystore AES-GCM）/ isPreset / sortOrder
- `model`（模型）：id / providerId(FK) / name / supportsVision / isDefault / sortOrder
- `settings`（DataStore）：currentModelId（主模型）/ visionModelId（视觉模型，用户自选）

**内置预设提供商**（可编辑 Key/地址；模型列表预置、可增删。模型名单已联网核实，截至 **2026-08-02**；App 内置后仍可手动增删改，随厂商迭代自行更新）：

| 提供商 | 默认 API Host | 预设模型（能力标注） |
|--------|----------------|----------------------|
| DeepSeek | https://api.deepseek.com | deepseek-v4-pro（仅文本，1M 上下文）/ deepseek-v4-flash（仅文本，1M） |
| 智谱 | https://open.bigmodel.cn/api/paas/v4 | glm-5.2（仅文本，1M）/ glm-5v-turbo（多模态，200K）/ glm-4v-flash（多模态，免费 16K） |
| OpenAI | https://api.openai.com/v1 | gpt-5.6-sol（多模态，旗舰）/ gpt-5.6-terra（多模态，均衡）/ gpt-5.6-luna（多模态，高性价比，降价后 $0.2/M 输入） |
| 通义 | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen3.7-max（仅文本，1M）/ qwen3.7-plus（多模态，1M）/ qwen3-vl-flash（多模态） |
| Kimi | https://api.moonshot.cn/v1 | kimi-k3（多模态，1M，原生视觉）/ kimi-k2.6（多模态，256K） |

> **模型时效说明（2026-08-02 核实）**：DeepSeek 的 deepseek-chat/deepseek-reasoner 已于 2026-07-24 退役（现指向 V4 系）；Kimi 的 kimi-k2 系列已下线、moonshot-v1 系列 2026-08-31 平台下线；通义 qwen-vl-max 已 Legacy（用 qwen3-vl 系）；OpenAI 最新为 2026-07 发布的 GPT-5.6 系列（Sol/Terra/Luna，7-30 官宣 Luna 降 80%、Terra 降 20%）。预设表不含任何已退役模型名。

**自定义提供商**：用户全自定义（名称 + Host + Key + 模型列表），兼容 OpenAI 格式接口，支持 Ollama 等本地服务。

**运行机制**：
- 主模型 = currentModelId 对应模型（对话/五步分析）
- 视觉模型 = visionModelId 对应模型（截图转述通道 B 用，**用户从模型库自行选择**，替代 read-image 插件的 VISION_MODEL 硬编码）
- 对话页顶部模型切换器：切换 currentModelId，不中断对话
- 请求构造：LLM Client 按 provider.baseUrl + model.name 组 OpenAI 兼容请求；多模态模型传 image_url，非多模态拒绝图片输入并提示走通道 B

### 4.2 截图分析双通道（read-image 机制移植，已在 WorkBuddy 端验证）

```
用户选择截图
    ↓
通道判断：当前主模型是否多模态？
    ├─ 是 → 通道A：截图(base64, image_url 格式) 直接发主模型 → 五步法分析
    └─ 否 → 通道B：视觉转述（read-image 三步机制）
            ① 截图 → base64
            ② 发视觉模型（默认预设 GLM-4V-Flash，OpenAI 兼容格式）
            ③ 视觉模型返回"聊天记录文字转述"（可见原文/说话人/顺序/间隔）
            ④ 转述文本交给主模型 → 五步法分析
    ↓
标注三态：截图能证明 / 转述提示 / 仍未知（不脑补）
```

- **视觉模型槽位**：设置页可独立配置（默认 GLM-4V-Flash 预设，低成本）；未配置时截图入口引导
- 图片压缩：选图后缩放最长边 ≤1568px、质量 85%（GPT-4o 视觉推荐规格），控制 token 消耗
- 通道 B 转述结果对用户可见（"AI 从截图中读出了这些内容"），可编辑修正后再分析——符合 SKILL.md"只把可见原文当事实"

### 4.3 Prompt 工程架构（上下文预算分配）

| 层 | 内容 | 预算 |
|----|------|------|
| system-核心 | SKILL.md 精简版（核心原则 + 五步法 + 安全边界） | ~1.5K token |
| system-档案 | 用户/对象档案（问卷结构化 JSON） | ~0.5K token |
| system-知识 | 按需加载 1-3 份文档（截断后） | 4-12K token |
| 用户消息 | 粘贴文本 / 截图转述 / 对话历史 | 视输入 |

## 5. 关键技术约束与对策（可行性坑）

| # | 坑 | 对策 |
|---|----|------|
| 1 | **DeepSeek 官方 API 无视觉能力**（deepseek-chat 纯文本） | **双通道方案（已拍板）**：多模态模型直读；非多模态走视觉转述通道（read-image 机制移植，视觉模型默认 GLM-4V-Flash 预设）；设置页按模型能力标注分流 |
| 2 | 单文档 13KB ≈ 6-7K token 超预算 | 按 `##` 分块 + 关键词命中章节，单文档预算 4K token，每次拼 1-3 份 |
| 3 | SSE 回调在后台线程 | callbackFlow + Dispatchers 调度，主线程更新 UI |
| 4 | API Key 本地安全 | Keystore AES-GCM 加密存储，仅设置页可见，支持一键清除 |
| 5 | 无自有后端 → 无 CORS 问题但需 INTERNET 权限 | manifest 声明 INTERNET；隐私声明明示"数据发送至用户配置的第三方服务" |
| 6 | 流式中断/超时/模型不可用 | 指数退避重试 3 次 + 停止按钮 + 明确错误提示（401=Key 无效，429=限流，502=服务异常） |
| 7 | 截图中文识别准确率风险 | 通道 A 视觉模型直读；通道 B 视觉转述（视觉模型即"超级 OCR"，优于传统 OCR 引擎，read-image 已验证） |
| 8 | 合规：法律/危机内容 | 按 SKILL.md 安全边界转介（当地紧急服务），不输出恋爱话术 |

## 6. 数据库 Schema 草案（Phase 2 细化）

- `profile`（用户档案：MBTI/评分/优势短板）
- `target`（对象档案：代号/MBTI/评分/关系状态/事件时间线）
- `session`（会话：创建时间/场景标签/引用文档）
- `message`（消息：角色/内容/类型[text|image]/创建时间）
- `provider`（提供商：名称/Base URL/加密 Key/是否预设/排序）
- `model`（模型：提供商 FK/模型名/多模态能力/是否默认/排序）
- DataStore：currentModelId + visionModelId + 主题设置

## 7. 风险清单

- 多模态模型质量与成本（用户侧）：截图分析 token 消耗大于文本 → 结果页提示本次消耗估算
- 知识库文档更新：路由表编译期生成，文档增改需重跑脚本（脚本纳入项目，自动化）
- 隐私信任：纯本地为最大壁垒，任何上云功能一律否决
